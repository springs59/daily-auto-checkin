package com.autocheckin.daily.core

import android.content.Context
import com.autocheckin.daily.data.CheckinLog
import com.autocheckin.daily.data.Repository
import com.autocheckin.daily.net.CheckinEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CheckinExecutor {

    /**
     * 对当前启用站点下的启用账号逐一执行签到。
     * [force] 为 true 时跳过"今日已签"去重（手动触发使用）。
     * 该方法内部捕获所有异常，单个账号失败不影响其他账号。
     */
    suspend fun runAll(context: Context, force: Boolean) {
        val repo = Repository(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sites = repo.getEffectiveSites().filter { it.enabled && isEnabledForSite(it.id, repo) }

        for (site in sites) {
            val accounts = repo.getAccounts(site.id).filter { it.enabled }
            if (accounts.isEmpty()) continue
            for (account in accounts) {
                if (!force && account.lastDate == today) {
                    repo.updateAccountStatus(account.id, "今日已签到")
                    continue
                }
                val result = try {
                    CheckinEngine.run(site, account)
                } catch (e: Exception) {
                    com.autocheckin.daily.net.CheckinResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
                }
                repo.updateAccountStatus(account.id, result.message)
                repo.addLog(
                    CheckinLog(
                        time = System.currentTimeMillis(),
                        siteName = site.name,
                        accountName = account.name,
                        success = result.success,
                        message = result.message
                    )
                )
            }
        }
    }

    private fun isEnabledForSite(siteId: String, repo: Repository): Boolean = when (siteId) {
        "xiaoheihe-checkin" -> repo.xiaoheiheCheckinEnabled
        "xiaoheihe-task" -> repo.xiaoheiheTaskEnabled
        "xiaoheihe-reward" -> repo.xiaoheiheRewardEnabled
        "xiaoheihe-lottery" -> repo.xiaoheiheLotteryEnabled
        else -> true
    }
}
