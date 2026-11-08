// DeezerLyricsProvider.kt

package com.rambox.plus.lyrics

import android.content.Context
import com.music.directlyrics.Deezer
import com.rambox.plus.constants.EnableDeezerKey
import com.rambox.plus.utils.dataStore
import com.rambox.plus.utils.get

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableDeezerKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
