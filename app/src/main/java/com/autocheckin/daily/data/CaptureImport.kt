package com.autocheckin.daily.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class CapturedRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val sourceLabel: String = ""
)

data class CaptureParseResult(
    val format: String,
    val requests: List<CapturedRequest>,
    val warnings: List<String> = emptyList(),
    val unparsedCount: Int = 0
)

data class ScoredCaptureRequest(val request: CapturedRequest, val score: Int, val evidence: List<String>)
data class CapturePlatformGroup(val name: String, val requests: List<ScoredCaptureRequest>, val evidence: List<String>)

object CaptureImporter {
    private val sensitiveNames = setOf("authorization", "cookie", "set-cookie", "token", "secret", "password", "session")
    private val verbs = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    fun parse(bytes: ByteArray, _filename: String): CaptureParseResult {
        if (bytes.size > 10 * 1024 * 1024) error("文件超过 10 MB，请导出单个目标会话")
        val text = bytes.toString(Charsets.ISO_8859_1)
        val trimmed = text.trimStart()
        return when {
            bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x0a, 0x0d, 0x0d, 0x0a)) -> parsePacket(text, "PCAPNG")
            isPcap(bytes) -> parsePacket(text, "PCAP")
            trimmed.startsWith("curl ") || trimmed.startsWith("curl\n") -> parseCurl(text)
            trimmed.startsWith("{") || trimmed.startsWith("[") -> parseJson(JSONObject(trimmed), _filename)
            else -> error("无法识别文件格式。请选择 HAR、cURL、Postman、Insomnia、OpenAPI、PCAP 或 PCAPNG 文件")
        }
    }

    fun classify(requests: List<CapturedRequest>): List<CapturePlatformGroup> = requests
        .groupBy { host(it.url) }
        .map { (domain, grouped) ->
            val scored = grouped.map { request -> score(request) }.sortedByDescending { it.score }
            CapturePlatformGroup(platformName(domain), scored, listOf("平台域名：$domain") + scored.flatMap { it.evidence }.distinct())
        }.sortedBy { it.name }

    fun mask(name: String, value: String): String {
        val normalized = name.lowercase(Locale.ROOT)
        return if (sensitiveNames.any { normalized.contains(it) }) "••••••••" else value
    }

    fun buildDraft(request: CapturedRequest, siteName: String, token: String): Pair<SiteConfig, Account> {
        require(siteName.isNotBlank()) { "请输入站点名称" }
        require(request.url.isNotBlank()) { "请求 URL 为空" }
        val matchingHeader = request.headers.entries.firstOrNull { sensitiveNames.any { key -> it.key.lowercase().contains(key) } }
        val normalizedToken = if (isXiaoheihe(request)) XiaoheiheCredentialParser.normalize(token) else token
        val headers = request.headers.toMutableMap()
        if (matchingHeader != null && normalizedToken.isNotBlank()) headers[matchingHeader.key] = "{{token}}"
        val body = if (normalizedToken.isNotBlank()) request.body.replace(token, "{{token}}") else request.body
        val success = when {
            request.responseBody?.contains("\"success\":true") == true -> SiteConfig.SuccessRule("contains", value = "\"success\":true")
            request.responseBody?.contains("\"code\":0") == true -> SiteConfig.SuccessRule("contains", value = "\"code\":0")
            else -> SiteConfig.SuccessRule("none")
        }
        val siteId = siteIdFor(request)
        val site = SiteConfig(siteId, siteName, true, false, "从 ${request.sourceLabel} 导入", SiteConfig.CheckinConfig(
            request.url, request.method.uppercase(), headers, body, success, emptyList()
        ))
        return site to Account(java.util.UUID.randomUUID().toString(), siteId, "$siteName 会话", normalizedToken, true)
    }

    private fun parseJson(root: JSONObject, filename: String): CaptureParseResult = when {
        root.has("log") -> parseHar(root)
        root.has("item") && root.has("info") -> parsePostman(root)
        root.has("resources") -> parseInsomnia(root)
        root.has("paths") -> parseOpenApi(root)
        else -> error("JSON 文件不包含 HAR、Postman、Insomnia 或 OpenAPI 结构")
    }

    private fun parseHar(root: JSONObject): CaptureParseResult {
        val entries = root.getJSONObject("log").optJSONArray("entries") ?: JSONArray()
        val requests = (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val req = entry.optJSONObject("request") ?: return@mapNotNull null
            val headers = headers(req.optJSONArray("headers"))
            req.optJSONArray("cookies")?.let { cookies -> if (cookies.length() > 0) headers["Cookie"] = (0 until cookies.length()).joinToString("; ") { cookie ->
                val item = cookies.optJSONObject(cookie); "${item?.optString("name") ?: ""}=${item?.optString("value") ?: ""}"
            } }
            CapturedRequest(req.optString("url"), req.optString("method", "GET"), headers,
                req.optJSONObject("postData")?.optString("text", "") ?: "", entry.optJSONObject("response")?.optInt("status"),
                entry.optJSONObject("response")?.optJSONObject("content")?.optString("text"), "HAR")
        }.filter { it.url.isNotBlank() }
        return CaptureParseResult("HAR", requests, unparsedCount = entries.length() - requests.size)
    }

    private fun parsePostman(root: JSONObject): CaptureParseResult {
        val result = mutableListOf<CapturedRequest>()
        fun visit(items: JSONArray?) { for (i in 0 until (items?.length() ?: 0)) { val item = items?.optJSONObject(i) ?: continue
            item.optJSONArray("item")?.let(::visit)
            val request = item.optJSONObject("request") ?: continue
            val url = request.opt("url")?.let { if (it is JSONObject) it.optString("raw") else it.toString() }.orEmpty()
            result += CapturedRequest(url, request.optString("method", "GET"), headers(request.optJSONArray("header")), request.optJSONObject("body")?.optString("raw", "").orEmpty(), sourceLabel = "Postman")
        } }
        visit(root.optJSONArray("item"))
        return CaptureParseResult("Postman Collection", result.filter { it.url.isNotBlank() })
    }

    private fun parseInsomnia(root: JSONObject): CaptureParseResult {
        val resources = root.optJSONArray("resources") ?: JSONArray()
        val requests = (0 until resources.length()).mapNotNull { i -> resources.optJSONObject(i)?.takeIf { it.optString("_type") == "request" }?.let {
            CapturedRequest(it.optString("url"), it.optString("method", "GET"), headers(it.optJSONArray("headers")), it.optJSONObject("body")?.optString("text", "").orEmpty(), sourceLabel = "Insomnia")
        } }.filter { it.url.isNotBlank() }
        return CaptureParseResult("Insomnia Export", requests)
    }

    private fun parseOpenApi(root: JSONObject): CaptureParseResult {
        val base = root.optJSONArray("servers")?.optJSONObject(0)?.optString("url").orEmpty()
        val requests = mutableListOf<CapturedRequest>()
        root.optJSONObject("paths")?.keys()?.forEach { path -> root.getJSONObject("paths").optJSONObject(path)?.keys()?.forEach { method ->
            if (method.uppercase() in verbs && base.isNotBlank()) requests += CapturedRequest(base.trimEnd('/') + path, method.uppercase(), sourceLabel = "OpenAPI")
        } }
        return CaptureParseResult("OpenAPI", requests, listOf("OpenAPI 是接口定义，可能缺少登录凭据和请求体"))
    }

    private fun parseCurl(text: String): CaptureParseResult {
        val url = Regex("(?:curl\\s+)?(?:['\"])?(https?://[^\\s'\"]+)").find(text)?.groupValues?.getOrNull(1).orEmpty()
        val method = Regex("(?:-X|--request)\\s+['\"]?([A-Za-z]+)").find(text)?.groupValues?.getOrNull(1) ?: if (text.contains("-d ") || text.contains("--data")) "POST" else "GET"
        val headers = Regex("(?:-H|--header)\\s+['\"]([^'\"]+)['\"]").findAll(text).associate { match ->
            val pair = match.groupValues[1].split(":", limit = 2); pair[0].trim() to pair.getOrElse(1) { "" }.trim()
        }.toMutableMap()
        val body = Regex("(?:-d|--data(?:-raw)?)\\s+['\"](.*?)['\"]").find(text)?.groupValues?.getOrNull(1).orEmpty()
        return CaptureParseResult("cURL", listOf(CapturedRequest(url, method, headers, body, sourceLabel = "cURL")).filter { it.url.isNotBlank() })
    }

    private fun parsePacket(text: String, format: String): CaptureParseResult {
        val starts = Regex("(?m)(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS) ([^\\s]+) HTTP/1\\.[01]").findAll(text).toList()
        val requests = starts.mapNotNull { match ->
            val part = text.substring(match.range.first, minOf(text.length, match.range.first + 16384)); val headerEnd = part.indexOf("\r\n\r\n").takeIf { it >= 0 } ?: part.indexOf("\n\n")
            if (headerEnd < 0) return@mapNotNull null
            val lines = part.substring(0, headerEnd).split(Regex("\\r?\\n")); val headers = lines.drop(1).mapNotNull { line -> line.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
            val host = headers.entries.firstOrNull { it.key.equals("Host", true) }?.value ?: return@mapNotNull null
            val path = match.groupValues[2]; CapturedRequest("http://$host$path", match.groupValues[1], headers, part.substring(headerEnd + if (part.startsWith("\r\n\r\n", headerEnd)) 4 else 2).take(4096), sourceLabel = format)
        }
        val warnings = if (requests.isEmpty()) listOf("未发现可读 HTTP 请求。HTTPS 流量需要先在源工具完成 TLS 解密，建议导出 HAR 或 cURL。") else emptyList()
        return CaptureParseResult(format, requests, warnings)
    }

    private fun score(request: CapturedRequest): ScoredCaptureRequest {
        val source = "${request.url} ${request.body} ${request.responseBody.orEmpty()}".lowercase(Locale.ROOT)
        val words = listOf("sign", "checkin", "attendance", "签到", "daily", "reward", "task", "mission", "claim", "任务", "奖励", "领取", "奖池")
        val matched = words.filter { source.contains(it) }; val evidence = matched.map { "检测到签到语义：$it" }.toMutableList()
        if (request.method.uppercase() in setOf("POST", "PUT", "PATCH")) evidence += "写操作请求"
        return ScoredCaptureRequest(request, matched.size * 10 + if (evidence.contains("写操作请求")) 3 else 0, evidence.ifEmpty { listOf("按主域名归组") })
    }

    private fun headers(items: JSONArray?): MutableMap<String, String> = buildMap { for (i in 0 until (items?.length() ?: 0)) { val item = items?.optJSONObject(i) ?: continue; val name = item.optString("name", item.optString("key")); val value = item.optString("value"); if (name.isNotBlank()) put(name, value) } }.toMutableMap()
    private fun host(url: String): String = try { URI(url).host?.removePrefix("www.") ?: "未知平台" } catch (_: Exception) { "未知平台" }
    private fun platformName(domain: String): String = when {
        domain.contains("xiaoheihe") || domain.contains("heybox") -> "小黑盒（$domain）"
        domain.contains("miyoushe") || domain.contains("mihoyo") -> "米游社（$domain）"
        else -> domain
    }
    private fun siteIdFor(request: CapturedRequest): String {
        val domain = host(request.url)
        if (domain.contains("xiaoheihe") || domain.contains("heybox")) {
            val source = "${request.url} ${request.body} ${request.responseBody.orEmpty()}".lowercase(Locale.ROOT)
            val action = when {
                source.contains("sign") || source.contains("checkin") || source.contains("签到") -> "checkin"
                source.contains("reward") || source.contains("claim") || source.contains("奖励") || source.contains("领取") -> "reward"
                source.contains("lottery") || source.contains("pool") || source.contains("奖池") -> "lottery"
                else -> "task"
            }
            return "xiaoheihe-$action"
        }
        return "import-" + domain.replace(Regex("[^a-zA-Z0-9]+"), "-").trim('-')
    }
    private fun isXiaoheihe(request: CapturedRequest): Boolean =
        host(request.url).let { it.contains("xiaoheihe") || it.contains("heybox") }
    private fun isPcap(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes.copyOfRange(0, 4).let { it.contentEquals(byteArrayOf(0xd4.toByte(), 0xc3.toByte(), 0xb2.toByte(), 0xa1.toByte())) || it.contentEquals(byteArrayOf(0xa1.toByte(), 0xb2.toByte(), 0xc3.toByte(), 0xd4.toByte())) || it.contentEquals(byteArrayOf(0x4d, 0x3c, 0xb2.toByte(), 0xa1.toByte())) || it.contentEquals(byteArrayOf(0xa1.toByte(), 0xb2.toByte(), 0x3c, 0x4d)) }
}
