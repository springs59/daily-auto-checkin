package com.autocheckin.daily.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.autocheckin.daily.data.Account
import com.autocheckin.daily.data.CaptureImporter
import com.autocheckin.daily.data.CapturedRequest
import com.autocheckin.daily.data.Repository
import com.autocheckin.daily.data.SiteConfig
import com.autocheckin.daily.databinding.DialogCaptureDraftBinding
import com.autocheckin.daily.databinding.DialogAccountBinding
import com.autocheckin.daily.databinding.FragmentAccountsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.util.UUID

class AccountsFragment : Fragment() {

    private var _binding: FragmentAccountsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: Repository
    private lateinit var adapter: AccountAdapter
    private val sites = mutableListOf<SiteConfig>()
    private var selectedSiteId = ""

    private val importCapture = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取文件")
            val result = CaptureImporter.parse(bytes, uri.lastPathSegment.orEmpty())
            val groups = CaptureImporter.classify(result.requests)
            if (groups.isEmpty()) {
                val detail = result.warnings.joinToString("\n").ifBlank { "文件中没有可导入的 HTTP 请求" }
                showCaptureHelp(detail)
            } else {
                showCaptureCandidates(result.format, groups.flatMap { it.requests }, result.warnings)
            }
        } catch (e: Exception) {
            showCaptureHelp("导入失败：${e.message}")
        }
    }

    private val exportConfig = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireNotNull(requireContext().contentResolver.openOutputStream(uri)) { "无法创建文件" }.bufferedWriter().use {
                it.write(repo.exportConfig().toString(2))
            }
            Toast.makeText(requireContext(), "配置已导出", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val importConfig = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取文件")
            val count = repo.importConfig(JSONObject(text))
            loadSites()
            Toast.makeText(requireContext(), "已导入 $count 个账号", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "导入失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = Repository(requireContext())

        adapter = AccountAdapter(
            onToggle = { account, checked ->
                repo.saveAccount(account.copy(enabled = checked))
                loadAccounts()
            },
            onDelete = { account -> confirmDelete(account) },
            onClick = { account -> showAccountDialog(account) }
        )
        binding.accountList.layoutManager = LinearLayoutManager(requireContext())
        binding.accountList.adapter = adapter

        binding.siteSpinner.setOnItemClickListener { _, _, pos, _ ->
            if (pos in sites.indices) {
                selectedSiteId = sites[pos].id
                loadAccounts()
            }
        }
        binding.fabAddAccount.setOnClickListener { showAccountDialog(null) }
        binding.exportConfigBtn.setOnClickListener { exportConfig.launch("autocheckin-backup.json") }
        binding.importCaptureBtn.setOnClickListener { importCapture.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "application/vnd.tcpdump.pcap")) }
        binding.importConfigBtn.setOnClickListener {
            importConfig.launch(arrayOf("application/json", "text/plain"))
        }

        loadSites()
    }

    private fun loadSites() {
        sites.clear()
        sites.addAll(repo.getEffectiveSites())
        binding.siteSpinner.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sites.map { it.name })
        )
        if (sites.isNotEmpty() && sites.none { it.id == selectedSiteId }) {
            selectedSiteId = sites[0].id
        }
        binding.siteSpinner.setText(sites.firstOrNull { it.id == selectedSiteId }?.name ?: "", false)
        loadAccounts()
    }

    private fun loadAccounts() {
        adapter.submit(repo.getAccounts(selectedSiteId))
    }

    private fun confirmDelete(account: Account) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除账号")
            .setMessage("确定删除账号 \"${account.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                repo.deleteAccount(account.id)
                loadAccounts()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCaptureHelp(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("抓包文件导入说明")
            .setMessage("支持 HAR、HAR JSON、cURL、Postman Collection、Insomnia Export、OpenAPI JSON、PCAP 和 PCAPNG。\n\n浏览器可从网络面板导出 HAR 或复制为 cURL。PCAP 与 PCAPNG 中的 HTTPS 请求需要先在源工具完成 TLS 解密；加密原始包只能提供连接元数据。\n\n$message")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showCaptureCandidates(
        format: String,
        candidates: List<com.autocheckin.daily.data.ScoredCaptureRequest>,
        warnings: List<String>
    ) {
        val labels = candidates.map { candidate ->
            val request = candidate.request
            "${request.method} ${request.url}\n评分 ${candidate.score}：${candidate.evidence.joinToString("、")}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$format：选择签到请求")
            .setMessage(warnings.joinToString("\n").ifBlank { "已按平台和签到语义排序，请确认实际签到请求。" })
            .setItems(labels) { _, which -> showCaptureDraft(candidates[which].request, candidates[which].evidence) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCaptureDraft(request: CapturedRequest, evidence: List<String>) {
        val draft = DialogCaptureDraftBinding.inflate(layoutInflater)
        draft.captureSummary.text = "来源：${request.sourceLabel}\n${request.method} ${request.url}\n${evidence.joinToString("、")}\n敏感请求头在下方以掩码显示。请核对内容后保存。"
        draft.captureSiteName.setText(request.url.substringAfter("://").substringBefore('/'))
        draft.captureUrl.setText(request.url)
        draft.captureMethod.setText(request.method)
        draft.captureHeaders.setText(request.headers.entries.joinToString("\n") { "${it.key}: ${CaptureImporter.mask(it.key, it.value)}" })
        draft.captureBody.setText(request.body)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认签到配置")
            .setView(draft.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        try {
                            val headers = draft.captureHeaders.text?.toString().orEmpty().lineSequence().mapNotNull { line ->
                                line.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
                            }.toMap()
                            val edited = request.copy(
                                url = draft.captureUrl.text?.toString().orEmpty().trim(),
                                method = draft.captureMethod.text?.toString().orEmpty().trim(),
                                headers = headers,
                                body = draft.captureBody.text?.toString().orEmpty()
                            )
                            val token = draft.captureToken.text?.toString().orEmpty()
                            val (site, account) = CaptureImporter.buildDraft(edited, draft.captureSiteName.text?.toString().orEmpty().trim(), token)
                            repo.saveCustomSite(site)
                            if (token.isNotBlank()) repo.saveAccount(account)
                            loadSites()
                            Toast.makeText(requireContext(), "已保存站点 ${site.name}${if (token.isNotBlank()) " 和账号" else "；请补充登录凭据"}", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "保存失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showAccountDialog(existing: Account?) {
        val db = DialogAccountBinding.inflate(layoutInflater)
        if (existing != null) {
            db.etAccountName.setText(existing.name)
            db.etAccountToken.setText(existing.token)
            db.switchAccountEnabled.isChecked = existing.enabled
        }
        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "添加账号" else "编辑账号")
            .setView(db.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = db.etAccountName.text.toString().trim()
                val token = db.etAccountToken.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入账号名称", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (token.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入登录凭证", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val account = existing?.copy(
                    name = name,
                    token = token,
                    enabled = db.switchAccountEnabled.isChecked
                ) ?: Account(
                    id = UUID.randomUUID().toString(),
                    siteId = selectedSiteId,
                    name = name,
                    token = token,
                    enabled = db.switchAccountEnabled.isChecked
                )
                repo.saveAccount(account)
                dlg.dismiss()
                loadAccounts()
            }
        }
        dlg.show()
    }

    override fun onResume() {
        super.onResume()
        loadSites()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
