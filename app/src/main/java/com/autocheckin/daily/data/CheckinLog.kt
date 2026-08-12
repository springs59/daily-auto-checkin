package com.autocheckin.daily.data

import org.json.JSONArray
import org.json.JSONObject

data class CheckinLog(
    val time: Long,
    val siteName: String,
    val accountName: String,
    val success: Boolean,
    val message: String
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("time", time)
        o.put("siteName", siteName)
        o.put("accountName", accountName)
        o.put("success", success)
        o.put("message", message)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): CheckinLog = CheckinLog(
            time = o.optLong("time"),
            siteName = o.optString("siteName"),
            accountName = o.optString("accountName"),
            success = o.optBoolean("success"),
            message = o.optString("message")
        )

        fun listToJson(list: List<CheckinLog>): JSONArray {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr
        }
    }
}
