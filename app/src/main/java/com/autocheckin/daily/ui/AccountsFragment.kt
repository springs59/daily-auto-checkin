package com.autocheckin.daily.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.autocheckin.daily.data.Account
import com.autocheckin.daily.data.Repository
import com.autocheckin.daily.data.SiteConfig
import com.autocheckin.daily.databinding.DialogAccountBinding
import com.autocheckin.daily.databinding.FragmentAccountsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.UUID

class AccountsFragment : Fragment() {

    private var _binding: FragmentAccountsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: Repository
    private lateinit var adapter: AccountAdapter
    private val sites = mutableListOf<SiteConfig>()
    private var selectedSiteId = ""

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
