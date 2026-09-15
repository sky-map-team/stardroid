/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.stardroid.R
import com.google.android.stardroid.ui.common.rememberAssetBitmap
import kotlinx.coroutines.delay

// Long enough to register as a brand beat, short enough not to slow the launch. The banner
// continues straight from the navy OS splash, so there is no fade-in — only a brief opaque hold
// and then a slow fade, over which the map behind it comes into view.
private const val HOLD_MILLIS = 500L
private const val FADE_MILLIS = 2000

private val BannerNavy = Color(0xFF0B1130)
private val StarGold = Color(0xFFFFC107)
private val TitleInk = Color(0xFFEEF2FF)

/**
 * The launch "version banner": the round app logo with the app name and version, held briefly
 * over an opaque navy field and then faded out, revealing the map loading behind it
 * (D-splash, Hybrid). It reuses the launcher-icon art so the icon, OS splash, and banner are one
 * asset. Tapping dismisses it immediately.
 *
 * @param onFinished called once the fade completes (or on tap), so the host can drop it from
 *   composition.
 */
@Composable
fun VersionBanner(
    versionName: String,
    onFinished: () -> Unit,
) {
    // The host passes a fresh lambda each recomposition, so track the latest rather than
    // keying the effect on it and restarting the animation.
    val currentOnFinished by rememberUpdatedState(onFinished)
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(HOLD_MILLIS)
        alpha.animateTo(targetValue = 0f, animationSpec = tween(FADE_MILLIS))
        currentOnFinished()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value }
                .background(BannerNavy)
                // clickable, not pointerInput: the tap-to-dismiss affordance has to carry
                // semantics so TalkBack can reach it. No indication — a ripple across the
                // whole splash would read as a defect.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.splash_dismiss),
                    onClick = currentOnFinished,
                ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoDisc(releasePortraitAsset(versionName))
        Text(
            text = stringResource(R.string.app_name),
            color = TitleInk,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        // The version is best-effort (the host swallows a PackageManager failure to ""), so
        // drop the line rather than show a bare "v".
        if (versionName.isNotEmpty()) {
            Text(
                text = stringResource(R.string.splash_version, versionName),
                color = StarGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The round "picture": the release's circular portrait when this release has one (v1's
 * per-release splash art, e.g. Apollo's Moon), else the nebula-gradient disc with the
 * launcher foreground art on top.
 */
@Composable
private fun LogoDisc(portraitAsset: String?) {
    val portrait = portraitAsset?.let { rememberAssetBitmap(it) }
    val nebula =
        remember {
            Brush.radialGradient(
                colors = listOf(Color(0xFF3D2F74), Color(0xFF1A1F4E), Color(0xFF060819)),
            )
        }
    Column(
        modifier =
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(nebula),
    ) {
        if (portrait != null) {
            Image(
                bitmap = portrait,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The circular portrait for a release, keyed by the codename after the ':' in the version
 * name ("2.0.0-alpha03:Apollo" → "apollo"). Drawn from the public-domain celestial images
 * already shipped for info cards — never from `branding/` (scientific imagery is not
 * All-Rights-Reserved, see LICENSE rules). Releases without an entry fall back to the
 * launcher art.
 */
internal fun releasePortraitAsset(versionName: String): String? =
    RELEASE_PORTRAITS[versionName.substringAfter(':', "").trim().lowercase()]

private val RELEASE_PORTRAITS =
    mapOf(
        // Apollo: NASA mission badge
        "apollo" to "splash/apollo.png",
    )
