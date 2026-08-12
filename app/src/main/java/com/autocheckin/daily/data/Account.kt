package com.autocheckin.daily.data

import org.json.JSONArray
import org.json.JSONObject

data class Account(
    val id: String,
    val siteId: String,
    val name: String,
    val token: String,
    val enabled: Boolean,
    var lastStatus: String = "",
    var lastTime: String = "",
    var lastDate: String = ""
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("siteId", siteId)
        o.put("name", name)
        o.put("token", token)
        o.put("enabled", enabled)
        o.put("lastStatus", lastStatus)
        o.put("lastTime", lastTime)
        o.put("lastDate", lastDate)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Account = Account(
            id = o.optString("id", ""),
            siteId = o.optString("siteId", ""),
            name = o.optString("name", ""),
            token = o.optString("token", ""),
            enabled = o.optBoolean("enabled", true),
            lastStatus = o.optString("lastStatus", ""),
            lastTime = o.optString("lastTime", ""),
            lastDate = o.optString("lastDate", "")
        )

        fun listToJson(list: List<Account>): JSONArray {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr
        }
    }
}
