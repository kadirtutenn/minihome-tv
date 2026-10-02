package com.minihome.tv

import android.content.Context
import android.util.Log
import org.json.JSONArray

data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val primaryUrl: String,
    val backupUrls: List<String> = emptyList(),
)

/** Bundled channel list + the only persisted state (last channel, favorites). Never throws. */
class ChannelRepository(context: Context) {
    private val prefs = context.getSharedPreferences("state", Context.MODE_PRIVATE)
    val channels: List<Channel> = loadChannels(context)

    fun lastIndex(): Int = safe(0) {
        val id = prefs.getString(KEY_LAST, null)
        channels.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }

    fun saveLast(c: Channel) = safe(Unit) { prefs.edit().putString(KEY_LAST, c.id).apply() }

    fun favorites(): Set<String> = safe(emptySet()) { prefs.getStringSet(KEY_FAV, null)?.toSet() ?: emptySet() }

    fun toggleFavorite(c: Channel) = safe(Unit) {
        val fav = favorites().toMutableSet()
        if (!fav.add(c.id)) fav.remove(c.id)
        prefs.edit().putStringSet(KEY_FAV, fav).apply()
    }

    // Wrong types / garbage in prefs: wipe and continue with defaults. Startup must never fail on state.
    private inline fun <T> safe(default: T, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "invalid persisted state, resetting", e)
        runCatching { prefs.edit().clear().apply() }
        default
    }

    companion object {
        private const val TAG = "ChannelRepository"
        private const val KEY_LAST = "last_channel"
        private const val KEY_FAV = "favorites"

        fun parse(json: String): List<Channel> {
            val arr = JSONArray(json)
            val seenUrls = HashSet<String>()
            val seenNumbers = HashSet<Int>()
            val out = ArrayList<Channel>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url")
                val number = o.optInt("number", -1)
                if (!url.startsWith("http") || number < 0 || !seenUrls.add(url) || !seenNumbers.add(number)) continue
                val backups = o.optJSONArray("backup")
                val backupUrls = (0 until (backups?.length() ?: 0))
                    .map { backups!!.optString(it) }
                    .filter { it.startsWith("http") && it != url }
                    .distinct()
                out += Channel(
                    id = o.optString("id").ifEmpty { "ch$number" },
                    number = number,
                    name = o.optString("name").ifEmpty { "Kanal $number" },
                    primaryUrl = url,
                    backupUrls = backupUrls,
                )
            }
            return out.sortedBy { it.number }
        }

        private fun loadChannels(context: Context): List<Channel> = try {
            parse(context.assets.open("channels.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.e(TAG, "channels.json unreadable", e)
            emptyList()
        }
    }
}
