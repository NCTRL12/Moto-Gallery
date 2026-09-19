package com.nctrl.motogallery.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nctrl.motogallery.R
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.ui.components.OneUiChip
import com.nctrl.motogallery.ui.components.OneUiSearchField
import com.nctrl.motogallery.ui.screens.AlbumsGrid
import com.nctrl.motogallery.ui.screens.EditorScreen
import com.nctrl.motogallery.ui.screens.MediaGridScreen
import com.nctrl.motogallery.ui.screens.PermissionScreen
import com.nctrl.motogallery.ui.screens.PlacesGrid
import com.nctrl.motogallery.ui.screens.SearchScreen
import com.nctrl.motogallery.ui.screens.TrashScreen
import com.nctrl.motogallery.ui.screens.ViewerScreen
import com.nctrl.motogallery.ui.theme.Motion
import com.nctrl.motogallery.util.MediaAccess
import com.nctrl.motogallery.util.Permissions

private object Routes {
    const val PHOTOS = "photos"
    const val ALBUMS = "albums"
    const val PLACES = "places"
    const val FAVORITES = "favorites"
    const val SEARCH = "search"
    const val TRASH = "trash"
    const val EDITOR = "edit/{uri}?enhance={enhance}"
    const val ALBUM_DETAIL = "album/{albumId}"
    const val PLACE_DETAIL = "place/{place}"
    const val VIEWER = "viewer/{source}/{index}"

    fun album(albumId: Long) = "album/$albumId"
    fun place(name: String) = "place/${Uri.encode(name)}"
    fun viewer(source: String, index: Int) = "viewer/${Uri.encode(source)}/$index"
    fun editor(uri: Uri, enhance: Boolean = false) =
        "edit/${Uri.encode(uri.toString())}?enhance=$enhance"
}

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.PHOTOS, R.string.tab_photos, Icons.Filled.PhotoLibrary),
    Tab(Routes.ALBUMS, R.string.tab_albums, Icons.Outlined.Collections),
    Tab(Routes.PLACES, R.string.tab_places, Icons.Filled.Place),
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
    val actions = rememberMediaActions(viewModel::onMediaChanged)
    val searchState by viewModel.search.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val bottomPadding = padding.calculateBottomPadding()

        NavHost(
            navController = navController,
            startDestination = Routes.PHOTOS,
            enterTransition = { fadeIn(Motion.quick()) + scaleIn(Motion.soft(), initialScale = 0.97f) },
            exitTransition = { fadeOut(Motion.quick()) },
            popEnterTransition = { fadeIn(Motion.quick()) },
            popExitTransition = { fadeOut(Motion.quick()) + scaleOut(Motion.quick(), targetScale = 0.97f) },
        ) {
            composable(Routes.PHOTOS) {
                MediaGridScreen(
                    title = stringResource(R.string.app_name),
                    items = state.filtered,
                    favoriteKeys = state.favoriteKeys,
                    loading = state.loading,
                    emptyMessage = stringResource(R.string.empty_photos),
                    bottomPadding = bottomPadding,
                    partialAccess = state.access == MediaAccess.Partial,
                    onSelectMorePhotos = onSelectMorePhotos,
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onOpen = { navController.navigate(Routes.viewer("home", it)) },
                    onDelete = actions.trash,
                    onToggleFavorite = viewModel::toggleFavorite,
                    header = {
                        HomeHeader(
                            filter = state.filter,
                            onFilterChange = viewModel::setFilter,
                            onSearchClick = { navController.navigate(Routes.SEARCH) },
                        )
                    },
                )
            }

            composable(Routes.FAVORITES) {
                MediaGridScreen(
                    title = stringResource(R.string.tab_favorites),
                    items = state.favorites,
                    favoriteKeys = state.favoriteKeys,
                    loading = state.loading,
                    emptyMessage = stringResource(R.string.empty_favorites),
                    bottomPadding = bottomPadding,
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onOpen = { navController.navigate(Routes.viewer("fav", it)) },
                    onDelete = actions.trash,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }

            composable(Routes.ALBUMS) {
                CollectionScaffold(
                    title = stringResource(R.string.tab_albums),
                    action = {
                        OneUiChip(
                            label = stringResource(R.string.tab_trash),
                            selected = false,
                            onClick = { navController.navigate(Routes.TRASH) },
                            icon = Icons.Default.Delete,
                        )
                    },
                ) { topPadding ->
                    AlbumsGrid(
                        albums = state.albums,
                        bottomPadding = bottomPadding,
                        topPadding = topPadding,
                        onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                    )
                }
            }

            composable(Routes.PLACES) {
                CollectionScaffold(title = stringResource(R.string.tab_places)) { topPadding ->
                    PlacesGrid(
                        places = state.placeGroups,
                        indexing = state.indexingPlaces,
                        bottomPadding = bottomPadding,
                        topPadding = topPadding,
                        onPlaceClick = { navController.navigate(Routes.place(it.name)) },
                    )
                }
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
                    bottomPadding = bottomPadding,
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(Routes.viewer("album-$albumId", it)) },
                    onDelete = actions.trash,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }

            composable(
                route = Routes.PLACE_DETAIL,
                arguments = listOf(navArgument("place") { type = NavType.StringType }),
            ) { entry ->
                val place = entry.arguments?.getString("place").orEmpty()
                val items = remember(state.items, state.places, place) {
                    viewModel.itemsForPlace(place)
                }
                MediaGridScreen(
                    title = place,
                    items = items,
                    favoriteKeys = state.favoriteKeys,
                    loading = state.loading,
                    emptyMessage = stringResource(R.string.empty_photos),
                    bottomPadding = bottomPadding,
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(Routes.viewer("place-$place", it)) },
                    onDelete = actions.trash,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }

            composable(Routes.TRASH) {
                // Se recarga al entrar: se pudo borrar algo desde otra app.
                LaunchedEffect(Unit) { viewModel.refreshTrash() }

                TrashScreen(
                    items = state.trashed,
                    supported = state.trashSupported,
                    bottomPadding = bottomPadding,
                    onOpen = { navController.navigate(Routes.viewer("trash", it)) },
                    onRestore = actions.restore,
                    onDeleteForever = actions.deleteForever,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("enhance") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val raw = entry.arguments?.getString("uri").orEmpty()
                val enhance = entry.arguments?.getBoolean("enhance") ?: false
                val uri = remember(raw) { Uri.parse(raw) }
                val name = remember(raw, state.items) {
                    state.items.firstOrNull { it.uri.toString() == raw }?.name ?: "IMG"
                }
                val context = LocalContext.current
                val savedMessage = stringResource(R.string.editor_saved)

                EditorScreen(
                    uri = uri,
                    sourceName = name,
                    enhance = enhance,
                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    onClose = { navController.popBackStack() },
                    onSaved = {
                        Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                        // La copia nueva entra por el ContentObserver, pero se
                        // fuerza la recarga para verla nada más volver.
                        viewModel.refresh()
                        navController.popBackStack()
                    },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    state = searchState,
                    favoriteKeys = state.favoriteKeys,
                    bottomPadding = bottomPadding,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onOpen = { navController.navigate(Routes.viewer("search", it)) },
                    onDelete = actions.trash,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.VIEWER,
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType },
                    navArgument("index") { type = NavType.IntType },
                ),
                enterTransition = {
                    fadeIn(Motion.medium()) + scaleIn(Motion.soft(), initialScale = 0.92f)
                },
                popExitTransition = {
                    fadeOut(Motion.medium()) + scaleOut(Motion.soft(), targetScale = 0.92f)
                },
            ) { entry ->
                val source = entry.arguments?.getString("source") ?: "home"
                val index = entry.arguments?.getInt("index") ?: 0
                val items = remember(source, state.items, state.favoriteKeys, searchState.results) {
                    viewModel.itemsForSource(source, searchState.results)
                }

                ViewerScreen(
                    items = items,
                    startIndex = index,
                    favoriteKeys = state.favoriteKeys,
                    place = { item -> state.places[item.key] },
                    onToggleFavorite = viewModel::toggleFavorite,
                    // Lo que ya está en la papelera no puede volver a tirarse.
                    onDelete = if (source == "trash") actions.deleteForever else actions.trash,
                    onBack = { navController.popBackStack() },
                    onEdit = if (source == "trash") {
                        null
                    } else {
                        { item -> navController.navigate(Routes.editor(item.uri)) }
                    },
                    onEnhance = if (source == "trash") {
                        null
                    } else {
                        { item ->
                            navController.navigate(Routes.editor(item.uri, enhance = true))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    filter: MediaFilter,
    onFilterChange: (MediaFilter) -> Unit,
    onSearchClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 10.dp)) {
        OneUiSearchField(
            placeholder = stringResource(R.string.search_hint),
            onClick = onSearchClick,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 2.dp),
        ) {
            OneUiChip(
                label = stringResource(R.string.filter_all),
                selected = filter == MediaFilter.ALL,
                onClick = { onFilterChange(MediaFilter.ALL) },
            )
            OneUiChip(
                label = stringResource(R.string.filter_photos),
                selected = filter == MediaFilter.PHOTOS,
                onClick = { onFilterChange(MediaFilter.PHOTOS) },
            )
            OneUiChip(
                label = stringResource(R.string.filter_videos),
                selected = filter == MediaFilter.VIDEOS,
                onClick = { onFilterChange(MediaFilter.VIDEOS) },
            )
            OneUiChip(
                label = stringResource(R.string.filter_favorites),
                selected = filter == MediaFilter.FAVORITES,
                onClick = { onFilterChange(MediaFilter.FAVORITES) },
            )
        }
    }
}

/** Cabecera grande reutilizada por álbumes y lugares. */
@Composable
private fun CollectionScaffold(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable (topPadding: Dp) -> Unit,
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        content(8.dp)
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
