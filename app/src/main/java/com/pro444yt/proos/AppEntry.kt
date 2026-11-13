package com.pro444yt.proos

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class AppEntry(
    val label: String,
    val pkg: String,
    val cls: String,
    val icon: Drawable
) {
    val component: ComponentName get() = ComponentName(pkg, cls)
    val key: String get() = "$pkg/$cls"
}
