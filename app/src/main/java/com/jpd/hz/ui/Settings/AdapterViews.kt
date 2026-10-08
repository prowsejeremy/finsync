package com.jpd.hz.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.jpd.hz.R

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
