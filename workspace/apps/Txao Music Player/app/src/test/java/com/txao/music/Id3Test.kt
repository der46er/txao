package com.txao.music

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Id3Test {
    private val audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0, 1, 2, 3, 4)

    private fun run(input: ByteArray, edit: TagEdit): ByteArray {
        val out = ByteArrayOutputStream()
        Id3.rewrite(ByteArrayInputStream(input), edit, out)
        return out.toByteArray()
    }

    @Test fun addsTagToUntaggedFileAndKeepsAudio() {
        val out = run(audio, TagEdit(title = "Song", artist = "Band", trackNo = 3, year = 1999))
        val (frames, offset) = Id3.inspect(out)
        assertEquals("Song", Id3.text(frames, "TIT2"))
        assertEquals("Band", Id3.text(frames, "TPE1"))
        assertEquals("3", Id3.text(frames, "TRCK"))
        assertEquals("1999", Id3.text(frames, "TDRC"))
        assertArrayEquals(audio, out.copyOfRange(offset, out.size))
    }

    @Test fun replacesEditedFieldsAndKeepsOthers() {
        val first = run(audio, TagEdit(title = "Old", artist = "Keep", album = "Alb"))
        val second = run(first, TagEdit(title = "New", album = ""))
        val (frames, offset) = Id3.inspect(second)
        assertEquals("New", Id3.text(frames, "TIT2"))
        assertEquals("Keep", Id3.text(frames, "TPE1"))
        assertNull(Id3.text(frames, "TALB"))
        assertArrayEquals(audio, second.copyOfRange(offset, second.size))
    }

    @Test fun storesCoverAsFrontPicture() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val (frames, _) = Id3.inspect(run(audio, TagEdit(cover = jpeg)))
        val apic = frames.single { it.id == "APIC" }.data
        assertArrayEquals(jpeg, apic.copyOfRange(apic.size - jpeg.size, apic.size))
    }
}
