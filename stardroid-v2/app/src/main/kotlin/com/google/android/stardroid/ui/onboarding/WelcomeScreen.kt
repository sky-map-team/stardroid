/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.onboarding

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.catalog.CelestialObjectId
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.TypeCode
import com.google.android.stardroid.ui.objectinfo.ObjectInfoBody
import com.google.android.stardroid.ui.theme.NightPhotoTint
import com.google.android.stardroid.ui.theme.statusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SLIDE_COUNT = 3

/** Delay between revealing each sensor's result in the slide-3 check (v1's 0.8 s cadence). */
private const val SENSOR_CHECK_STEP_MS = 800L

/**
 * The text panel's width when it sits beside the illustration in landscape. Chosen for the
 * measure, not the space available: past roughly this width the slide descriptions stretch
 * into a line too long to read comfortably, and every dp beyond it is worth more to the
 * illustration — which is the half that is starved in landscape.
 */
private val PANEL_MAX_WIDTH: Dp = 340.dp

/**
 * The most of a landscape slide the text panel may take. A `Row` measures its unweighted
 * children first, so [PANEL_MAX_WIDTH] on its own would let the panel swallow a narrow
 * landscape window — multi-window, freeform, a small cover screen — and leave the
 * illustration a sliver or nothing at all. Whichever of the two limits is smaller wins.
 */
private const val PANEL_MAX_FRACTION = 0.45f

/**
 * The sensor check's content width in landscape. Its rows push the status icon to the far
 * end with a weighted spacer, so across a full landscape window the label and its tick end
 * up a screen apart.
 */
private val SENSOR_CONTENT_MAX_WIDTH: Dp = 480.dp

/** v1's bottom text-panel scrim (`#DD0B0F19` in `fragment_welcome_slide_1.xml`). */
private val PanelScrim = Color(0xDD0B0F19)

/** Legibility scrim over the slide-2/3 photos (v1 used `#55000000`). */
private val PhotoScrim = Color(0x55000000)

/**
 * v1 `WarmWelcomeActivity` as a Compose pager (screens-and-startup.md): the chrome tour over
 * a real map screenshot, a real info card over the Crab Nebula, and the sensor check +
 * calibration nudge over Iapetus — v1's three slides with v2's chrome and card (D61). Skip
 * and Launch! both complete the welcome — v1 marked it seen either way; [onSkip] vs
 * [onFinished] only distinguishes the analytics funnel (D49), and [onStarted]/[onSlideViewed]
 * feed the same.
 */
@Composable
fun WelcomeScreen(
    hasCompass: Boolean,
    hasAccelerometer: Boolean,
    hasGyroscope: Boolean,
    nightMode: Boolean,
    onFinished: () -> Unit,
    onSkip: () -> Unit = onFinished,
    onStarted: () -> Unit = {},
    onSlideViewed: (slideNumber: Int) -> Unit = {},
) {
    val pagerState = rememberPagerState { SLIDE_COUNT }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onStarted() }
    // Fires for slide 1 on entry and again per settled page (v1 logged both).
    LaunchedEffect(pagerState.settledPage) { onSlideViewed(pagerState.settledPage + 1) }
    // Black behind the full-bleed photos; contentColor is explicit because black isn't a
    // scheme color, so contentColorFor would otherwise leave the slide text unreadable.
    Surface(
        Modifier.fillMaxSize(),
        color = Color.Black,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 ->
                        ChromeTourSlide(
                            active = pagerState.settledPage == page,
                            nightMode = nightMode,
                        )
                    1 -> InfoCardSlide(nightMode = nightMode)
                    else ->
                        SensorSlide(
                            // The sensor check animates only once its slide is settled in
                            // front of the user (v1 ran it from onSelected).
                            active = pagerState.settledPage == page,
                            hasCompass = hasCompass,
                            hasAccelerometer = hasAccelerometer,
                            hasGyroscope = hasGyroscope,
                            nightMode = nightMode,
                        )
                }
            }
            BottomBar(
                currentPage = pagerState.currentPage,
                onSkip = onSkip,
                onNext = {
                    if (pagerState.currentPage == SLIDE_COUNT - 1) {
                        onFinished()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
            )
        }
    }
}

/** Skip / star page-dots / Next-Launch!, v1's bottom strip (star dots included). */
@Composable
private fun BottomBar(
    currentPage: Int,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PanelScrim)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.warm_welcome_skip))
        }
        Spacer(Modifier.weight(1f))
        PageStars(currentPage)
        Spacer(Modifier.weight(1f))
        val lastPage = currentPage == SLIDE_COUNT - 1
        Button(onClick = onNext) {
            Text(
                stringResource(
                    if (lastPage) {
                        R.string.warm_welcome_finish
                    } else {
                        R.string.warm_welcome_next
                    },
                ),
            )
        }
    }
}

/**
 * A slide's two halves — the illustration and its text panel — stacked in portrait and set
 * side by side in landscape.
 *
 * Landscape is the orientation with width to spare and none of the height, and the two halves
 * want opposite things: the panel is height-cheap (it scrolls), while the illustration is
 * width-cheap and height-hungry — [ChromeTourDemo] renders the real `MapChrome`, whose layer
 * rail and action cluster are edge-anchored columns in landscape. Stacked, they were sharing
 * a window height that neither could live in: the panel took its half and the chrome
 * overflowed what was left, clipping the rail and the last of the bottom-right actions off
 * the slide entirely.
 *
 * The panel is capped both by measure ([PANEL_MAX_WIDTH]) and by share
 * ([PANEL_MAX_FRACTION]): the measure keeps the text readable on a wide window or tablet by
 * handing the surplus to the illustration, and the share keeps the illustration alive on a
 * narrow one.
 */
@Composable
private fun SlideLayout(
    backdrop: @Composable () -> Unit,
    illustration: @Composable (Modifier) -> Unit,
    panel: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        backdrop()
        if (isLandscape()) {
            val panelWidth = minOf(PANEL_MAX_WIDTH, maxWidth * PANEL_MAX_FRACTION)
            Row(Modifier.fillMaxSize()) {
                illustration(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                panel(
                    Modifier
                        .width(panelWidth)
                        .fillMaxHeight(),
                )
            }
        } else {
            // Portrait keeps v1's stack, panel capped at half the slide so a large font
            // scale scrolls the text instead of squeezing the illustration to nothing.
            val panelMaxHeight = maxHeight / 2
            Column(Modifier.fillMaxSize()) {
                illustration(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                panel(Modifier.heightIn(max = panelMaxHeight))
            }
        }
    }
}

@Composable
private fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Slide 1: the guided tour of the real map chrome over a real v2 sky capture. */
@Composable
private fun ChromeTourSlide(
    active: Boolean,
    nightMode: Boolean,
) {
    SlideLayout(
        backdrop = {
            Image(
                painterResource(R.drawable.welcome_sky_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = nightPhotoFilter(nightMode),
                modifier = Modifier.fillMaxSize(),
            )
        },
        illustration = { modifier ->
            ChromeTourDemo(active = active, nightMode = nightMode, modifier = modifier)
        },
        panel = { modifier ->
            TextPanel(
                R.string.warm_welcome_slide1_title,
                R.string.warm_welcome_slide1_desc,
                modifier = modifier,
            )
        },
    )
}

/** Slide 2: navigation modes plus a genuine info card of the Crab Nebula, over its photo. */
@Composable
private fun InfoCardSlide(nightMode: Boolean) {
    SlideLayout(
        backdrop = {
            AssetBackdrop("celestial_images/deep_sky_objects/hubble_m1.jpg", nightMode)
        },
        illustration = { modifier ->
            Box(
                modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                SampleInfoCard(nightMode)
            }
        },
        panel = { modifier ->
            TextPanel(
                R.string.warm_welcome_slide2_title,
                R.string.warm_welcome_slide2_desc,
                R.string.warm_welcome_slide2_info_desc,
                modifier = modifier,
            )
        },
    )
}

/**
 * The real [ObjectInfoBody] with a canned Crab Nebula — the very object in the slide's
 * background photo. Strings come from resources so the translation pipeline covers them; the
 * shipped card for M1 draws the same text from the catalog.
 */
@Composable
private fun SampleInfoCard(nightMode: Boolean) {
    val info =
        ObjectInfo(
            id = CelestialObjectId("dso/m1"),
            name = stringResource(R.string.warm_welcome_card_name),
            type = TypeCode("nebula"),
            position = null,
            parent = null,
            magnitude = 8.4,
            description = stringResource(R.string.warm_welcome_card_description),
            funFact = stringResource(R.string.warm_welcome_card_fun_fact),
            distance = stringResource(R.string.warm_welcome_card_distance),
            size = stringResource(R.string.warm_welcome_card_size),
            mass = null,
            spectralClass = null,
            imageRef = "deep_sky_objects/hubble_m1.jpg",
            imageCredit = "NASA/ESA/Hubble",
            searchSubtext = null,
        )
    Card {
        Column(
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                info.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ObjectInfoBody(
                info = info,
                riseSet = null,
                nightMode = nightMode,
                onSeeAlso = {},
                onImageTap = {},
            )
        }
    }
}

/** Slide 3: the sensor check and calibration nudge, over v1's Iapetus photo. */
@Composable
private fun SensorSlide(
    active: Boolean,
    hasCompass: Boolean,
    hasAccelerometer: Boolean,
    hasGyroscope: Boolean,
    nightMode: Boolean,
) {
    val sensors =
        listOf(
            SensorCheckRow(R.string.diagnostics_compass, R.drawable.ic_compass, hasCompass),
            SensorCheckRow(
                R.string.diagnostics_accelerometer,
                R.drawable.ic_accelerometer,
                hasAccelerometer,
            ),
            SensorCheckRow(R.string.diagnostics_gyroscope, R.drawable.ic_gyroscope, hasGyroscope),
        )
    val context = LocalContext.current
    // How many sensors have finished "checking" and revealed their status. All spin at first;
    // the check ticks them up one at a time (v1 revealed compass/accel/gyro at 0.8/1.6/2.4 s
    // with a per-sensor buzz — a happy tap when present, a double-buzz when missing).
    var revealed by remember { mutableIntStateOf(sensors.size) }
    LaunchedEffect(active) {
        if (!active) {
            revealed = sensors.size
            return@LaunchedEffect
        }
        revealed = 0
        for ((index, sensor) in sensors.withIndex()) {
            delay(SENSOR_CHECK_STEP_MS)
            revealed = index + 1
            context.sensorCheckBuzz(present = sensor.present)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AssetBackdrop("celestial_images/planets/cassini_iapetus.webp", nightMode)
        Column(
            Modifier
                .fillMaxHeight()
                // This slide has no separate text panel to set beside anything, so landscape
                // only needs its content held to a readable block rather than stretched the
                // full width of the window (see SENSOR_CONTENT_MAX_WIDTH).
                .then(
                    if (isLandscape()) {
                        Modifier.widthIn(max = SENSOR_CONTENT_MAX_WIDTH)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            SlideTitle(R.string.warm_welcome_slide3_title)
            SlideBody(R.string.warm_welcome_slide3_desc)
            Spacer(Modifier.height(8.dp))
            sensors.forEachIndexed { index, sensor ->
                SensorRow(sensor, checking = index >= revealed, nightMode = nightMode)
            }
            Spacer(Modifier.height(16.dp))
            // v1: the calibration nudge, or the manual-mode reassurance when the required
            // sensors (compass + accelerometer) are missing — held back until the check
            // finishes.
            if (revealed >= sensors.size) {
                SlideBody(
                    if (hasCompass && hasAccelerometer) {
                        R.string.warm_welcome_slide3_compass_calib
                    } else {
                        R.string.warm_welcome_slide3_no_sensors
                    },
                )
            }
        }
    }
}

private data class SensorCheckRow(
    val labelRes: Int,
    val iconRes: Int,
    val present: Boolean,
)

/**
 * A full-bleed background photo from the bundled `celestial_images/` assets (the same tree
 * the info cards use), dimmed for text legibility and red-tinted in night mode like every
 * photo (D46).
 */
@Composable
private fun AssetBackdrop(
    assetPath: String,
    nightMode: Boolean,
) {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(initialValue = null, assetPath) {
        value =
            withContext(Dispatchers.IO) {
                try {
                    assets.open(assetPath).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = nightPhotoFilter(nightMode),
            modifier = Modifier.fillMaxSize(),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(PhotoScrim),
    )
}

private fun nightPhotoFilter(nightMode: Boolean): ColorFilter? =
    if (nightMode) ColorFilter.tint(NightPhotoTint, BlendMode.Modulate) else null

/**
 * v1's text panel: title and body over the dark scrim. Sits under the illustration in
 * portrait and beside it in landscape; [SlideLayout] owns that placement and the size cap
 * that goes with it, and the panel scrolls when the text outgrows what it is given.
 */
@Composable
private fun TextPanel(
    vararg textRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(PanelScrim)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        SlideTitle(textRes.first())
        for (body in textRes.drop(1)) {
            SlideBody(body)
        }
    }
}

@Composable
private fun SlideTitle(resId: Int) {
    Text(
        stringResource(resId),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun SlideBody(resId: Int) {
    Text(
        AnnotatedString.fromHtml(stringResource(resId)),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SensorRow(
    sensor: SensorCheckRow,
    checking: Boolean,
    nightMode: Boolean,
) {
    val colors = statusColors(nightMode)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(sensor.iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(stringResource(sensor.labelRes), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        if (checking) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (sensor.present) {
            // v1's green check / warning triangle, described for TalkBack by the old text.
            Icon(
                painterResource(R.drawable.ic_check_circle),
                contentDescription = stringResource(R.string.warm_welcome_sensor_ok),
                tint = colors.good,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = stringResource(R.string.warm_welcome_sensor_missing),
                tint = colors.bad,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** v1's star-shaped page dots: the current page's star is lit, the rest dimmed. */
@Composable
private fun PageStars(currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(SLIDE_COUNT) { page ->
            Icon(
                painterResource(R.drawable.ic_welcome_indicator),
                contentDescription = null,
                tint =
                    if (page == currentPage) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
