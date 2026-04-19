package com.jpd.finsync.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.jpd.finsync.R
import com.jpd.finsync.databinding.FragmentNavBarBinding

class NavBarFragment : Fragment() {

    enum class Tab { HOME, DOWNLOADS, NONE }

    private var _binding: FragmentNavBarBinding? = null
    private val binding get() = _binding!!

    private val activeTab: Tab
        get() = Tab.entries[requireArguments().getInt(ARG_TAB)]

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNavBarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTabState(activeTab)
        binding.tabHome.setOnClickListener { onTabSelected(Tab.HOME) }
        binding.tabDownloads.setOnClickListener { onTabSelected(Tab.DOWNLOADS) }
    }

    private fun setTabState(tab: Tab) {

        var pill: FrameLayout? = null
        var icon: ImageView? = null
        var label: TextView? = null

        val currentActiveTab = tab == activeTab
        val color = ContextCompat.getColor(requireContext(), if (currentActiveTab) R.color.text_primary else R.color.muted)
        val pillBackground = if (currentActiveTab) R.drawable.bg_nav_active else 0

        when (tab) {
            Tab.HOME -> {
                pill = binding.tabHomePill
                icon = binding.tabHomeIcon
                label = binding.tabHomeLabel
            }
            Tab.DOWNLOADS -> {
                pill = binding.tabDownloadsPill
                icon = binding.tabDownloadsIcon
                label = binding.tabDownloadsLabel
            }
            Tab.NONE -> Unit
        }

        if (pill == null || icon == null || label == null) return

        pill.setBackgroundResource(pillBackground)
        icon.imageTintList = android.content.res.ColorStateList.valueOf(color)
        label.setTextColor(color)
    }

    private fun onTabSelected(tab: Tab) {
        if (tab == activeTab) return
        val activity = requireActivity()
        when (tab) {
            Tab.HOME -> {
                activity.startActivity(
                    Intent(requireContext(), MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                activity.overridePendingTransition(0, 0)
            }
            Tab.DOWNLOADS -> {
                activity.startActivity(Intent(requireContext(), LibraryActivity::class.java))
                activity.overridePendingTransition(0, 0)
            }
            Tab.NONE -> Unit
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TAB = "activeTab"

        fun newInstance(activeTab: Tab) = NavBarFragment().apply {
            arguments = bundleOf(ARG_TAB to activeTab.ordinal)
        }
    }
}
