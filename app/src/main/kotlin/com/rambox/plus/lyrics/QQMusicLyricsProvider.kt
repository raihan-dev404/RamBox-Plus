// QQMusicLyricsProvider.kt

package com.rambox.plus.lyrics

import android.content.Context
import com.music.directlyrics.QQMusic
import com.rambox.plus.constants.EnableQQMusicKey
import com.rambox.plus.utils.dataStore
import com.rambox.plus.utils.get

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableQQMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
