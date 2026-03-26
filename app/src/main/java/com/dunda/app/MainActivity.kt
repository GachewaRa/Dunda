package com.dunda.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dunda.app.ui.components.MiniPlayer
import com.dunda.app.ui.navigation.DundaNavGraph
import com.dunda.app.ui.navigation.Routes
import com.dunda.app.ui.theme.DundaTheme
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted — songs will be loaded by the ViewModel
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestAudioPermission()

        setContent {
            DundaTheme {
                val navController = rememberNavController()
                val musicViewModel: MusicViewModel = viewModel()
                val playerViewModel: PlayerViewModel = viewModel()

                val currentSong by playerViewModel.currentSong.collectAsState()
                val isPlaying by playerViewModel.isPlaying.collectAsState()
                val position by playerViewModel.currentPosition.collectAsState()
                val duration by playerViewModel.duration.collectAsState()

                // Periodically update playback position
                LaunchedEffect(isPlaying) {
                    while (isPlaying) {
                        playerViewModel.tickPosition()
                        delay(250)
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        // Only show bottom nav on main screens
                        if (currentRoute == Routes.HOME || currentRoute == Routes.PLAYLISTS) {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Songs") },
                                    selected = currentRoute == Routes.HOME,
                                    onClick = {
                                        if (currentRoute != Routes.HOME) {
                                            navController.navigate(Routes.HOME) {
                                                popUpTo(Routes.HOME) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "Playlists") },
                                    label = { Text("Playlists") },
                                    selected = currentRoute == Routes.PLAYLISTS,
                                    onClick = {
                                        if (currentRoute != Routes.PLAYLISTS) {
                                            navController.navigate(Routes.PLAYLISTS) {
                                                popUpTo(Routes.HOME)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            DundaNavGraph(
                                navController = navController,
                                musicViewModel = musicViewModel,
                                playerViewModel = playerViewModel
                            )
                        }

                        // Floating Mini Player
                        MiniPlayer(
                            song = currentSong,
                            isPlaying = isPlaying,
                            currentPosition = position,
                            duration = duration,
                            onPlayPause = { playerViewModel.playPause() },
                            onSkipNext = { playerViewModel.skipNext() },
                            onSkipPrevious = { playerViewModel.skipPrevious() },
                            onClick = { /* TODO: expand to full player screen */ },
                            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                        )
                    }
                }
            }
        }
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }
}
