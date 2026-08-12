package com.autocheckin.daily.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.autocheckin.daily.data.Account
import com.autocheckin.daily.databinding.ItemAccountBinding

class AccountAdapter(
    private val onToggle: (Account, Boolean) -> Unit,
    private val onDelete: (Account) -> Unit,
    private val onClick: (Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.VH>() {

    private val items = mutableListOf<Account>()

    fun submit(list: List<Account>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val account = items[position]
        val b = holder.binding
        b.accountName.text = account.name
        b.accountToken.text = maskToken(account.token)
        b.accountStatus.text =
            if (account.lastTime.isEmpty()) "尚未签到"
            else "${account.lastTime}  ${account.lastStatus}"
        b.accountEnabledSwitch.setOnCheckedChangeListener(null)
        b.accountEnabledSwitch.isChecked = account.enabled
        b.accountEnabledSwitch.setOnCheckedChangeListener { _, checked -> onToggle(account, checked) }
        b.deleteBtn.setOnClickListener { onDelete(account) }
        b.root.setOnClickListener { onClick(account) }
    }

    private fun maskToken(token: String): String = when {
        token.isEmpty() -> "未设置凭证"
        token.length <= 8 -> "••••••••"
        else -> "••••••••" + token.takeLast(6)
    }
}
