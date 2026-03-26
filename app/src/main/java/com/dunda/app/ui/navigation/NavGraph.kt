package com.dunda.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dunda.app.ui.screens.HomeScreen
import com.dunda.app.ui.screens.PlaylistDetailScreen
import com.dunda.app.ui.screens.PlaylistScreen
import com.dunda.app.ui.screens.SettingsScreen
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel

object Routes {
    const val HOME = "home"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val SETTINGS = "settings"

    fun playlistDetail(playlistId: Long) = "playlist/$playlistId"
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
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
