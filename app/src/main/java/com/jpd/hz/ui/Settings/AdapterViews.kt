package com.jpd.hz.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.jpd.hz.R
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platform

/**
 * An adapter's summary line (spec "Settings → Adapters"): [before], then "Not signed in", or its
 * sync label in the label's colour and the Sync row's detail: "kurage · Synced · 1,400 of 1,400
 * songs", or "Jellyfin · Not signed in".
 */
fun Context.adapterSummary(status: AdapterStatus, before: List<String>): CharSequence {
    val display = (status as? AdapterStatus.SignedIn)?.display
        ?: return joinWithDots(before + getString(R.string.adapter_not_signed_in))
    val summary = SyncRowSummary.from(display)
    val label = getString(summary.status.labelRes)
    val text = SpannableString(joinWithDots(before + label + resources.syncRowDetail(summary)))
    val start = text.indexOf(label, joinWithDots(before).length)
    text.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(this, summary.status.labelColorRes)),
        start,
        start + label.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return text
}

/**
 * Shows [platform]'s summary through [show], live while this fragment's view lives: its
 * connection's sync status after [before], or "Not signed in". The connection is read once per
 * view; coming back to the screen makes a new one.
 */
fun Fragment.showAdapterSummary(
    platform: Platform,
    before: (Connection?) -> List<String>,
    show: (CharSequence) -> Unit
) {
    val connection = platform.connections().firstOrNull()
    if (connection == null) {
        show(requireContext().adapterSummary(AdapterStatus.SignedOut, before(null)))
        return
    }
    val scope = viewLifecycleOwner.lifecycleScope
    val monitor = ConnectionMonitor(requireActivity().application, connection, scope)
    monitor.state.asLiveData().observe(viewLifecycleOwner) { state ->
        val status = adapterStatusOf(connection, state)
        show(requireContext().adapterSummary(status, before(connection)))
    }
}
