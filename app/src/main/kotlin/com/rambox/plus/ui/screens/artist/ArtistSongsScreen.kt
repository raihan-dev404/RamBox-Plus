// ArtistSongsScreen.kt

package com.rambox.plus.ui.screens.artist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.rambox.plus.LocalPlayerAwareWindowInsets
import com.rambox.plus.LocalPlayerConnection
import com.rambox.plus.R
import com.rambox.plus.constants.ArtistSongSortDescendingKey
import com.rambox.plus.constants.ArtistSongSortType
import com.rambox.plus.constants.ArtistSongSortTypeKey
import com.rambox.plus.extensions.toMediaItem
import com.rambox.plus.playback.queues.ListQueue
import com.rambox.plus.ui.component.LocalMenuState
import com.rambox.plus.ui.component.SongSwipeBox
import com.rambox.plus.ui.component.detail.CreatorUi
import com.rambox.plus.ui.component.detail.PlaylistDetailScaffold
import com.rambox.plus.ui.component.detail.PlaylistEndOfLine
import com.rambox.plus.ui.component.detail.PlaylistHeader
import com.rambox.plus.ui.component.detail.PlaylistSortControl
import com.rambox.plus.ui.component.detail.PlaylistTrackRow
import com.rambox.plus.ui.component.librarySwipeAction
import com.rambox.plus.ui.component.queueSwipeAction
import com.rambox.plus.ui.component.rememberRevealSeenState
import com.rambox.plus.ui.component.revealOnAppear
import com.rambox.plus.ui.menu.SongMenu
import com.rambox.plus.ui.utils.backToMain
import com.rambox.plus.utils.makeTimeString
import com.rambox.plus.utils.rememberEnumPreference
import com.rambox.plus.utils.rememberPreference
import com.rambox.plus.viewmodels.ArtistSongsViewModel

/** the artist's songs in your library, as a playlist on the artist's banner */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistSongsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSongSortTypeKey, ArtistSongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSongSortDescendingKey, true)

    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()

    val artistId = artist?.artist?.id
    val (cachedName, bannerUrl) = rememberArtistBanner(artistId)
    val name = artist?.artist?.name ?: cachedName.orEmpty()
    val title = if (name.isNotBlank()) "$name in your library" else stringResource(R.string.songs)
    val totalSeconds = remember(songs) { songs.sumOf { it.song.duration } }
    val isThisQueue = queueTitle == title

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val list = if (shuffled) songs.shuffled() else songs
        playerConnection.playQueue(ListQueue(title = title, items = list.map { it.toMediaItem() }, startIndex = startIndex))
    }

    PlaylistDetailScaffold(
        sharedElementKey = null,
        coverUrl = bannerUrl ?: songs.firstOrNull()?.song?.thumbnailUrl,
        lazyListState = lazyListState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
    ) {
        item(key = "header", contentType = "header") {
            PlaylistHeader(
                title = title,
                creators = listOfNotNull(
                    name.takeIf { it.isNotBlank() }?.let { CreatorUi(name = it, avatarUrl = artist?.artist?.thumbnailUrl, artistId = artistId) }
                ),
                stats = com.rambox.plus.ui.screens.playlist.generatedStats(songs.size, totalSeconds),
                isPlaying = isThisQueue && isPlaying,
                onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else play() },
                onShuffleClick = { play(shuffled = true) },
                onCreatorClick = { creator -> creator.artistId?.let { navController.navigate("artist/$it") } },
                sortControl = {
                    PlaylistSortControl(
                        current = sortType,
                        descending = sortDescending,
                        options = ArtistSongSortType.entries,
                        label = { type ->
                            stringResource(
                                when (type) {
                                    ArtistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                    ArtistSongSortType.NAME -> R.string.sort_by_name
                                    ArtistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                }
                            )
                        },
                        onSelect = onSortTypeChange,
                        onToggleDirection = { onSortDescendingChange(!sortDescending) },
                    )
                },
            )
        }

        itemsIndexed(songs, key = { _, song -> song.id }, contentType = { _, _ -> "track" }) { index, song ->
            SongSwipeBox(
                modifier = Modifier.animateItem(),
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                PlaylistTrackRow(
                    thumbnailUrl = song.song.thumbnailUrl,
                    songId = song.id,
                    title = song.song.title,
                    subtitle = listOfNotNull(song.album?.title, makeTimeString(song.song.duration * 1000L)).joinToString(" • "),
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.revealOnAppear(key = "song_${song.id}", seenState = revealSeen, delayMillis = minOf(index, 8) * 28),
                    onClick = {
                        if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause() else play(startIndex = index)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                        }
                    },
                    onMenuClick = {
                        menuState.show {
                            SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                        }
                    },
                )
            }
        }

        if (songs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds)
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}
