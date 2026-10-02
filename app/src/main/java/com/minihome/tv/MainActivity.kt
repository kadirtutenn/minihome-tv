package com.minihome.tv

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.minihome.tv.PlayerController.Status

class MainActivity : Activity() {
    private lateinit var repo: ChannelRepository
    private lateinit var surface: SurfaceView
    private lateinit var osd: TextView
    private lateinit var statusView: TextView
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>

    private val handler = Handler(Looper.getMainLooper())
    private var player: PlayerController? = null
    private var index = 0
    private var digits = ""
    private var cursor = 0
    private var okArmed = false // OK press started while the list was open
    private var okLongFired = false
    private var online = true
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    private val channels get() = repo.channels
    private val hideOsd = Runnable { osd.visibility = View.GONE }
    private val hideListTask = Runnable { hideList() }
    private val commitDigits = Runnable { commitDigits() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surface = findViewById(R.id.surface)
        osd = findViewById(R.id.osd)
        statusView = findViewById(R.id.status)
        list = findViewById(R.id.list)

        repo = ChannelRepository(this)
        index = repo.lastIndex()

        adapter = ArrayAdapter(this, R.layout.item_channel, ArrayList())
        list.adapter = adapter
        list.isFocusable = false // AbsListView re-enables focus in its constructor; XML alone is not enough
        // Pointer (air-mouse) clicks still work; remote keys are handled in dispatchKeyEvent.
        list.setOnItemClickListener { _, _, pos, _ ->
            hideList()
            tune(pos)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        }
    }

    override fun onStart() {
        super.onStart()
        if (channels.isEmpty()) {
            showStatus(getString(R.string.no_channels))
            return
        }
        player = PlayerController(applicationContext, surface, ::onStatus)
        registerNetwork()
        tune(index)
    }

    override fun onStop() {
        // Covers Home, sleep, another app on top: free decoder + network, nothing survives in background.
        unregisterNetwork()
        player?.release()
        player = null
        handler.removeCallbacksAndMessages(null)
        digits = ""
        super.onStop()
    }

    // ---- channel switching ----

    private fun tune(i: Int) {
        val p = player ?: return
        index = Math.floorMod(i, channels.size)
        val c = channels[index]
        repo.saveLast(c)
        p.play(c)
        showOsd()
    }

    private fun showOsd(text: String? = null) {
        val c = channels.getOrNull(index) ?: return
        osd.text = text ?: "${c.number}  ${c.name}\n${statusLabel(player?.status)}"
        osd.visibility = View.VISIBLE
        handler.removeCallbacks(hideOsd)
        handler.postDelayed(hideOsd, OSD_MS)
    }

    private fun onStatus(s: Status) {
        if (osd.visibility == View.VISIBLE && digits.isEmpty()) showOsd()
        when (s) {
            Status.PLAYING -> statusView.visibility = View.GONE
            Status.UNAVAILABLE -> showStatus(getString(if (online) R.string.unavailable else R.string.offline))
            else -> showStatus(statusLabel(s))
        }
    }

    private fun statusLabel(s: Status?) = getString(
        when (s) {
            Status.PLAYING -> R.string.playing
            Status.RECOVERING -> R.string.recovering
            Status.UNAVAILABLE -> R.string.unavailable_short
            else -> R.string.loading
        }
    )

    private fun showStatus(text: String) {
        statusView.text = text
        statusView.visibility = View.VISIBLE
    }

    private fun commitDigits() {
        val n = digits.toIntOrNull()
        digits = ""
        val i = channels.indexOfFirst { it.number == n }
        if (i >= 0) tune(i) else showOsd(getString(R.string.no_such_channel, n ?: 0))
    }

    // ---- channel list overlay ----

    private fun showList(selected: Int = index) {
        refreshList()
        list.visibility = View.VISIBLE
        moveCursor(selected)
        touchList()
    }

    // The list never takes focus: air-mouse remotes keep the window in touch mode, where a focused
    // ListView swallows every DPAD press just to leave touch mode. We drive the cursor ourselves.
    private fun moveCursor(to: Int) {
        cursor = Math.floorMod(to, channels.size)
        list.setItemChecked(cursor, true)
        val rowHeight = list.getChildAt(0)?.height ?: 0
        list.setSelectionFromTop(cursor, (list.height - rowHeight) / 2)
    }

    private fun hideList() {
        okArmed = false
        handler.removeCallbacks(hideListTask)
        list.visibility = View.GONE
    }

    private fun touchList() {
        handler.removeCallbacks(hideListTask)
        handler.postDelayed(hideListTask, LIST_MS)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (list.visibility != View.VISIBLE) return super.dispatchKeyEvent(event)
        touchList()
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> if (down) moveCursor(cursor - 1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> if (down) moveCursor(cursor + 1)
            KeyEvent.KEYCODE_PAGE_UP -> if (down) moveCursor(cursor - PAGE)
            KeyEvent.KEYCODE_PAGE_DOWN -> if (down) moveCursor(cursor + PAGE)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down && event.repeatCount == 0) {
                    okArmed = true
                    okLongFired = false
                }
                if (down && okArmed && !okLongFired &&
                    (event.isLongPress || event.eventTime - event.downTime >= LONG_PRESS_MS)
                ) {
                    okLongFired = true // long OK = toggle favorite
                    repo.toggleFavorite(channels[cursor])
                    refreshList()
                    list.setItemChecked(cursor, true)
                }
                if (!down && okArmed && !okLongFired) {
                    okArmed = false
                    hideList()
                    tune(cursor)
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Unit
            else -> return super.dispatchKeyEvent(event) // BACK, HOME, volume...
        }
        return true
    }

    private fun refreshList() {
        val fav = repo.favorites()
        adapter.clear()
        val playing = channels.getOrNull(index)
        adapter.addAll(channels.map {
            "%s%3d  %s%s".format(if (it == playing) "▶" else " ", it.number, it.name, if (it.id in fav) "  ★" else "")
        })
    }

    // ---- remote keys ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (list.visibility == View.VISIBLE || player == null) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            // Arrows browse the list (cursor follows the visual direction); CH+/CH- zap directly.
            KeyEvent.KEYCODE_DPAD_UP -> showList(index - 1)
            KeyEvent.KEYCODE_DPAD_DOWN -> showList(index + 1)
            KeyEvent.KEYCODE_CHANNEL_UP -> tune(index + 1)
            KeyEvent.KEYCODE_CHANNEL_DOWN -> tune(index - 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MENU -> showList()
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> showOsd()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> digit(keyCode - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> digit(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun digit(d: Int) {
        if (digits.length >= 3) digits = ""
        digits += d
        showOsd("${digits}_")
        handler.removeCallbacks(commitDigits)
        handler.postDelayed(commitDigits, DIGIT_MS)
    }

    @Deprecated("API < 33 path; 33+ uses OnBackInvokedCallback")
    override fun onBackPressed() = handleBack()

    private fun handleBack() {
        if (list.visibility == View.VISIBLE) hideList() else finish()
    }

    // ---- network: recover automatically when connectivity returns ----

    private fun registerNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post {
                    online = true
                    if (player?.status == Status.UNAVAILABLE) player?.retry()
                }
            }

            override fun onLost(network: Network) {
                handler.post { online = cm.activeNetwork != null }
            }
        }
        val req = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching { cm.registerNetworkCallback(req, cb) }.onSuccess { netCallback = cb }
        online = cm.activeNetwork != null
    }

    private fun unregisterNetwork() {
        val cb = netCallback ?: return
        netCallback = null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    companion object {
        private const val OSD_MS = 4_000L
        private const val LIST_MS = 6_000L
        private const val LONG_PRESS_MS = 500L
        private const val PAGE = 8
        private const val DIGIT_MS = 1_500L
    }
}
