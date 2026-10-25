package com.txao.music

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Tag changes. A null field is left as it is; an empty string, or 0 for a number, removes it. */
class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val trackNo: Int? = null,
    val cover: ByteArray? = null,
)

/**
 * Minimal ID3v2 writer for MP3 files. It writes a fresh v2.4 tag in front of the untouched audio:
 * the fields in [TagEdit] are replaced, every other frame of an existing v2.3/v2.4 tag is carried over as it was.
 * Tags that are unsynchronised, or use frame compression or encryption, are not carried over (only the audio is kept).
 */
object Id3 {
    class Frame(val id: String, val data: ByteArray)

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7f) shl 21) or ((b[o + 1].toInt() and 0x7f) shl 14) or
            ((b[o + 2].toInt() and 0x7f) shl 7) or (b[o + 3].toInt() and 0x7f)

    private fun bigEndian(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)

    private fun putSyncsafe(n: Int) =
        byteArrayOf((n shr 21 and 0x7f).toByte(), (n shr 14 and 0x7f).toByte(), (n shr 7 and 0x7f).toByte(), (n and 0x7f).toByte())

    private fun readUpTo(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(buf, got, n - got)
            if (r < 0) break
            got += r
        }
        return buf.copyOf(got)
    }

    private fun isTag(h: ByteArray) = h.size == 10 && h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() && h[2] == '3'.code.toByte()

    /** Frames of the tag at the start of [data] and the offset where the audio begins. */
    fun inspect(data: ByteArray): Pair<List<Frame>, Int> {
        val head = data.copyOf(minOf(10, data.size))
        if (!isTag(head)) return emptyList<Frame>() to 0
        val size = syncsafe(data, 6)
        val body = data.copyOfRange(10, minOf(data.size, 10 + size))
        return parseFrames(body, data[3].toInt(), data[5].toInt() and 0xff) to 10 + size
    }

    private fun parseFrames(body: ByteArray, version: Int, flags: Int): List<Frame> {
        if (version !in 3..4 || flags and 0x80 != 0) return emptyList()
        var off = 0
        if (flags and 0x40 != 0 && body.size >= 4) off = if (version == 4) syncsafe(body, 0) else 4 + bigEndian(body, 0)
        val out = ArrayList<Frame>()
        while (off + 10 <= body.size && body[off].toInt() != 0) {
            val id = String(body, off, 4, Charsets.ISO_8859_1)
            val size = if (version == 4) syncsafe(body, off + 4) else bigEndian(body, off + 4)
            if (size < 0 || off + 10 + size > body.size) break
            val formatFlags = body[off + 8].toInt() or body[off + 9].toInt()
            if (formatFlags == 0) out += Frame(id, body.copyOfRange(off + 10, off + 10 + size))
            off += 10 + size
        }
        return out
    }

    private fun frame(id: String, data: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(id.toByteArray(Charsets.ISO_8859_1))
        o.write(putSyncsafe(data.size))
        o.write(byteArrayOf(0, 0))
        o.write(data)
        return o.toByteArray()
    }

    private fun text(id: String, value: String) = frame(id, byteArrayOf(3) + value.toByteArray(Charsets.UTF_8))

    private fun picture(bytes: ByteArray): ByteArray {
        val png = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
        val mime = (if (png) "image/png" else "image/jpeg").toByteArray(Charsets.ISO_8859_1)
        // encoding 0, mime, end, picture type 3 (front cover), empty description, data
        return frame("APIC", byteArrayOf(0) + mime + byteArrayOf(0, 3, 0) + bytes)
    }

    /** Writes the file from [input] to [out] with [edit] applied to its tag. */
    fun rewrite(input: InputStream, edit: TagEdit, out: OutputStream) {
        val inp = BufferedInputStream(input)
        val header = readUpTo(inp, 10)
        var kept = emptyList<Frame>()
        var leftover = ByteArray(0)
        if (isTag(header)) {
            val version = header[3].toInt()
            val flags = header[5].toInt() and 0xff
            val size = syncsafe(header, 6)
            val body = readUpTo(inp, size)
            if (version == 4 && flags and 0x10 != 0) readUpTo(inp, 10) // footer
            if (body.size == size) kept = parseFrames(body, version, flags)
        } else {
            leftover = header
        }

        val dropped = HashSet<String>()
        val added = ByteArrayOutputStream()
        fun replace(ids: List<String>, new: ByteArray?) {
            dropped += ids
            if (new != null) added.write(new)
        }
        edit.title?.let { replace(listOf("TIT2"), if (it.isBlank()) null else text("TIT2", it.trim())) }
        edit.artist?.let { replace(listOf("TPE1"), if (it.isBlank()) null else text("TPE1", it.trim())) }
        edit.album?.let { replace(listOf("TALB"), if (it.isBlank()) null else text("TALB", it.trim())) }
        edit.albumArtist?.let { replace(listOf("TPE2"), if (it.isBlank()) null else text("TPE2", it.trim())) }
        edit.genre?.let { replace(listOf("TCON"), if (it.isBlank()) null else text("TCON", it.trim())) }
        edit.trackNo?.let { replace(listOf("TRCK"), if (it <= 0) null else text("TRCK", it.toString())) }
        edit.year?.let { replace(listOf("TYER", "TDRC", "TDAT", "TIME", "TRDA"), if (it <= 0) null else text("TDRC", it.toString())) }
        edit.cover?.let { replace(listOf("APIC"), picture(it)) }

        val body = ByteArrayOutputStream()
        for (f in kept) if (f.id !in dropped) body.write(frame(f.id, f.data))
        body.write(added.toByteArray())
        if (body.size() > 0) {
            out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0))
            out.write(putSyncsafe(body.size()))
            out.write(body.toByteArray())
        }
        out.write(leftover)
        inp.copyTo(out)
    }

    /** First text value of [id], or null. */
    fun text(frames: List<Frame>, id: String): String? =
        frames.firstOrNull { it.id == id }?.data?.takeIf { it.size > 1 }?.let { d ->
            val cs = when (d[0].toInt()) { 1, 2 -> Charsets.UTF_16; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
            String(d, 1, d.size - 1, cs).trimEnd('\u0000')
        }
}
