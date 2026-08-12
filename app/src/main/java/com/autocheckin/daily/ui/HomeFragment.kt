package com.autocheckin.daily.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.autocheckin.daily.core.CheckinExecutor
import com.autocheckin.daily.core.CheckinScheduler
import com.autocheckin.daily.core.CheckinService
import com.autocheckin.daily.data.Repository
import com.autocheckin.daily.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: Repository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = Repository(requireContext())

        binding.serviceSwitch.isChecked = repo.serviceEnabled
        binding.scheduleSwitch.isChecked = repo.scheduleEnabled
        binding.timeButton.text = repo.scheduleText()

        binding.serviceSwitch.setOnCheckedChangeListener { _, checked ->
            repo.serviceEnabled = checked
            if (checked) startService() else stopService()
            refresh()
        }

        binding.scheduleSwitch.setOnCheckedChangeListener { _, checked ->
            repo.scheduleEnabled = checked
            if (checked) {
                if (repo.serviceEnabled) CheckinScheduler.scheduleNext(requireContext())
            } else {
                CheckinScheduler.cancel(requireContext())
            }
            refresh()
        }

        binding.timeButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    repo.scheduleHour = hour
                    repo.scheduleMinute = minute
                    binding.timeButton.text = repo.scheduleText()
                    if (repo.serviceEnabled && repo.scheduleEnabled) {
                        CheckinScheduler.scheduleNext(requireContext())
                    }
                    refresh()
                },
                repo.scheduleHour,
                repo.scheduleMinute,
                true
            ).show()
        }

        binding.checkinNowButton.setOnClickListener {
            binding.checkinNowButton.isEnabled = false
            binding.checkinNowButton.text = "签到中..."
            lifecycleScope.launch {
                try {
                    CheckinExecutor.runAll(requireContext(), force = true)
                    Toast.makeText(requireContext(), "签到完成", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "签到出错: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.checkinNowButton.isEnabled = true
                    binding.checkinNowButton.text = "立即签到"
                    refresh()
                }
            }
        }

        refresh()
    }

    private fun startService() {
        val ctx = requireContext()
        try {
            val intent = Intent(ctx, CheckinService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
            repo.serviceEnabled = false
            binding.serviceSwitch.isChecked = false
        }
    }

    private fun stopService() {
        CheckinScheduler.cancel(requireContext())
        requireContext().stopService(Intent(requireContext(), CheckinService::class.java))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val sites = repo.getEffectiveSites()
        val accounts = repo.getAccounts()
        val enabledSites = sites.count { it.enabled }
        val enabledAccounts = accounts.count { it.enabled }
        val nextText = if (repo.serviceEnabled && repo.scheduleEnabled) {
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                Date(CheckinScheduler.nextTriggerMillis(repo.scheduleHour, repo.scheduleMinute))
            )
            "下次自动签到: $t"
        } else {
            "自动签到未开启"
        }
        binding.summaryText.text =
            "启用站点: $enabledSites/${sites.size}\n启用账号: $enabledAccounts/${accounts.size}\n$nextText"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
