package com.nctrl.motogallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nctrl.motogallery.data.Album
import com.nctrl.motogallery.data.FavoritesStore
import com.nctrl.motogallery.data.LocationIndexer
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.data.MediaRepository
import com.nctrl.motogallery.data.Place
import com.nctrl.motogallery.data.SearchIndex
import com.nctrl.motogallery.data.SearchSuggestion
import com.nctrl.motogallery.data.toAlbums
import com.nctrl.motogallery.util.MediaAccess
import com.nctrl.motogallery.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Filtro rápido de la barra de chips. */
enum class MediaFilter { ALL, PHOTOS, VIDEOS, FAVORITES }

data class GalleryUiState(
    val loading: Boolean = true,
    val access: MediaAccess = MediaAccess.Denied,
    val items: List<MediaItem> = emptyList(),
    val albums: List<Album> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    /** item.key -> nombre del sitio donde se tomó. */
    val places: Map<String, String> = emptyMap(),
    val placeGroups: List<Place> = emptyList(),
    val indexingPlaces: Boolean = false,
    val filter: MediaFilter = MediaFilter.ALL,
) {
    // lazy: se calcula una vez por estado, no en cada recomposición.
    val favorites: List<MediaItem> by lazy { items.filter { it.key in favoriteKeys } }

    val filtered: List<MediaItem> by lazy {
        when (filter) {
            MediaFilter.ALL -> items
            MediaFilter.PHOTOS -> items.filter { !it.isVideo }
            MediaFilter.VIDEOS -> items.filter { it.isVideo }
            MediaFilter.FAVORITES -> favorites
        }
    }
}

data class SearchState(
    val query: String = "",
    val results: List<MediaItem> = emptyList(),
    val suggestions: List<SearchSuggestion> = emptyList(),
    val searching: Boolean = false,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val favoritesStore = FavoritesStore(application)
    private val locationIndexer = LocationIndexer(application)

    private val _state = MutableStateFlow(
        GalleryUiState(
            favoriteKeys = favoritesStore.load(),
            places = locationIndexer.cached(),
        )
    )
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private var index: SearchIndex = SearchIndex.EMPTY
    private var loadJob: Job? = null
    private var indexJob: Job? = null
    private var searchJob: Job? = null

    private val observer = repository.observeChanges { scheduleRefresh() }

    init {
        refresh()
    }

    fun refresh() = load(delayMs = 0)

    /**
     * El ContentObserver dispara varias veces seguidas en una ráfaga de fotos;
     * se agrupan en una sola recarga para no repetir la consulta entera.
     */
    private fun scheduleRefresh() = load(delayMs = 450)

    private fun load(delayMs: Long) {
        val access = Permissions.check(getApplication())
        _state.update { it.copy(access = access) }

        if (access == MediaAccess.Denied) {
            _state.update { it.copy(loading = false, items = emptyList(), albums = emptyList()) }
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            _state.update { it.copy(loading = true) }

            val items = repository.loadAll()
            // Agrupar y ordenar miles de elementos no toca el hilo principal.
            val albums = withContext(Dispatchers.Default) { items.toAlbums() }

            _state.update { current ->
                current.copy(
                    loading = false,
                    items = items,
                    albums = albums,
                    placeGroups = groupPlaces(items, current.places),
                )
            }

            rebuildIndex()
            indexPlaces(items)
        }
    }

    private fun indexPlaces(items: List<MediaItem>) {
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            _state.update { it.copy(indexingPlaces = true) }
            locationIndexer.index(items) { places ->
                val groups = withContext(Dispatchers.Default) {
                    groupPlaces(_state.value.items, places)
                }
                _state.update { it.copy(places = places, placeGroups = groups) }
                rebuildIndex()
            }
            _state.update { it.copy(indexingPlaces = false) }
        }
    }

    private suspend fun rebuildIndex() {
        val snapshot = _state.value
        index = withContext(Dispatchers.Default) {
            SearchIndex.build(snapshot.items, snapshot.places)
        }
        // Si había una búsqueda escrita, se refresca con el índice nuevo.
        if (_search.value.query.isNotBlank()) onSearchQueryChange(_search.value.query)
        refreshSuggestions()
    }

    private fun groupPlaces(items: List<MediaItem>, places: Map<String, String>): List<Place> =
        items.mapNotNull { item -> places[item.key]?.takeIf { it.isNotBlank() }?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .map { (name, grouped) -> Place(name, grouped.first(), grouped.size) }
            .sortedByDescending { it.count }

    fun onSearchQueryChange(query: String) {
        _search.update { it.copy(query = query, searching = query.isNotBlank()) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _search.update { it.copy(results = emptyList(), searching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(140) // Deja de buscar en cada pulsación de tecla.
            val results = withContext(Dispatchers.Default) { index.query(query) }
            _search.update { it.copy(results = results, searching = false) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.update { it.copy(query = "", results = emptyList(), searching = false) }
    }

    private fun refreshSuggestions() {
        val snapshot = _state.value
        val suggestions = buildList {
            snapshot.placeGroups.take(6).forEach {
                add(SearchSuggestion(it.name, SearchSuggestion.Kind.PLACE, it.cover))
            }
            snapshot.albums.take(4).forEach {
                add(SearchSuggestion(it.name, SearchSuggestion.Kind.ALBUM, it.cover))
            }
            snapshot.items
                .map { yearOf(it.dateTaken) }
                .distinct()
                .take(4)
                .forEach { add(SearchSuggestion(it, SearchSuggestion.Kind.YEAR, null)) }
            add(SearchSuggestion("Vídeos", SearchSuggestion.Kind.TYPE, null))
            add(SearchSuggestion("Capturas", SearchSuggestion.Kind.TYPE, null))
        }
        _search.update { it.copy(suggestions = suggestions) }
    }

    private fun yearOf(millis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return calendar.get(Calendar.YEAR).toString()
    }

    fun setFilter(filter: MediaFilter) = _state.update { it.copy(filter = filter) }

    fun toggleFavorite(item: MediaItem) {
        _state.update { current ->
            val keys = current.favoriteKeys.toMutableSet()
            if (!keys.add(item.key)) keys.remove(item.key)
            favoritesStore.save(keys)
            current.copy(favoriteKeys = keys)
        }
    }

    fun itemsForAlbum(albumId: Long): List<MediaItem> =
        _state.value.items.filter { it.bucketId == albumId }

    fun albumName(albumId: Long): String =
        _state.value.albums.firstOrNull { it.id == albumId }?.name.orEmpty()

    fun itemsForPlace(place: String): List<MediaItem> {
        val places = _state.value.places
        return _state.value.items.filter { places[it.key] == place }
    }

    /** Resuelve la lista que debe mostrar el visor según de dónde se abrió. */
    fun itemsForSource(source: String, searchResults: List<MediaItem>): List<MediaItem> = when {
        source == "fav" -> _state.value.favorites
        source == "search" -> searchResults
        source.startsWith("album-") ->
            itemsForAlbum(source.removePrefix("album-").toLongOrNull() ?: 0L)

        source.startsWith("place-") -> itemsForPlace(source.removePrefix("place-"))
        else -> _state.value.filtered
    }

    /** Quita lo borrado al momento para que la rejilla no espere al sistema. */
    fun onDeleted(deleted: List<MediaItem>) {
        val deletedKeys = deleted.map { it.key }.toSet()
        _state.update { current ->
            val keys = current.favoriteKeys - deletedKeys
            favoritesStore.save(keys)
            current.copy(
                items = current.items.filterNot { it.key in deletedKeys },
                favoriteKeys = keys,
            )
        }
        refresh()
    }

    override fun onCleared() {
        repository.stopObserving(observer)
        super.onCleared()
    }
}
