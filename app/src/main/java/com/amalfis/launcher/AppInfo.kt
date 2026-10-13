package com.amalfis.launcher

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    var isAllowed: Boolean = false,
    val isSystemApp: Boolean = false
)
