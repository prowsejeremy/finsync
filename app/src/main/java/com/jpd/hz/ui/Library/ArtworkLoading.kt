package com.jpd.hz.ui

import android.widget.ImageView
import com.bumptech.glide.Glide
import java.io.File

/** Loads album art from a local file, or clears the view so its surface_2 background shows. */
fun loadArtwork(view: ImageView, path: String?) {
    if (path == null) {
        Glide.with(view).clear(view)
        return
    }
    Glide.with(view).load(File(path)).centerCrop().into(view)
}
