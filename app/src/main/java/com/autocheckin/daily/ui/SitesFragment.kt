package com.autocheckin.daily.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.autocheckin.daily.data.Repository
import com.autocheckin.daily.data.SiteConfig
import com.autocheckin.daily.databinding.DialogSiteJsonBinding
import com.autocheckin.daily.databinding.FragmentSitesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject

class SitesFragment : Fragment() {

    private var _binding: FragmentSitesBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: Repository
    private lateinit var adapter: SiteAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSitesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = Repository(requireContext())

        adapter = SiteAdapter(
            onToggle = { site, checked ->
                val updated = site.copy(enabled = checked)
                if (site.builtin) repo.setSiteOverride(updated) else repo.saveCustomSite(updated)
                refresh()
            },
            onEdit = { site -> showJsonDialog(site) },
            onReset = { site ->
                repo.removeSiteOverride(site.id)
                refresh()
            },
            onDelete = { site -> confirmDelete(site) }
            }
        )
        binding.siteList.layoutManager = LinearLayoutManager(requireContext())
        binding.siteList.adapter = adapter
        binding.fabAddSite.setOnClickListener { showJsonDialog(null) }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.submit(repo.getEffectiveSites())
    }

    private fun showJsonDialog(existing: SiteConfig?) {
        val db = DialogSiteJsonBinding.inflate(layoutInflater)
        db.etSiteJson.setText(
            if (existing != null) existing.toJson().toString(2) else DEFAULT_TEMPLATE
        )
        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "添加自定义站点" else "编辑站点配置")
            .setView(db.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = db.etSiteJson.text.toString().trim()
                val parsed = try {
                    SiteConfig.fromJson(JSONObject(text))
                } catch (e: Exception) {
                    null
                }
                if (parsed == null) {
                    Toast.makeText(requireContext(), "JSON 解析失败，请检查格式", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (parsed.id.isBlank() || parsed.name.isBlank() || parsed.checkin.url.isBlank()) {
                    Toast.makeText(requireContext(), "必须包含 id / name / checkin.url", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val finalSite = if (existing != null) {
                    parsed.copy(builtin = existing.builtin)
                } else {
                    val builtinIds = repo.getBuiltinSites().map { it.id }
                    if (parsed.id in builtinIds) parsed.copy(builtin = true) else parsed.copy(builtin = false)
                }
                if (finalSite.builtin) repo.setSiteOverride(finalSite)
                else repo.saveCustomSite(finalSite)
                dlg.dismiss()
                refresh()
            }
        }
        dlg.show()
    }

    private fun confirmDelete(site: SiteConfig) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除站点")
            .setMessage("删除 \"${site.name}\" 及其关联账号？")
            .setPositiveButton("删除") { _, _ ->
                repo.deleteCustomSite(site.id)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val DEFAULT_TEMPLATE = """
            {
              "id": "mysite",
              "name": "我的站点",
              "enabled": true,
              "builtin": false,
              "description": "自定义站点",
              "checkin": {
                "url": "https://example.com/api/checkin",
                "method": "POST",
                "headers": {
                  "Content-Type": "application/json",
                  "Cookie": "{{token}}"
                },
                "body": "{}",
                "success": {
                  "type": "contains",
                  "value": "success"
                },
                "alreadySigned": []
              }
            }
        """.trimIndent()
    }
}
