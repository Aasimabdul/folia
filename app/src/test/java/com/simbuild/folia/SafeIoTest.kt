package com.simbuild.folia

import com.simbuild.folia.engine.DocumentTooLargeException
import com.simbuild.folia.engine.LimitedInputStream
import com.simbuild.folia.engine.MediaBudget
import com.simbuild.folia.engine.SafeLimits
import com.simbuild.folia.engine.SafeXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class SafeIoTest {

    @Test
    fun limitedStreamAllowsDataUnderTheLimit() {
        val data = ByteArray(1000) { it.toByte() }
        val read = LimitedInputStream(ByteArrayInputStream(data), 1000).readBytes()
        assertEquals(1000, read.size)
    }

    @Test
    fun limitedStreamStopsZipBombStyleOverflow() {
        val data = ByteArray(5000)
        try {
            LimitedInputStream(ByteArrayInputStream(data), 1000).readBytes()
            fail("Expected DocumentTooLargeException")
        } catch (_: DocumentTooLargeException) {
        }
    }

    @Test
    fun mediaBudgetRejectsOversizedAndExcessImages() {
        val budget = MediaBudget(total = 10L * 1024 * 1024)
        assertFalse(budget.tryTake(SafeLimits.MAX_MEDIA_BYTES + 1))
        assertFalse(budget.tryTake(-1))
        assertTrue(budget.tryTake(6L * 1024 * 1024))
        assertFalse(budget.tryTake(6L * 1024 * 1024))
        assertTrue(budget.tryTake(4L * 1024 * 1024))
    }

    @Test
    fun xmlParserRejectsEntityExpansionAndExternalEntities() {
        val bomb = """<?xml version="1.0"?><!DOCTYPE l [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;">]><l>&b;&b;&b;</l>"""
        val xxe = """<?xml version="1.0"?><!DOCTYPE d [<!ENTITY x SYSTEM "file:///etc/passwd">]><d>&x;</d>"""
        for (xml in listOf(bomb, xxe)) {
            val parsed = runCatching {
                SafeXml.factory().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
            }
            assertTrue("DOCTYPE documents must be rejected", parsed.isFailure)
        }
    }

    @Test
    fun xmlParserStillReadsNormalDocuments() {
        val doc = SafeXml.factory().newDocumentBuilder()
            .parse(ByteArrayInputStream("<w:p xmlns:w=\"urn:x\"><w:t>Hi</w:t></w:p>".toByteArray()))
        assertEquals(1, doc.getElementsByTagNameNS("*", "t").length)
    }
}
