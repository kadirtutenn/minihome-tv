package com.minihome.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource

/**
 * Owns the single ExoPlayer instance. Lives between Activity.onStart and onStop; release() is final.
 *
 * Recovery ladder per channel (bounded, no loops):
 *   primary -> primary retry -> primary on a fresh player -> backup 1 -> backup 2 ... -> UNAVAILABLE
 * The ladder resets only after STABLE_MS of uninterrupted playback.
 */
class PlayerController(
    private val context: Context,
    private val surface: SurfaceView,
    private val onStatus: (Status) -> Unit,
) {
    enum class Status { LOADING, PLAYING, RECOVERING, UNAVAILABLE }

    private class Step(val url: String, val freshPlayer: Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var channel: Channel? = null
    private var steps: List<Step> = emptyList()
    private var step = 0
    private var recreateNext = false
    private var lastPos = -1L
    private var lastProgressAt = 0L
    private var playingSince = 0L

    var status = Status.LOADING
        private set

    private val startStep = Runnable { startStep() }

    private val watchdog = object : Runnable {
        override fun run() {
            val p = player ?: return
            val now = SystemClock.elapsedRealtime()
            val pos = p.currentPosition
            val wantsToPlay = p.playWhenReady && p.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
            if (!wantsToPlay || pos != lastPos) {
                lastPos = pos
                lastProgressAt = now
            } else if (now - lastProgressAt > if (playingSince == 0L) FIRST_FRAME_MS else STALL_MS) {
                fail("no progress for ${now - lastProgressAt} ms (state=${p.playbackState})")
                return
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            surface.keepScreenOn = isPlaying
            if (isPlaying) {
                playingSince = SystemClock.elapsedRealtime()
                setStatus(Status.PLAYING)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> if (status == Status.PLAYING) setStatus(Status.LOADING)
                Player.STATE_ENDED -> fail("live stream ended")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            // Decoder trouble usually means a wedged codec: the next attempt must use a fresh player.
            if (error.errorCode in 4000..4999) recreateNext = true
            fail("${error.errorCodeName}: ${error.message}")
        }

        override fun onVideoSizeChanged(size: VideoSize) = fitSurface(size)
    }

    fun play(c: Channel) {
        channel = c
        steps = buildList {
            add(Step(c.primaryUrl, false))
            add(Step(c.primaryUrl, false))
            add(Step(c.primaryUrl, true))
            c.backupUrls.forEach { add(Step(it, false)) }
        }
        step = 0
        handler.removeCallbacks(startStep)
        startStep()
    }

    fun retry() {
        channel?.let(::play)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        channel = null
    }

    private fun startStep() {
        val s = steps.getOrNull(step) ?: return
        if (player == null || s.freshPlayer || recreateNext) {
            releasePlayer()
            player = buildPlayer()
            recreateNext = false
        }
        val p = player!!
        p.setMediaItem(MediaItem.fromUri(s.url))
        p.prepare()
        p.playWhenReady = true
        playingSince = 0L
        lastPos = -1L
        lastProgressAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, CHECK_MS)
        setStatus(if (step == 0) Status.LOADING else Status.RECOVERING)
    }

    private fun fail(reason: String) {
        handler.removeCallbacks(watchdog)
        val stable = playingSince > 0 && SystemClock.elapsedRealtime() - playingSince > STABLE_MS
        step = if (stable) 0 else step + 1
        Log.w(TAG, "${channel?.name}: $reason -> step $step/${steps.size}")
        if (step >= steps.size) {
            player?.stop()
            surface.keepScreenOn = false
            setStatus(Status.UNAVAILABLE)
            return
        }
        setStatus(Status.RECOVERING)
        handler.removeCallbacks(startStep)
        handler.postDelayed(startStep, RETRY_DELAY_MS)
    }

    private fun buildPlayer(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(NET_TIMEOUT_MS)
            .setReadTimeoutMs(NET_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
        // Small buffers: low RAM and fast zapping. Live TV gains nothing from minutes of buffer.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 30_000, 1_000, 2_000)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(HlsMediaSource.Factory(http))
            .setLoadControl(load)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
            .also {
                it.setVideoSurfaceView(surface)
                it.addListener(listener)
            }
    }

    private fun releasePlayer() {
        player?.let {
            it.removeListener(listener)
            it.release() // also detaches the surface and frees decoders
        }
        player = null
        surface.keepScreenOn = false
    }

    private fun setStatus(s: Status) {
        if (status == s) return
        status = s
        Log.i(TAG, "${channel?.name}: $s")
        onStatus(s)
    }

    private fun fitSurface(size: VideoSize) {
        val parent = surface.parent as? View ?: return
        if (size.width == 0 || size.height == 0 || parent.width == 0 || parent.height == 0) return
        val ratio = size.width * size.pixelWidthHeightRatio / size.height
        val pw = parent.width
        val ph = parent.height
        val lp = surface.layoutParams as FrameLayout.LayoutParams
        if (pw.toFloat() / ph > ratio) {
            lp.width = (ph * ratio).toInt(); lp.height = ph
        } else {
            lp.width = pw; lp.height = (pw / ratio).toInt()
        }
        lp.gravity = Gravity.CENTER
        surface.layoutParams = lp
    }

    companion object {
        private const val TAG = "PlayerController"
        private const val CHECK_MS = 2_000L
        private const val STALL_MS = 10_000L
        private const val FIRST_FRAME_MS = 12_000L // slow 10 s-segment streams (NOW) need ~9 s on low-end TVs
        private const val STABLE_MS = 30_000L
        private const val RETRY_DELAY_MS = 500L
        private const val NET_TIMEOUT_MS = 8_000
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android TV) MiniHomeTV/1.0"
    }
}
