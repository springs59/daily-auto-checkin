package com.autocheckin.daily.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Repository(private val context: Context) {

    private val prefs = context.getSharedPreferences("autocheckin", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_LOGS = "logs"
        private const val KEY_CUSTOM_SITES = "custom_sites"
        private const val KEY_SITE_OVERRIDES = "site_overrides"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_SCHEDULE_HOUR = "schedule_hour"
        private const val KEY_SCHEDULE_MINUTE = "schedule_minute"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_XIAOHEIHE_CHECKIN_ENABLED = "xiaoheihe_checkin_enabled"
        private const val KEY_XIAOHEIHE_TASK_ENABLED = "xiaoheihe_task_enabled"
        private const val KEY_XIAOHEIHE_REWARD_ENABLED = "xiaoheihe_reward_enabled"
        private const val KEY_XIAOHEIHE_LOTTERY_ENABLED = "xiaoheihe_lottery_enabled"
        const val MAX_LOGS = 200
    }

    // ---------------- Settings ----------------
    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    var scheduleHour: Int
        get() = prefs.getInt(KEY_SCHEDULE_HOUR, 10)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_HOUR, value).apply()

    var scheduleMinute: Int
        get() = prefs.getInt(KEY_SCHEDULE_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_MINUTE, value).apply()

    var xiaoheiheCheckinEnabled: Boolean
        get() = prefs.getBoolean(KEY_XIAOHEIHE_CHECKIN_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_XIAOHEIHE_CHECKIN_ENABLED, value).apply()

    var xiaoheiheTaskEnabled: Boolean
        get() = prefs.getBoolean(KEY_XIAOHEIHE_TASK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_XIAOHEIHE_TASK_ENABLED, value).apply()

    var xiaoheiheRewardEnabled: Boolean
        get() = prefs.getBoolean(KEY_XIAOHEIHE_REWARD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_XIAOHEIHE_REWARD_ENABLED, value).apply()

    var xiaoheiheLotteryEnabled: Boolean
        get() = prefs.getBoolean(KEY_XIAOHEIHE_LOTTERY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_XIAOHEIHE_LOTTERY_ENABLED, value).apply()

    fun scheduleText(): String = "%02d:%02d".format(scheduleHour, scheduleMinute)

    // ---------------- Sites ----------------
    fun getBuiltinSites(): List<SiteConfig> {
        return try {
            val text = context.assets.open("sites.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            (0 until arr.length()).map { SiteConfig.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readJsonArray(key: String): JSONArray {
        val raw = prefs.getString(key, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun writeJsonArray(key: String, arr: JSONArray) {
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun getCustomSites(): List<SiteConfig> {
        val arr = readJsonArray(KEY_CUSTOM_SITES)
        return (0 until arr.length()).map { SiteConfig.fromJson(arr.getJSONObject(it)) }
    }

    fun saveCustomSite(site: SiteConfig) {
        val list = getCustomSites().filter { it.id != site.id }.toMutableList()
        list.add(site)
        writeJsonArray(KEY_CUSTOM_SITES, SiteConfig.listToJson(list))
    }

    fun deleteCustomSite(id: String) {
        val list = getCustomSites().filter { it.id != id }
        writeJsonArray(KEY_CUSTOM_SITES, SiteConfig.listToJson(list))
        deleteAccountsForSite(id)
    }

    fun getSiteOverride(id: String): SiteConfig? {
        val raw = prefs.getString("$KEY_SITE_OVERRIDES:$id", null) ?: return null
        return try {
            SiteConfig.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun setSiteOverride(site: SiteConfig) {
        prefs.edit().putString("$KEY_SITE_OVERRIDES:${site.id}", site.toJson().toString()).apply()
    }

    fun removeSiteOverride(id: String) {
        prefs.edit().remove("$KEY_SITE_OVERRIDES:$id").apply()
    }

    fun getEffectiveSites(): List<SiteConfig> {
        val builtinIds = getBuiltinSites().map { it.id }.toSet()
        val result = getBuiltinSites().map { getSiteOverride(it.id) ?: it }.toMutableList()
        getCustomSites().filter { it.id !in builtinIds }.forEach { result.add(it) }
        return result
    }

    fun getSite(id: String): SiteConfig? = getEffectiveSites().firstOrNull { it.id == id }

    // ---------------- Accounts ----------------
    fun getAccounts(): List<Account> {
        val arr = readJsonArray(KEY_ACCOUNTS)
        return (0 until arr.length()).map { Account.fromJson(arr.getJSONObject(it)) }
    }

    fun getAccounts(siteId: String): List<Account> = getAccounts().filter { it.siteId == siteId }

    fun saveAccount(account: Account) {
        val list = getAccounts().filter { it.id != account.id }.toMutableList()
        list.add(account)
        writeJsonArray(KEY_ACCOUNTS, Account.listToJson(list))
    }

    fun deleteAccount(id: String) {
        val list = getAccounts().filter { it.id != id }
        writeJsonArray(KEY_ACCOUNTS, Account.listToJson(list))
    }

    private fun deleteAccountsForSite(siteId: String) {
        val list = getAccounts().filter { it.siteId != siteId }
        writeJsonArray(KEY_ACCOUNTS, Account.listToJson(list))
    }

    // ---------------- Backup ----------------
    fun exportConfig(): JSONObject = JSONObject().apply {
        put("format", "autocheckin-backup")
        put("version", 1)
        put("customSites", SiteConfig.listToJson(getCustomSites()))
        put("siteOverrides", JSONArray().apply {
            getBuiltinSites().forEach { site ->
                getSiteOverride(site.id)?.let { put(it.toJson()) }
            }
        })
        put("accounts", Account.listToJson(getAccounts()))
        put("settings", JSONObject().apply {
            put("scheduleEnabled", scheduleEnabled)
            put("scheduleHour", scheduleHour)
            put("scheduleMinute", scheduleMinute)
        })
    }

    fun importConfig(backup: JSONObject): Int {
        require(backup.optString("format") == "autocheckin-backup") { "不是支持的配置文件" }
        val importedSites = backup.optJSONArray("customSites") ?: JSONArray()
        val importedOverrides = backup.optJSONArray("siteOverrides") ?: JSONArray()
        val importedAccounts = backup.optJSONArray("accounts") ?: JSONArray()
        val builtInIds = getBuiltinSites().map { it.id }.toSet()

        for (index in 0 until importedSites.length()) {
            val site = SiteConfig.fromJson(importedSites.getJSONObject(index)).copy(builtin = false)
            require(site.id.isNotBlank() && site.name.isNotBlank() && site.checkin.url.isNotBlank()) { "站点配置不完整" }
            if (site.id !in builtInIds) saveCustomSite(site)
        }
        for (index in 0 until importedOverrides.length()) {
            val site = SiteConfig.fromJson(importedOverrides.getJSONObject(index))
            if (site.id in builtInIds) setSiteOverride(site.copy(builtin = true))
        }
        var count = 0
        for (index in 0 until importedAccounts.length()) {
            val account = Account.fromJson(importedAccounts.getJSONObject(index))
            if (account.siteId.isNotBlank() && account.name.isNotBlank() && account.token.isNotBlank() && getSite(account.siteId) != null) {
                saveAccount(account.copy(id = java.util.UUID.randomUUID().toString()))
                count++
            }
        }
        backup.optJSONObject("settings")?.let { settings ->
            scheduleEnabled = settings.optBoolean("scheduleEnabled", scheduleEnabled)
            scheduleHour = settings.optInt("scheduleHour", scheduleHour).coerceIn(0, 23)
            scheduleMinute = settings.optInt("scheduleMinute", scheduleMinute).coerceIn(0, 59)
        }
        return count
    }

    fun updateAccountStatus(accountId: String, status: String) {
        val now = System.currentTimeMillis()
        val timeText = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(now))
        val dateText = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(now))
        val list = getAccounts().map {
            if (it.id == accountId) {
                it.copy(lastStatus = status, lastTime = timeText, lastDate = dateText)
            } else it
        }
        writeJsonArray(KEY_ACCOUNTS, Account.listToJson(list))
    }

    // ---------------- Logs ----------------
    fun getLogs(): List<CheckinLog> {
        val arr = readJsonArray(KEY_LOGS)
        return (0 until arr.length()).map { CheckinLog.fromJson(arr.getJSONObject(it)) }
    }

    fun addLog(log: CheckinLog) {
        val list = getLogs().toMutableList()
        list.add(0, log)
        if (list.size > MAX_LOGS) list.subList(MAX_LOGS, list.size).clear()
        writeJsonArray(KEY_LOGS, CheckinLog.listToJson(list))
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }
}
