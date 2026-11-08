// ScrobbleSite.kt

package com.rambox.plus.scrobble

import androidx.datastore.preferences.core.Preferences
import com.rambox.plus.BuildConfig
import com.rambox.plus.constants.EnableLastFMScrobblingKey
import com.rambox.plus.constants.EnableLibreFmScrobblingKey
import com.rambox.plus.constants.LastFMSessionKey
import com.rambox.plus.constants.LastFMUsernameKey
import com.rambox.plus.constants.LibreFmSessionKey
import com.rambox.plus.constants.LibreFmUsernameKey
import com.music.lastfm.Audioscrobbler

/**
 * The services RamBox scrobbles to. Both speak Last.fm's API, so one client serves both. Their
 * API keys come from local.properties (or CI secrets) at build time and never sit in the source.
 */
enum class ScrobbleSite(
    val label: String,
    val sessionKey: Preferences.Key<String>,
    val usernameKey: Preferences.Key<String>,
    /** Scrobbling on or paused, with the account kept either way. */
    val enabledKey: Preferences.Key<Boolean>,
    private val profilePage: String,
    /** where someone without an account makes one */
    val signUpUrl: String,
    val api: Audioscrobbler,
) {
    LASTFM(
        label = "Last.fm",
        sessionKey = LastFMSessionKey,
        usernameKey = LastFMUsernameKey,
        enabledKey = EnableLastFMScrobblingKey,
        profilePage = "https://www.last.fm/user/",
        signUpUrl = "https://www.last.fm/join",
        api = Audioscrobbler(
            apiRoot = "https://ws.audioscrobbler.com/2.0/",
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET,
        ),
    ),
    LIBREFM(
        label = "Libre.fm",
        sessionKey = LibreFmSessionKey,
        usernameKey = LibreFmUsernameKey,
        enabledKey = EnableLibreFmScrobblingKey,
        profilePage = "https://libre.fm/user/",
        signUpUrl = "https://libre.fm/register.php",
        api = Audioscrobbler(
            apiRoot = "https://libre.fm/2.0/",
            apiKey = BuildConfig.LIBREFM_API_KEY,
            secret = BuildConfig.LIBREFM_SECRET,
            signsInWithToken = true,
        ),
    );

    fun profileUrl(username: String) = profilePage + username
}
