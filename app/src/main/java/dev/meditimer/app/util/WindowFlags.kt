package dev.meditimer.app.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.*

@Composable
fun KeepScreenOn(activity: Activity, enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
