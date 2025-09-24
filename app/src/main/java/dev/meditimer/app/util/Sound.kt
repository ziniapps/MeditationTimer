package dev.meditimer.app.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import dev.meditimer.app.R

object Sound {
    private var player: MediaPlayer? = null

    fun playBuiltin(ctx: Context, gong: Int) {
        stop()
        val resId = when (gong) {
            1 -> R.raw.gong1
            2 -> R.raw.gong2
            else -> R.raw.gong3
        }
        player = MediaPlayer.create(ctx, resId)
        player?.start()
    }

    fun playUri(ctx: Context, uriStr: String) {
        stop()
        val uri = Uri.parse(uriStr)
        player = MediaPlayer.create(ctx, uri)
        player?.start()
    }

    fun stop() {
        player?.stop()
        player?.release()
        player = null
    }
}
