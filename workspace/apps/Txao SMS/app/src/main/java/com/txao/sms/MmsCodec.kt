package com.txao.sms

import java.io.ByteArrayOutputStream

/** One body part of a multimedia message (text, picture, video, audio, SMIL layout). */
class MmsPart(val contentType: String, val name: String?, val contentId: String?, val data: ByteArray) {
    val isText get() = contentType.startsWith("text/plain")
    val isSmil get() = contentType == "application/smil"
    val isMedia get() = contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")
    val text get() = String(data, Charsets.UTF_8)
}

/** The "you have a picture message" push that tells us where to download it. */
data class MmsNotification(val transactionId: String, val location: String, val from: String?, val size: Long, val subject: String?)

data class MmsMessage(val from: String?, val to: List<String>, val subject: String?, val date: Long, val parts: List<MmsPart>)

/**
 * Reads and writes the binary MMS format (OMA MMS 1.2 headers on top of WSP multipart bodies).
 * Pure Kotlin so it can be unit tested on the JVM. Written from the public specification only.
 */
object MmsCodec {
    const val TYPE_SEND_REQ = 0x80
    const val TYPE_NOTIFICATION_IND = 0x82
    const val TYPE_RETRIEVE_CONF = 0x84

    private val knownTypes = mapOf(
        0x00 to "*/*", 0x01 to "text/*", 0x02 to "text/html", 0x03 to "text/plain", 0x06 to "text/x-vCalendar", 0x07 to "text/x-vCard",
        0x0C to "multipart/mixed", 0x1C to "image/*", 0x1D to "image/gif", 0x1E to "image/jpeg", 0x1F to "image/tiff", 0x20 to "image/png",
        0x21 to "image/vnd.wap.wbmp", 0x23 to "application/vnd.wap.multipart.mixed", 0x26 to "application/vnd.wap.multipart.alternative",
        0x33 to "application/vnd.wap.multipart.related", 0x36 to "application/vnd.wap.multipart.alternative",
    )

    private class Reader(val b: ByteArray, var pos: Int = 0) {
        fun more() = pos < b.size
        fun peek() = b[pos].toInt() and 0xFF
        fun u8() = (b[pos++].toInt() and 0xFF)

        fun uintvar(): Int {
            var v = 0
            while (true) {
                val x = u8()
                v = (v shl 7) or (x and 0x7F)
                if (x and 0x80 == 0) return v
            }
        }

        fun text(): String {
            if (peek() == 0x7F) pos++ // quote char in front of strings that start with a high byte
            val start = pos
            while (b[pos].toInt() != 0) pos++
            val s = String(b, start, pos - start, Charsets.UTF_8)
            pos++
            return s
        }

        fun valueLength(): Int {
            val x = u8()
            return if (x <= 30) x else uintvar()
        }

        fun longInt(): Long {
            val n = u8()
            var v = 0L
            repeat(n) { v = (v shl 8) or u8().toLong() }
            return v
        }

        /** Skips any WSP value: short integer, length-prefixed blob, or text string. */
        fun skipValue() {
            val x = peek()
            when {
                x >= 0x80 -> pos++
                x <= 30 -> { pos++; pos += x }
                x == 31 -> { pos++; val n = uintvar(); pos += n }
                else -> text()
            }
        }

        /** Encoded-string: plain text, or a length-prefixed charset + text. */
        fun encodedString(): String {
            val x = peek()
            if (x > 31) return text()
            val len = valueLength()
            val end = pos + len
            if (peek() >= 0x80) pos++ else if (peek() <= 30) { val n = valueLength(); pos += n } else text() // charset
            val s = if (pos < end) text() else ""
            pos = end
            return s
        }
    }

    private class ContentType(val media: String, val charset: String?, val name: String?)

    private fun charsetFor(mib: Int?): String = when (mib) {
        3 -> "US-ASCII"; 4 -> "ISO-8859-1"; 1013 -> "UTF-16BE"; 1014 -> "UTF-16LE"; 1015 -> "UTF-16"; else -> "UTF-8"
    }

    private fun readContentType(r: Reader): ContentType {
        val x = r.peek()
        if (x >= 0x80) { r.pos++; return ContentType(knownTypes[x and 0x7F] ?: "application/octet-stream", null, null) }
        if (x > 31) return ContentType(r.text(), null, null)
        val len = r.valueLength()
        val end = r.pos + len
        val media = when {
            r.peek() >= 0x80 -> knownTypes[r.u8() and 0x7F] ?: "application/octet-stream"
            else -> r.text()
        }
        var charset: String? = null
        var name: String? = null
        while (r.pos < end) {
            val p = r.u8()
            when (p) {
                0x81 -> { // charset
                    val c = r.peek()
                    charset = when {
                        c >= 0x80 -> { r.pos++; charsetFor(c and 0x7F) }
                        c <= 30 -> { val n = r.valueLength(); val e = r.pos + n; var v = 0; while (r.pos < e) v = (v shl 8) or r.u8(); charsetFor(v) }
                        else -> { r.text(); "UTF-8" }
                    }
                }
                0x85, 0x86 -> name = r.text() // name, filename
                else -> if (p >= 0x80) r.skipValue() else { r.pos--; r.text(); r.skipValue() }
            }
        }
        r.pos = end
        return ContentType(media, charset, name)
    }

    private fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=").trim()

    private class Headers {
        var type = 0
        var transactionId = ""
        var location = ""
        var from: String? = null
        val to = ArrayList<String>()
        var subject: String? = null
        var date = 0L
        var size = 0L
        var bodyStart = -1
    }

    private fun readHeaders(r: Reader): Headers {
        val h = Headers()
        while (r.more()) {
            val f = r.u8()
            if (f < 0x80) { r.pos--; r.text(); r.skipValue(); continue }
            when (f) {
                0x8C -> h.type = r.u8()
                0x98 -> h.transactionId = r.text()
                0x83 -> h.location = r.text()
                0x89 -> { // From: length, then 0x80 + address or 0x81 (insert address)
                    val len = r.valueLength()
                    val end = r.pos + len
                    if (len > 0 && r.u8() == 0x80) h.from = cleanAddress(r.encodedString())
                    r.pos = end
                }
                0x97, 0x82 -> h.to += cleanAddress(r.encodedString())
                0x96 -> h.subject = r.encodedString().ifBlank { null }
                0x85 -> h.date = r.longInt()
                0x8E -> h.size = r.longInt()
                0x84 -> { h.bodyStart = r.pos; return h } // content type is the last header; the body follows it
                else -> r.skipValue()
            }
        }
        return h
    }

    fun messageType(pdu: ByteArray): Int? = try {
        val r = Reader(pdu)
        var t: Int? = null
        while (r.more() && t == null) {
            val f = r.u8()
            if (f == 0x8C) t = r.u8() else if (f < 0x80) { r.pos--; r.text(); r.skipValue() } else r.skipValue()
        }
        t
    } catch (_: Exception) { null }

    fun parseNotification(pdu: ByteArray): MmsNotification? = try {
        val h = readHeaders(Reader(pdu))
        if (h.type != TYPE_NOTIFICATION_IND || h.location.isEmpty()) null
        else MmsNotification(h.transactionId, h.location, h.from, h.size, h.subject)
    } catch (_: Exception) { null }

    fun parseRetrieve(pdu: ByteArray): MmsMessage? = try {
        val r = Reader(pdu)
        val h = readHeaders(r)
        if (h.type != TYPE_RETRIEVE_CONF || h.bodyStart < 0) null
        else {
            r.pos = h.bodyStart
            val ct = readContentType(r)
            val parts = if (ct.media.contains("multipart")) readParts(r) else listOf(MmsPart(ct.media, ct.name, null, pdu.copyOfRange(r.pos, pdu.size)))
            MmsMessage(h.from, h.to, h.subject, h.date, parts)
        }
    } catch (_: Exception) { null }

    private fun readParts(r: Reader): List<MmsPart> {
        val count = r.uintvar()
        val out = ArrayList<MmsPart>()
        repeat(count) {
            val hLen = r.uintvar()
            val dLen = r.uintvar()
            val hEnd = r.pos + hLen
            val ct = readContentType(r)
            var cid: String? = null
            var name = ct.name
            while (r.pos < hEnd) {
                val f = r.u8()
                when (f) {
                    0xC0 -> { if (r.peek() == 0x22) r.pos++; cid = r.text().removePrefix("<").removeSuffix(">") }
                    0x8E -> name = r.text()
                    else -> if (f >= 0x80) r.skipValue() else { r.pos--; r.text(); r.skipValue() }
                }
            }
            r.pos = hEnd
            var data = r.b.copyOfRange(r.pos, r.pos + dLen)
            r.pos += dLen
            if (ct.media.startsWith("text/") && ct.charset != null && ct.charset != "UTF-8") {
                data = String(data, charset(ct.charset)).toByteArray(Charsets.UTF_8)
            }
            out += MmsPart(ct.media, name, cid, data)
        }
        return out
    }

    // ---------- writing ----------

    private fun ByteArrayOutputStream.text(s: String) { write(s.toByteArray(Charsets.UTF_8)); write(0) }

    private fun ByteArrayOutputStream.uintvar(v: Int) {
        val groups = ArrayList<Int>()
        var n = v
        do { groups += n and 0x7F; n = n shr 7 } while (n > 0)
        for (i in groups.indices.reversed()) write(if (i == 0) groups[i] else groups[i] or 0x80)
    }

    private fun ByteArrayOutputStream.valueLength(len: Int) {
        if (len <= 30) write(len) else { write(31); uintvar(len) }
    }

    private fun ByteArrayOutputStream.longInt(v: Long) {
        var n = 0
        var t = v
        do { n++; t = t shr 8 } while (t > 0)
        write(n)
        for (i in n - 1 downTo 0) write(((v shr (8 * i)) and 0xFF).toInt())
    }

    private fun recipient(a: String) = if (a.contains('@')) a else "$a/TYPE=PLMN"

    /** A picture message ready to hand to the platform's MMS sender. [parts] must not contain the SMIL layout. */
    fun buildSendReq(to: List<String>, subject: String?, parts: List<MmsPart>, transactionId: String, nowSeconds: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x8C); out.write(TYPE_SEND_REQ)
        out.write(0x98); out.text(transactionId)
        out.write(0x8D); out.write(0x92) // MMS 1.2
        out.write(0x85); out.longInt(nowSeconds)
        out.write(0x89); out.write(1); out.write(0x81) // From: the network fills in our number
        to.forEach { out.write(0x97); out.text(recipient(it)) }
        if (!subject.isNullOrBlank()) {
            val s = subject.toByteArray(Charsets.UTF_8)
            out.write(0x96); out.valueLength(s.size + 2); out.write(0xEA); out.write(s); out.write(0)
        }
        out.write(0x8A); out.write(0x80) // class: personal
        out.write(0x90); out.write(0x81) // no delivery report
        out.write(0x91); out.write(0x81) // no read report

        // Content type (last header): multipart/related with the SMIL layout as start part.
        val ct = ByteArrayOutputStream()
        ct.write(0xB3)
        ct.write(0x89); ct.text("application/smil")
        ct.write(0x8A); ct.text("<smil>")
        out.write(0x84); out.valueLength(ct.size()); out.write(ct.toByteArray())

        val all = ArrayList<MmsPart>()
        all += MmsPart("application/smil", "smil.xml", "smil", smil(parts).toByteArray(Charsets.UTF_8))
        all += parts
        out.uintvar(all.size)
        for (p in all) {
            val h = ByteArrayOutputStream()
            if (p.isText) {
                h.valueLength("text/plain".length + 1 + 2); h.text("text/plain"); h.write(0x81); h.write(0xEA)
            } else {
                h.text(p.contentType)
            }
            val loc = p.name ?: p.contentId ?: "part"
            h.write(0x8E); h.text(loc)
            h.write(0xC0); h.write(0x22); h.text("<${p.contentId ?: loc}>")
            out.uintvar(h.size()); out.uintvar(p.data.size)
            out.write(h.toByteArray()); out.write(p.data)
        }
        return out.toByteArray()
    }

    /** One slide per picture/video/audio, with the text on the first slide. */
    fun smil(parts: List<MmsPart>): String {
        val media = parts.filter { it.isMedia }
        val texts = parts.filter { it.isText }
        val sb = StringBuilder("<smil><head><layout><root-layout width=\"320\" height=\"480\"/>")
        sb.append("<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>")
        sb.append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/></layout></head><body>")
        val slides = maxOf(media.size, 1)
        for (i in 0 until slides) {
            sb.append("<par dur=\"5000ms\">")
            media.getOrNull(i)?.let { m ->
                val tag = when {
                    m.contentType.startsWith("image/") -> "img"
                    m.contentType.startsWith("video/") -> "video"
                    else -> "audio"
                }
                sb.append("<$tag src=\"${escape(m.name ?: m.contentId ?: "part")}\"" + (if (tag == "audio") "" else " region=\"Image\"") + "/>")
            }
            if (i == 0) texts.firstOrNull()?.let { sb.append("<text src=\"${escape(it.name ?: it.contentId ?: "text")}\" region=\"Text\"/>") }
            sb.append("</par>")
        }
        sb.append("</body></smil>")
        return sb.toString()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
