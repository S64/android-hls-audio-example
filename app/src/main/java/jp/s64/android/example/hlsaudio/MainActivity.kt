package jp.s64.android.example.hlsaudio

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import java.io.IOException
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StringRes
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.hls.HlsManifest
import com.google.android.material.snackbar.Snackbar
import java.lang.IllegalArgumentException


class MainActivity : AppCompatActivity() {

    companion object {

        private const val TAG = "MainActivity"
        private const val USER_AGENT
            = "MyUserAgent"

    }

    private lateinit var player: ExoPlayer
    private lateinit var dataSourceFactory: DataSource.Factory
    private lateinit var mediaSourceFactory: HlsMediaSource.Factory
    private lateinit var handler: Handler

    private val container by lazy { findViewById<ViewGroup>(R.id.container) }
    private val url by lazy { findViewById<EditText>(R.id.url) }
    private val load by lazy { findViewById<Button>(R.id.load) }
    private val play by lazy { findViewById<Button>(R.id.play) }
    private val logs by lazy { findViewById<TextView>(R.id.logs) }
    private val divider by lazy { findViewById<Button>(R.id.divider) }
    private val pause by lazy { findViewById<Button>(R.id.pause) }
    private val bufferProgress by lazy { findViewById<ProgressBar>(R.id.bufferProgress) }
    private val speed by lazy { findViewById<SeekBar>(R.id.speed) }

    private var tracker: ProgressTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        handler = Handler()

        val band = DefaultBandwidthMeter()
        val adaptiveTrackSelection = AdaptiveTrackSelection.Factory(band)

        dataSourceFactory = DefaultDataSourceFactory(this, USER_AGENT, band)
        mediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
        player = ExoPlayerFactory.newSimpleInstance(
            this,
            DefaultTrackSelector(adaptiveTrackSelection),
            DefaultLoadControl()
        ).apply {
            addListener(playerEventListener)
        }

        resetToBeforePlay()
        setUserInterfaceBufferingState(false)

        load.setOnClickListener {
            val source = mediaSourceFactory.createMediaSource(Uri.parse(url.text.toString()))
                .apply {
                    this.addEventListener(handler, sourceEventListener)
                }
            player.prepare(
                source
            )
        }

        play.setOnClickListener {
            player.playWhenReady = true
        }

        pause.setOnClickListener {
            pause()
        }

        divider.setOnClickListener {
            logs.text = "-------- DIVIDER --------${System.lineSeparator()}${logs.text}"
        }

        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate: Float = if (progress == 0) {
                    0.01F
                } else {
                    (progress * 2) / 100F
                }

                player.playbackParameters = PlaybackParameters(rate)
                log("Speed: ${rate}")
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                // no-op
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                // no-op
            }

        })
    }

    override fun onPause() {
        super.onPause()
        tracker!!.destroy()
        tracker = null
    }

    private fun resetToBeforePlay() {
        player.playWhenReady = false
    }

    private fun pause() {
        resetToBeforePlay()
    }

    private val playerEventListener = object : Player.EventListener {

        private val icon = "🔊"

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
            log("${icon}onTimelineChanged(reason: ${convertReasonFlag(reason)})")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            log("${icon}onIsPlayingChanged(isPlaying: ${isPlaying})")
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            log("${icon}onPlayerStateChanged(playWhenReady: ${playWhenReady}, playbackState: ${convertPlaybackStateFlag(playbackState)})")

            if (!playWhenReady && playbackState == Player.STATE_READY) {
                resetToBeforePlay()
                notifyPlayerReady()
            }

            if (playbackState == Player.STATE_ENDED) {
                resetToBeforePlay()
                notifyPlaybackEndedSuccessfully()
            }

            setUserInterfaceBufferingState(playbackState == Player.STATE_BUFFERING)
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            log("${icon}onLoadingChanged(isLoading: ${isLoading})")
        }

        override fun onPlayerError(error: ExoPlaybackException?) {
            log("${icon}onPlayerError(error: ${error})")
            notifyPlaybackAborted()
        }

    }

    private fun setUserInterfaceBufferingState(nowBuffering: Boolean) {
        if (nowBuffering) {
            snack(R.string.now_buffering)
        }
        bufferProgress.visibility = if (nowBuffering) View.VISIBLE else View.GONE
    }

    private fun convertPlaybackStateFlag(playbackState: Int): String {
        return when (playbackState) {
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_ENDED -> "ENDED"
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_READY -> "READY"
            else -> throw IllegalArgumentException()
        }
    }

    private fun convertReasonFlag(reason: Int): String {
        return when (reason) {
            Player.TIMELINE_CHANGE_REASON_DYNAMIC -> "DYNAMIC"
            Player.TIMELINE_CHANGE_REASON_PREPARED -> "PREPARED"
            Player.TIMELINE_CHANGE_REASON_RESET -> "RESET"
            else -> throw IllegalArgumentException()
        }
    }

    private val sourceEventListener = object : MediaSourceEventListener {

        private val icon = "🥫"

        override fun onLoadStarted(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
            mediaLoadData: MediaSourceEventListener.MediaLoadData?
        ) {
            log("${icon}onLoadStarted")
        }

        override fun onDownstreamFormatChanged(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            mediaLoadData: MediaSourceEventListener.MediaLoadData?
        ) {
            log("${icon}onDownstreamFormatChanged")
        }

        override fun onUpstreamDiscarded(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            mediaLoadData: MediaSourceEventListener.MediaLoadData?
        ) {
            log("${icon}onUpstreamDiscarded")
        }

        override fun onMediaPeriodCreated(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?
        ) {
            log("${icon}onMediaPeriodCreated")
        }

        override fun onLoadCanceled(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
            mediaLoadData: MediaSourceEventListener.MediaLoadData?
        ) {
            log("${icon}onLoadCanceled")
        }

        override fun onMediaPeriodReleased(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?
        ) {
            log("${icon}onMediaPeriodReleased")
            notifyStreamCompleted()
        }

        override fun onReadingStarted(windowIndex: Int, mediaPeriodId: MediaSource.MediaPeriodId?) {
            log("${icon}onReadingStarted")
        }

        override fun onLoadCompleted(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
            mediaLoadData: MediaSourceEventListener.MediaLoadData?
        ) {
            log("${icon}onLoadCompleted")
        }

        override fun onLoadError(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
            mediaLoadData: MediaSourceEventListener.MediaLoadData?,
            error: IOException?,
            wasCanceled: Boolean
        ) {
            log("${icon}onLoadError(error: ${error})")
            if (!wasCanceled && error != null) {
                notifyNetworkError()
            }
        }

    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logs.text = "${System.currentTimeMillis()} ${msg}${System.lineSeparator()}${logs.text}"
    }

    private fun notifyStreamCompleted() {
        snack(R.string.stream_completed)
    }

    private fun notifyNetworkError() {
        snack(R.string.network_warn)
    }

    private fun notifyPlayerReady() {
        snack(R.string.ready)
    }

    private fun notifyPlaybackAborted() {
        snack(R.string.playback_aborted)
    }

    private fun notifyPlaybackEndedSuccessfully() {
        snack(R.string.playback_ended_successfully)
    }

    private fun snack(@StringRes resId: Int) {
        Snackbar.make(container, resId, Snackbar.LENGTH_SHORT)
            .show()
        log("\uD83C\uDF7F${getString(resId)}")
    }

    inner class ProgressTracker(
        private val callback: (currentPosition: Long) -> Unit
    ) : Runnable {

        private val handler = Handler()

        private var destroyed: Boolean = false

        override fun run() {
            callback(player.currentPosition)
            if (!destroyed) {
                handler.postDelayed(this, 100)
            }
        }

        fun destroy() {
            destroyed = true
        }

    }

}
