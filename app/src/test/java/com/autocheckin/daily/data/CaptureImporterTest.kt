package com.autocheckin.daily.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureImporterTest {

    @Test
    fun parsesCurlRequestAndHeaders() {
        val result = CaptureImporter.parse(
            "curl -X POST 'https://example.com/api/checkin' -H 'Authorization: Bearer value' -d '{\"daily\":true}'".toByteArray(),
            "request.txt"
        )

        assertEquals("cURL", result.format)
        assertEquals(1, result.requests.size)
        assertEquals("POST", result.requests.single().method)
        assertEquals("Bearer value", result.requests.single().headers["Authorization"])
    }

    @Test
    fun groupsAndPrioritizesCheckinRequest() {
        val groups = CaptureImporter.classify(listOf(
            CapturedRequest("https://example.com/api/profile", "GET"),
            CapturedRequest("https://example.com/api/daily-checkin", "POST")
        ))

        assertEquals(1, groups.size)
        assertTrue(groups.single().requests.first().request.url.contains("daily-checkin"))
    }

    @Test
    fun masksSensitiveHeaderValues() {
        assertEquals("••••••••", CaptureImporter.mask("Cookie", "session=value"))
        assertEquals("application/json", CaptureImporter.mask("Content-Type", "application/json"))
    }

    @Test
    fun buildsCompatibleTokenTemplate() {
        val (site, account) = CaptureImporter.buildDraft(
            CapturedRequest("https://example.com/checkin", "POST", mapOf("Authorization" to "Bearer token-value")),
            "Example",
            "Bearer token-value"
        )

        assertEquals("{{token}}", site.checkin.headers["Authorization"])
        assertEquals("Example", account.name.removeSuffix(" 会话"))
    }
}
