package com.autocheckin.daily.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.autocheckin.daily.data.SiteConfig
import com.autocheckin.daily.databinding.ItemSiteBinding

class SiteAdapter(
    private val onToggle: (SiteConfig, Boolean) -> Unit,
    private val onEdit: (SiteConfig) -> Unit,
    private val onReset: (SiteConfig) -> Unit
) : RecyclerView.Adapter<SiteAdapter.VH>() {

    private val items = mutableListOf<SiteConfig>()

    fun submit(list: List<SiteConfig>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemSiteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSiteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val site = items[position]
        val b = holder.binding
        b.siteName.text = site.name
        b.siteDesc.text = site.description.ifEmpty { "(无描述)" }
        b.siteId.text = "id: ${site.id}"
        b.siteEnabledSwitch.setOnCheckedChangeListener(null)
        b.siteEnabledSwitch.isChecked = site.enabled
        b.siteEnabledSwitch.setOnCheckedChangeListener { _, checked -> onToggle(site, checked) }
        b.editBtn.setOnClickListener { onEdit(site) }
        b.resetBtn.visibility = if (site.builtin) View.VISIBLE else View.GONE
        b.resetBtn.setOnClickListener { onReset(site) }
    }
}
