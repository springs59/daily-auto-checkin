package com.autocheckin.daily.data

import org.json.JSONArray
import org.json.JSONObject

data class SiteConfig(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val builtin: Boolean,
    val description: String,
    val checkin: CheckinConfig
) {

    data class CheckinConfig(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: String,
        val success: SuccessRule,
        val alreadySigned: List<String>
    )

    data class SuccessRule(
        val type: String,
        val path: String = "",
        val value: String = ""
    )

    fun toJson(): JSONObject {
        val checkinObj = JSONObject()
        checkinObj.put("url", checkin.url)
        checkinObj.put("method", checkin.method)

        val headersObj = JSONObject()
        checkin.headers.forEach { (k, v) -> headersObj.put(k, v) }
        checkinObj.put("headers", headersObj)

        checkinObj.put("body", checkin.body)

        val successObj = JSONObject()
        successObj.put("type", checkin.success.type)
        successObj.put("path", checkin.success.path)
        successObj.put("value", checkin.success.value)
        checkinObj.put("success", successObj)

        checkinObj.put("alreadySigned", JSONArray(checkin.alreadySigned))

        val root = JSONObject()
        root.put("id", id)
        root.put("name", name)
        root.put("enabled", enabled)
        root.put("builtin", builtin)
        root.put("description", description)
        root.put("checkin", checkinObj)
        return root
    }

    companion object {
        fun fromJson(o: JSONObject): SiteConfig {
            val c = o.optJSONObject("checkin") ?: JSONObject()
            val headers = mutableMapOf<String, String>()
            c.optJSONObject("headers")?.let { h ->
                h.keys().forEach { k -> headers[k] = h.optString(k) }
            }
            val successObj = c.optJSONObject("success") ?: JSONObject()
            val success = SuccessRule(
                type = successObj.optString("type", "contains"),
                path = successObj.optString("path", ""),
                value = successObj.optString("value", "")
            )
            val already = mutableListOf<String>()
            c.optJSONArray("alreadySigned")?.let { arr ->
                for (i in 0 until arr.length()) already.add(arr.optString(i))
            }
            val checkin = CheckinConfig(
                url = c.optString("url", ""),
                method = c.optString("method", "GET").uppercase(),
                headers = headers,
                body = c.optString("body", ""),
                success = success,
                alreadySigned = already
            )
            return SiteConfig(
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                enabled = o.optBoolean("enabled", true),
                builtin = o.optBoolean("builtin", false),
                description = o.optString("description", ""),
                checkin = checkin
            )
        }

        fun listToJson(list: List<SiteConfig>): JSONArray {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr
        }
    }
}
