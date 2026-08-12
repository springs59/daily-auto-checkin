package com.autocheckin.daily.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.autocheckin.daily.R
import com.autocheckin.daily.data.CheckinLog
import com.autocheckin.daily.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val items = mutableListOf<CheckinLog>()

    fun submit(list: List<CheckinLog>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val log = items[position]
        val b = holder.binding
        b.logTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(log.time))
        b.logTitle.text = "${log.siteName} · ${log.accountName}"
        b.logSuccess.text = if (log.success) "成功" else "失败"
        b.logSuccess.setTextColor(
            ContextCompat.getColor(
                b.root.context,
                if (log.success) R.color.log_success else R.color.log_fail
            )
        )
        b.logMessage.text = log.message
    }
}
