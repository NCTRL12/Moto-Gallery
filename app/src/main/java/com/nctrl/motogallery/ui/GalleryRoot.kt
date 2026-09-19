package com.nctrl.motogallery.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.ui.screens.AlbumsScreen
import com.nctrl.motogallery.ui.screens.MediaGridScreen
import com.nctrl.motogallery.ui.screens.PermissionScreen
import com.nctrl.motogallery.ui.screens.ViewerScreen
import com.nctrl.motogallery.util.MediaAccess
import com.nctrl.motogallery.util.Permissions

private object Routes {
    const val PHOTOS = "photos"
    const val ALBUMS = "albums"
    const val FAVORITES = "favorites"
    const val ALBUM_DETAIL = "album/{albumId}"
    const val VIEWER = "viewer/{source}/{index}"

    fun album(albumId: Long) = "album/$albumId"
    fun viewer(source: String, index: Int) = "viewer/$source/$index"
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.PHOTOS, R.string.tab_photos, Icons.Filled.PhotoLibrary),
    Tab(Routes.ALBUMS, R.string.tab_albums, Icons.Outlined.Collections),
    Tab(Routes.FAVORITES, R.string.tab_favorites, Icons.Filled.Favorite),
)

@Composable
fun GalleryRoot(viewModel: GalleryViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionAsked by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    // Al volver de los ajustes del sistema puede que ya haya permiso.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.access) {
        if (state.access == MediaAccess.Denied && !permissionAsked) {
            permissionAsked = true
            permissionLauncher.launch(Permissions.required())
        }
    }

    if (state.access == MediaAccess.Denied) {
        PermissionScreen(
            showSettingsFallback = permissionAsked,
            onRequest = { permissionLauncher.launch(Permissions.required()) },
        )
        return
    }

    GalleryNavigation(
        state = state,
        viewModel = viewModel,
        onSelectMorePhotos = { permissionLauncher.launch(Permissions.required()) },
    )
}

@Composable
private fun GalleryNavigation(
    state: GalleryUiState,
    viewModel: GalleryViewModel,
    onSelectMorePhotos: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }
    val deleteItems = rememberDeleteAction { viewModel.onDeleted(it) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PHOTOS,
            modifier = Modifier,
        ) {
            composable(Routes.PHOTOS) {
                MediaGridScreen(
                    title = stringResource(R.string.app_name),
                    items = state.items,
                    favoriteKeys = state.favoriteKeys,
                    loading = state.loading,
                    emptyMessage = stringResource(R.string.empty_photos),
                    partialAccess = state.access == MediaAccess.Partial,
                    scaffoldPadding = padding,
                    onSelectMorePhotos = onSelectMorePhotos,
                    onRefresh = viewModel::refresh,
                    onOpen = { index -> navController.navigate(Routes.viewer("all", index)) },
                    onDelete = deleteItems,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }

            composable(Routes.FAVORITES) {
                val favorites = state.favorites
                MediaGridScreen(
                    title = stringResource(R.string.tab_favorites),
                    items = favorites,
                    favoriteKeys = state.favoriteKeys,
                    loading = state.loading,
                    emptyMessage = stringResource(R.string.empty_favorites),
                    partialAccess = false,
                    scaffoldPadding = padding,
                    onSelectMorePhotos = onSelectMorePhotos,
                    onRefresh = viewModel::refresh,
                    onOpen = { index -> navController.navigate(Routes.viewer("fav", index)) },
                    onDelete = deleteItems,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }

            composable(Routes.ALBUMS) {
                AlbumsScreenContainer(
                    state = state,
                    scaffoldPadding = padding,
                    onAlbumClick = { navController.navigate(Routes.album(it)) },
                )
            }

            composable(
                route = Routes.ALBUM_DETAIL,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            ) { entry ->
                val albumId = entry.arguments?.getLong("albumId") ?: 0L
                val items = remember(state.items, albumId) { viewModel.itemsForAlbum(albumId) }
                MediaGridScreen(
                    title = viewModel.albumName(albumId),
                    items = items,
                    favoriteKeys = state.favoriteKeys,
                    loading = state.loading,
                    emptyMessage = stringResource(R.string.empty_photos),
                    partialAccess = false,
                    scaffoldPadding = padding,
                    onBack = { navController.popBackStack() },
                    onSelectMorePhotos = onSelectMorePhotos,
                    onRefresh = viewModel::refresh,
                    onOpen = { index ->
                        navController.navigate(Routes.viewer("album-$albumId", index))
                    },
                    onDelete = deleteItems,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }

            composable(
                route = Routes.VIEWER,
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType },
                    navArgument("index") { type = NavType.IntType },
                ),
            ) { entry ->
                val source = entry.arguments?.getString("source") ?: "all"
                val index = entry.arguments?.getInt("index") ?: 0
                val items: List<MediaItem> = when {
                    source == "fav" -> state.favorites
                    source.startsWith("album-") -> {
                        val albumId = source.removePrefix("album-").toLongOrNull() ?: 0L
                        viewModel.itemsForAlbum(albumId)
                    }

                    else -> state.items
                }

                ViewerScreen(
                    items = items,
                    startIndex = index,
                    favoriteKeys = state.favoriteKeys,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDelete = deleteItems,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumsScreenContainer(
    state: GalleryUiState,
    scaffoldPadding: PaddingValues,
    onAlbumClick: (Long) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_albums)) }) },
    ) { innerPadding ->
        if (state.albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.empty_albums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            AlbumsScreen(
                albums = state.albums,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() + 24.dp,
                ),
                onAlbumClick = { onAlbumClick(it.id) },
            )
        }
    }
}
