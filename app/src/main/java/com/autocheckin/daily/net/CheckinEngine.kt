package com.autocheckin.daily.net

import com.autocheckin.daily.data.Account
import com.autocheckin.daily.data.SiteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CheckinResult(val success: Boolean, val message: String)

object CheckinEngine {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun run(site: SiteConfig, account: Account): CheckinResult =
        withContext(Dispatchers.IO) {
            try {
                val cfg = site.checkin
                if (cfg.url.isBlank()) {
                    return@withContext CheckinResult(false, "站点未配置 URL")
                }
                val url = substitute(cfg.url, account)
                val bodyText = substitute(cfg.body, account)
                val headerBuilder = Headers.Builder()
                cfg.headers.forEach { (k, v) ->
                    val value = substitute(v, account)
                    if (k.isNotBlank()) headerBuilder.add(k, value)
                }
                val reqBuilder = Request.Builder().url(url).headers(headerBuilder.build())
                val method = cfg.method.uppercase()
                if (method == "POST" || method == "PUT") {
                    val contentType = cfg.headers["Content-Type"] ?: "application/json"
                    reqBuilder.method(method, bodyText.toRequestBody(contentType.toMediaType()))
                } else {
                    reqBuilder.get()
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        CheckinResult(false, "HTTP ${resp.code}: ${text.take(160)}")
                    } else {
                        evaluate(cfg, text)
                    }
                }
            } catch (e: Exception) {
                CheckinResult(false, e.javaClass.simpleName + ": " + (e.message ?: "未知错误"))
            }
        }

    private fun substitute(template: String, account: Account): String =
        template
            .replace("{{token}}", account.token)
            .replace("{{accountName}}", account.name)

    private fun evaluate(cfg: SiteConfig.CheckinConfig, body: String): CheckinResult {
        cfg.alreadySigned.firstOrNull { body.contains(it) }?.let {
            return CheckinResult(true, "今日已签到(重复签到)")
        }
        val rule = cfg.success
        return when (rule.type) {
            "json" -> {
                val ok = jsonFieldEquals(body, rule.path, rule.value)
                CheckinResult(ok, if (ok) "签到成功" else "签到失败: 字段不匹配(${body.take(200)})")
            }
            "json_not" -> {
                val bad = jsonFieldEquals(body, rule.path, rule.value)
                CheckinResult(!bad, if (!bad) "签到成功" else "签到失败: 命中排除规则(${body.take(200)})")
            }
            "none" -> CheckinResult(true, "签到成功")
            else -> {
                val ok = body.contains(rule.value)
                CheckinResult(ok, if (ok) "签到成功" else "签到失败: 未命中关键字(${body.take(200)})")
            }
        }
    }

    private fun jsonFieldEquals(body: String, path: String, expected: String): Boolean {
        return try {
            val root = JSONObject(body)
            var cur: Any? = root
            val parts = path.split(".").filter { it.isNotBlank() }
            for (p in parts) {
                cur = when (cur) {
                    is JSONObject -> {
                        if (!cur.has(p)) return false
                        cur.get(p)
                    }
                    is JSONArray -> {
                        val idx = p.toIntOrNull() ?: return false
                        if (idx < 0 || idx >= cur.length()) return false
                        cur.get(idx)
                    }
                    else -> return false
                }
            }
            cur != null && cur.toString() == expected
        } catch (e: Exception) {
            false
        }
    }
}
