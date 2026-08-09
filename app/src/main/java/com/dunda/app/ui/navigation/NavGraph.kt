package com.dunda.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dunda.app.ui.screens.ArtistDetailScreen
import com.dunda.app.ui.screens.ArtistsScreen
import com.dunda.app.ui.screens.HomeScreen
import com.dunda.app.ui.screens.NowPlayingScreen
import com.dunda.app.ui.screens.PlaylistDetailScreen
import com.dunda.app.ui.screens.PlaylistScreen
import com.dunda.app.ui.screens.SettingsScreen
import com.dunda.app.ui.screens.StatsScreen
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel

object Routes {
    const val HOME = "home"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val NOW_PLAYING = "now_playing"
    const val ARTISTS = "artists"
    const val ARTIST_DETAIL = "artist/{artistName}"

    fun playlistDetail(playlistId: Long) = "playlist/$playlistId"

    /** Artist names may contain any character — always travel encoded. */
    fun artistDetail(artistName: String) = "artist/${Uri.encode(artistName)}"
}

@Composable
fun DundaNavGraph(
    navController: NavHostController,
    musicViewModel: MusicViewModel,
    playerViewModel: PlayerViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                musicViewModel = musicViewModel,
                playerViewModel = playerViewModel,
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onStatsClick = { navController.navigate(Routes.STATS) },
                onOpenNowPlaying = { navController.navigate(Routes.NOW_PLAYING) }
            )
        }

        composable(Routes.PLAYLISTS) {
            PlaylistScreen(
                musicViewModel = musicViewModel,
                onPlaylistClick = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                }
            )
        }

        composable(
            route = Routes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                musicViewModel = musicViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenNowPlaying = { navController.navigate(Routes.NOW_PLAYING) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STATS) {
            StatsScreen(
                musicViewModel = musicViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                musicViewModel = musicViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onArtistClick = { artist ->
                    navController.navigate(Routes.artistDetail(artist))
                }
            )
        }

        composable(Routes.ARTISTS) {
            ArtistsScreen(
                musicViewModel = musicViewModel,
                onArtistClick = { artist ->
                    navController.navigate(Routes.artistDetail(artist))
                }
            )
        }

        composable(
            route = Routes.ARTIST_DETAIL,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName")
                ?: return@composable
            ArtistDetailScreen(
                artistName = artistName,
                musicViewModel = musicViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenNowPlaying = { navController.navigate(Routes.NOW_PLAYING) }
            )
        }
    }
}
