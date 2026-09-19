package com.nctrl.motogallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nctrl.motogallery.data.Album
import com.nctrl.motogallery.data.FavoritesStore
import com.nctrl.motogallery.data.MediaItem
import com.nctrl.motogallery.data.MediaRepository
import com.nctrl.motogallery.data.toAlbums
import com.nctrl.motogallery.util.MediaAccess
import com.nctrl.motogallery.util.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val loading: Boolean = true,
    val access: MediaAccess = MediaAccess.Denied,
    val items: List<MediaItem> = emptyList(),
    val albums: List<Album> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
) {
    val favorites: List<MediaItem> get() = items.filter { it.key in favoriteKeys }
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val favoritesStore = FavoritesStore(application)

    private val _state = MutableStateFlow(
        GalleryUiState(favoriteKeys = favoritesStore.load())
    )
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    private val observer = repository.observeChanges { refresh() }

    init {
        refresh()
    }

    fun refresh() {
        val access = Permissions.check(getApplication())
        _state.update { it.copy(access = access) }

        if (access == MediaAccess.Denied) {
            _state.update { it.copy(loading = false, items = emptyList(), albums = emptyList()) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = repository.loadAll()
            _state.update {
                it.copy(loading = false, items = items, albums = items.toAlbums())
            }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        _state.update { current ->
            val keys = current.favoriteKeys.toMutableSet()
            if (!keys.add(item.key)) keys.remove(item.key)
            favoritesStore.save(keys)
            current.copy(favoriteKeys = keys)
        }
    }

    fun isFavorite(item: MediaItem): Boolean = item.key in _state.value.favoriteKeys

    fun itemsForAlbum(albumId: Long): List<MediaItem> =
        _state.value.items.filter { it.bucketId == albumId }

    fun albumName(albumId: Long): String =
        _state.value.albums.firstOrNull { it.id == albumId }?.name.orEmpty()

    /**
     * Quita de la lista lo que el sistema ya borró, para que el grid se
     * actualice sin esperar al ContentObserver.
     */
    fun onDeleted(deleted: List<MediaItem>) {
        val deletedKeys = deleted.map { it.key }.toSet()
        _state.update { current ->
            val items = current.items.filterNot { it.key in deletedKeys }
            val keys = current.favoriteKeys - deletedKeys
            favoritesStore.save(keys)
            current.copy(items = items, albums = items.toAlbums(), favoriteKeys = keys)
        }
        refresh()
    }

    override fun onCleared() {
        repository.stopObserving(observer)
        super.onCleared()
    }
}
