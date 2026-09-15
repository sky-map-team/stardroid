/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.gallery

import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.GalleryItem
import com.google.android.stardroid.catalog.LocaleSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The image-gallery grid content: every catalog object with a photo, name-sorted for the
 * requested locale (v1's `ImageGalleryActivity` over `ObjectInfoRegistry.getAllWithImages`).
 * Tapping a tile opens the object's info card, owned by `ObjectInfoViewModel` — this view
 * model only lists.
 */
class GalleryViewModel(
    private val catalog: suspend () -> CatalogRepository,
    private val locale: StateFlow<LocaleSpec>,
) : ViewModel() {
    private val _items = MutableStateFlow<List<GalleryItem>>(emptyList())
    val items: StateFlow<List<GalleryItem>> = _items.asStateFlow()

    // Owned by the ViewModel, not the composable, so rotation doesn't discard warm thumbnails.
    // Thumbnails a screenful either side of the viewport stay warm; the rest re-decode on
    // scroll-back. ~48 tiles × ~256 KB ≈ 12 MB ceiling.
    val thumbnailCache = LruCache<String, ImageBitmap>(48)

    // Re-listed on a language change: the view model outlives the activity recreation the
    // switch triggers, so a once-only load would keep showing the old language's names.
    init {
        viewModelScope.launch {
            locale.collectLatest { _items.value = catalog().galleryItems(it) }
        }
    }
}
