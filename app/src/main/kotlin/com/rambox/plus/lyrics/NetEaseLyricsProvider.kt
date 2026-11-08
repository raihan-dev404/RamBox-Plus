// NetEaseLyricsProvider.kt

package com.rambox.plus.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import com.rambox.plus.constants.EnableNetEaseKey
import com.rambox.plus.utils.dataStore
import com.rambox.plus.utils.get

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableNetEaseKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
