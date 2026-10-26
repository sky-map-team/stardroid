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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.catalog.GalleryItem
import com.google.android.stardroid.ui.common.rememberCelestialThumbnailDecoder
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.gallery_back
import com.google.android.stardroid.ui.resources.gallery_title
import com.google.android.stardroid.ui.theme.NightPhotoTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * The image gallery — v1's `ImageGalleryActivity` grid as a full-screen Compose overlay:
 * three columns of celestial photos with their names, red-tinted in night mode. Tapping a
 * tile opens the object's info card (v1's `ObjectInfoDialogFragment` wiring), whose Find
 * button routes into the search flow. Back belongs to the host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    nightMode: Boolean,
    onItemClick: (GalleryItem) -> Unit,
    onBack: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val decode = rememberCelestialThumbnailDecoder(TARGET_THUMBNAIL_PX)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.gallery_title)) },
                windowInsets = topBarWindowInsets(stringResource(Res.string.gallery_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.gallery_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(items, key = { it.id.value }) { item ->
                    GalleryTile(item, nightMode, decode, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(
    item: GalleryItem,
    nightMode: Boolean,
    decode: (imageRef: String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        // remember(item.imageRef), not produceState: a recycled tile must show the cached
        // bitmap (or nothing) the instant its key changes, not one frame later once the
        // LaunchedEffect coroutine below gets to run.
        val bitmapState = remember(item.imageRef) { mutableStateOf(thumbnails[item.imageRef]) }
        LaunchedEffect(item.imageRef) {
            if (bitmapState.value == null) {
                bitmapState.value =
                    withContext(Dispatchers.IO) {
                        decode(item.imageRef)?.also { thumbnails.put(item.imageRef, it) }
                    }
            }
        }
        val bitmap by bitmapState
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            bitmap?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    colorFilter =
                        if (nightMode) {
                            ColorFilter.tint(NightPhotoTint, BlendMode.Modulate)
                        } else {
                            null
                        },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            item.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
        )
    }
}

/**
 * Warm thumbnails, kept for the app's life so rotation doesn't discard them; Android's
 * activity-scoped view model held them for as long before the screen was shared. Those a
 * screenful either side of the viewport stay warm; the rest re-decode on scroll-back.
 * ~48 tiles × ~256 KB ≈ 12 MB ceiling.
 */
private val thumbnails = LruCache<String, ImageBitmap>(48)

private const val TARGET_THUMBNAIL_PX = 256
