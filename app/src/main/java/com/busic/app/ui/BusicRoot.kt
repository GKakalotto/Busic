package com.busic.app.ui

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.busic.app.data.FavFolder
import com.busic.app.ui.library.AboutScreen
import com.busic.app.ui.library.AccountViewModel
import com.busic.app.ui.library.FavScreen
import com.busic.app.ui.library.SettingsScreen
import com.busic.app.ui.library.LoginScreen
import com.busic.app.ui.library.MeScreen
import com.busic.app.ui.player.PlayerScreen
import com.busic.app.ui.player.rememberPlayer
import com.busic.app.ui.search.SearchScreen
import com.busic.app.ui.search.SearchViewModel
import com.busic.app.ui.theme.BgBase

private val SlideSpec = tween<IntOffset>(280)

@Composable
fun BusicRoot() {
    val player = rememberPlayer()
    val playerState by player.state.collectAsState()
    val app = LocalContext.current.applicationContext as Application
    val searchVm: SearchViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app),
    )
    val account: AccountViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app),
    )
    val ready by account.ready.collectAsState()
    val user by account.user.collectAsState()
    val folders by account.folders.collectAsState()
    val accountLoading by account.loading.collectAsState()
    val accountError by account.error.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    val settingsSheet = remember { MutableTransitionState(false) }
    settingsSheet.targetState = settingsOpen
    var aboutOpen by remember { mutableStateOf(false) }
    val aboutSheet = remember { MutableTransitionState(false) }
    aboutSheet.targetState = aboutOpen
    var searchOpen by remember { mutableStateOf(false) }
    val searchSheet = remember { MutableTransitionState(false) }
    searchSheet.targetState = searchOpen
    var loginOpen by remember { mutableStateOf(false) }
    val loginSheet = remember { MutableTransitionState(false) }
    loginSheet.targetState = loginOpen
    var folder by remember { mutableStateOf<FavFolder?>(null) }
    var shownFolder by remember { mutableStateOf<FavFolder?>(null) }
    if (folder != null) shownFolder = folder
    val folderSheet = remember { MutableTransitionState(false) }
    folderSheet.targetState = folder != null
    var playerOpen by remember { mutableStateOf(false) }
    val playerSheet = remember { MutableTransitionState(false) }
    playerSheet.targetState = playerOpen
    val activity = LocalContext.current as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) player.setUiActive(true)
            if (event == Lifecycle.Event.ON_STOP) player.setUiActive(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var lastBack by remember { mutableLongStateOf(0L) }
    LaunchedEffect(searchSheet.currentState, searchSheet.targetState) {
        if (!searchSheet.currentState && !searchSheet.targetState) searchVm.clear()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BgBase,
            bottomBar = {
                PlayDock(
                    playing = playerState.playing && !playerOpen,
                    onOpenPlayer = { playerOpen = true },
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .clipToBounds()
                    .background(BgBase),
            ) {
                MeScreen(
                    ready = ready,
                    user = user,
                    folders = folders,
                    loading = accountLoading,
                    error = accountError,
                    onSearch = { searchOpen = true },
                    onSettings = { settingsOpen = true },
                    onLogin = { loginOpen = true },
                    onLogout = account::logout,
                    onFolder = { folder = it },
                )
                AnimatedVisibility(
                    visibleState = loginSheet,
                    modifier = Modifier.fillMaxSize(),
                    enter = slideInVertically(SlideSpec) { it },
                    exit = slideOutVertically(SlideSpec) { it },
                ) {
                    Box(Modifier.fillMaxSize().quietClick {}) {
                        LoginScreen(onSuccess = {
                            loginOpen = false
                            account.refresh()
                        })
                    }
                }
                AnimatedVisibility(
                    visibleState = folderSheet,
                    modifier = Modifier.fillMaxSize(),
                    enter = slideInVertically(SlideSpec) { it },
                    exit = slideOutVertically(SlideSpec) { it },
                ) {
                    shownFolder?.let { open ->
                        Box(Modifier.fillMaxSize().quietClick {}) {
                            FavScreen(open)
                        }
                    }
                }
                AnimatedVisibility(
                    visibleState = settingsSheet,
                    modifier = Modifier.fillMaxSize(),
                    enter = slideInVertically(SlideSpec) { it },
                    exit = slideOutVertically(SlideSpec) { it },
                ) {
                    Box(Modifier.fillMaxSize().quietClick {}) {
                        SettingsScreen(onAbout = { aboutOpen = true })
                    }
                }
                AnimatedVisibility(
                    visibleState = aboutSheet,
                    modifier = Modifier.fillMaxSize(),
                    enter = slideInVertically(SlideSpec) { it },
                    exit = slideOutVertically(SlideSpec) { it },
                ) {
                    Box(Modifier.fillMaxSize().quietClick {}) {
                        AboutScreen()
                    }
                }
                AnimatedVisibility(
                    visibleState = searchSheet,
                    modifier = Modifier.fillMaxSize(),
                    enter = slideInVertically(SlideSpec) { it },
                    exit = slideOutVertically(SlideSpec) { it },
                ) {
                    Box(Modifier.fillMaxSize().quietClick {}) {
                        SearchScreen(searchVm)
                    }
                }
            }
        }
        AnimatedVisibility(
            visibleState = playerSheet,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(SlideSpec) { it },
            exit = slideOutVertically(SlideSpec) { it },
        ) {
            PlayerScreen()
        }
        BackHandler {
            when {
                playerSheet.currentState || playerSheet.targetState -> playerOpen = false
                loginSheet.currentState || loginSheet.targetState -> loginOpen = false
                folderSheet.currentState || folderSheet.targetState -> folder = null
                aboutSheet.currentState || aboutSheet.targetState -> aboutOpen = false
                settingsSheet.currentState || settingsSheet.targetState -> {
                    aboutOpen = false
                    settingsOpen = false
                }
                searchSheet.currentState || searchSheet.targetState -> searchOpen = false
                else -> {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastBack < 2000L) {
                        activity?.moveTaskToBack(true)
                    } else {
                        lastBack = now
                        Toast.makeText(activity, "再按一次返回桌面", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
