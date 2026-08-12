package com.autocheckin.daily.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.autocheckin.daily.data.Repository
import com.autocheckin.daily.databinding.FragmentLogsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: Repository
    private lateinit var adapter: LogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = Repository(requireContext())

        adapter = LogAdapter()
        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = adapter

        binding.clearBtn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("清空日志")
                .setMessage("确定清空所有签到记录吗？")
                .setPositiveButton("清空") { _, _ ->
                    repo.clearLogs()
                    refresh()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.submit(repo.getLogs())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
