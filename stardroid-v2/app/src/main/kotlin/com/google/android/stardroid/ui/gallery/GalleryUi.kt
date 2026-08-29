/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.gallery

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R
import com.google.android.stardroid.catalog.GalleryItem
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.theme.NightPhotoTint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The image gallery — v1's `ImageGalleryActivity` grid as a full-screen Compose overlay:
 * three columns of celestial photos with their names, red-tinted in night mode. Tapping a
 * tile opens the object's info card (v1's `ObjectInfoDialogFragment` wiring), whose Find
 * button routes into the search flow.
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
    val cache = viewModel.thumbnailCache
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                windowInsets = topBarWindowInsets(stringResource(R.string.gallery_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.gallery_back),
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
                    GalleryTile(item, nightMode, cache, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(
    item: GalleryItem,
    nightMode: Boolean,
    cache: LruCache<String, ImageBitmap>,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        val assets = LocalContext.current.assets
        // remember(item.imageRef), not produceState: a recycled tile must show the cached
        // bitmap (or nothing) the instant its key changes, not one frame later once the
        // LaunchedEffect coroutine below gets to run.
        val bitmapState = remember(item.imageRef) { mutableStateOf(cache.get(item.imageRef)) }
        LaunchedEffect(item.imageRef) {
            if (bitmapState.value == null) {
                bitmapState.value =
                    withContext(Dispatchers.IO) {
                        decodeThumbnail(assets, item.imageRef)?.also {
                            cache.put(item.imageRef, it)
                        }
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
 * Grid-thumbnail decode: power-of-two downsample toward [TARGET_THUMBNAIL_PX] on the short
 * side, so a 3-across grid never holds full-resolution bitmaps (v1 delegated this to Coil).
 * A missing or corrupt asset is an empty tile; the name below is the content of record.
 */
private fun decodeThumbnail(
    assets: AssetManager,
    imageRef: String,
): ImageBitmap? =
    try {
        val path = "celestial_images/$imageRef"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= TARGET_THUMBNAIL_PX &&
            bounds.outHeight / (sampleSize * 2) >= TARGET_THUMBNAIL_PX
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        assets.open(path).use {
            BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

private const val TARGET_THUMBNAIL_PX = 256
