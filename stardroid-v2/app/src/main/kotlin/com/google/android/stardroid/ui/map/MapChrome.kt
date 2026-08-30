/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R
import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.EclipticLayer
import com.google.android.stardroid.layers.GridLayer
import com.google.android.stardroid.layers.HorizonLayer
import com.google.android.stardroid.layers.LayerParameter
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.ui.layers.LayerDataStatus
import com.google.android.stardroid.ui.layers.LayerParameterState
import com.google.android.stardroid.ui.layers.LayerToggle
import com.google.android.stardroid.ui.layers.LayersViewModel
import com.google.android.stardroid.ui.theme.statusColors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * One highlightable control of the map chrome, keyed for the warm-welcome tour (D61): the
 * tour hands [MapChrome] a per-target modifier so each control can report its on-screen
 * bounds, then cycles a spotlight through [CHROME_TOUR_STOPS].
 */
sealed interface ChromeTourTarget {
    data class Layer(val id: LayerId) : ChromeTourTarget

    data object LayersExpand : ChromeTourTarget

    data object Search : ChromeTourTarget

    data object TimeTravel : ChromeTourTarget

    data object NightMode : ChromeTourTarget

    data object AutoManual : ChromeTourTarget

    data object Overflow : ChromeTourTarget
}

/**
 * The three-zone map chrome (ux-polish item 1, D56): the layer rail on the left edge, the
 * primary actions at bottom-right (a right-edge column in landscape), and the ⋮ overflow
 * entry. Night mode re-tints all of it through the red scheme (D46). State is hoisted
 * ([toggles]/[onToggleLayer]) so the warm welcome can render the same chrome with canned
 * state (D61); [tourTargetModifier] lets the tour tag each control to track its position,
 * and defaults to a no-op for the real map.
 *
 * [visible] drives the show/hide transition *per zone*, each sliding toward the edge it is
 * anchored to, so the motion shows where the controls went and implies they can be brought
 * back — a plain cross-fade leaves a bare starfield and teaches nothing. The animation lives
 * here rather than in `MapScreen` because only this composable knows which edge owns which
 * zone. Defaults to `true` for the tour, which renders the chrome permanently shown.
 */
@Composable
fun MapChrome(
    toggles: List<LayerToggle>,
    nightMode: Boolean,
    referenceFrame: ReferenceFrame,
    sensorsAvailable: Boolean,
    onToggleLayer: (LayerId, Boolean) -> Unit,
    onToggleReferenceFrame: () -> Unit,
    onToggleNightMode: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTimeTravel: () -> Unit,
    onOpenLayersSheet: () -> Unit,
    onOpenOverflow: () -> Unit,
    modifier: Modifier = Modifier,
    // Null hides the HUD — the warm-welcome tour renders this chrome with canned state and
    // no live pointing to show.
    hudState: HudState? = null,
    // Extra top offset for the HUD, so the host can push it below the time-travel player
    // when both occupy the top band (portrait).
    hudTopClearance: Dp = 0.dp,
    onResetAlignment: () -> Unit = {},
    // Null hides the AR exposure/dimmer panel (camera layer off, and always in the tour).
    arUi: ArUiState? = null,
    arSpecs: ArCameraSpecs? = null,
    onArScrimChange: (Double) -> Unit = {},
    onArExposureChange: (Int) -> Unit = {},
    onArIsoFractionChange: (Double) -> Unit = {},
    onArShutterFractionChange: (Double) -> Unit = {},
    onShareShutter: () -> Unit = {},
    // The in-AR shutter is a share entry point, so it follows the SHARE_SKY experiment
    // independently of the camera layer itself.
    shareEnabled: Boolean = true,
    tourTargetModifier: (ChromeTourTarget) -> Modifier = { Modifier },
    // The rail's teaching labels (D83): shown for the first few reveals because the icons
    // alone read wrong to newcomers, then retired. Null means no labels at all — the tour,
    // which names each control in its own spotlight.
    railLabels: RailLabelState? = null,
    visible: Boolean = true,
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Top-right (D65): top-centre belongs to the time-travel player, the rail owns the
        // left edge in landscape, and the action cluster owns the bottom/right-edge corner.
        if (hudState != null) {
            ChromeZone(visible, ChromeEdge.TOP, Modifier.align(Alignment.TopEnd)) {
                MapHud(
                    state = hudState,
                    onResetAlignment = onResetAlignment,
                    modifier = Modifier.padding(top = 8.dp + hudTopClearance, end = 8.dp),
                )
            }
        }
        ChromeZone(
            visible,
            ChromeEdge.START,
            Modifier.align(Alignment.CenterStart).fillMaxHeight(),
        ) {
            LayerRail(
                toggles = toggles,
                nightMode = nightMode,
                onToggleLayer = onToggleLayer,
                onOpenLayersSheet = onOpenLayersSheet,
                tourTargetModifier = tourTargetModifier,
                railLabels = railLabels,
                modifier = Modifier.fillMaxHeight().padding(start = 8.dp),
            )
        }
        // Bottom-left, lifted clear of the portrait action row (Zone B), whose buttons reach
        // far enough left to collide with the dimmer slider; in landscape the actions are a
        // right-edge column, so the corner itself is free.
        if (arUi != null) {
            ChromeZone(visible, ChromeEdge.BOTTOM, Modifier.align(Alignment.BottomStart)) {
                ArControls(
                    state = arUi,
                    specs = arSpecs,
                    onScrimChange = onArScrimChange,
                    onExposureChange = onArExposureChange,
                    onIsoFractionChange = onArIsoFractionChange,
                    onShutterFractionChange = onArShutterFractionChange,
                    modifier =
                        Modifier.padding(
                            start = 8.dp,
                            bottom = if (landscape) 8.dp else 68.dp,
                        ),
                )
            }
            // "Photograph the moment" is most compelling while the camera is live
            // (camera-ar-mode.md slice 4): a transient shutter, lifted clear of the
            // portrait action row like the controls panel.
            if (shareEnabled) {
                ChromeZone(visible, ChromeEdge.BOTTOM, Modifier.align(Alignment.BottomCenter)) {
                    ShareShutter(
                        onClick = onShareShutter,
                        modifier = Modifier.padding(bottom = if (landscape) 8.dp else 68.dp),
                    )
                }
            }
        }
        // In landscape the cluster is a right-edge column, so it leaves sideways; in portrait
        // it is a bottom row and drops through the bottom edge.
        ChromeZone(
            visible,
            if (landscape) ChromeEdge.END else ChromeEdge.BOTTOM,
            Modifier.align(Alignment.BottomEnd),
        ) {
            ActionCluster(
                landscape = landscape,
                nightMode = nightMode,
                referenceFrame = referenceFrame,
                sensorsAvailable = sensorsAvailable,
                onToggleReferenceFrame = onToggleReferenceFrame,
                onToggleNightMode = onToggleNightMode,
                onOpenSearch = onOpenSearch,
                onOpenTimeTravel = onOpenTimeTravel,
                onOpenOverflow = onOpenOverflow,
                tourTargetModifier = tourTargetModifier,
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
            )
        }
    }
}

/** Which screen edge a chrome zone is anchored to, and therefore slides out toward. */
private enum class ChromeEdge { START, END, TOP, BOTTOM }

/**
 * One chrome zone's show/hide transition: a slide toward [edge] paired with a fade. The slide
 * is the load-bearing part (it shows where the controls went); the fade keeps the zone from
 * visibly clipping against the map as it crosses the edge.
 *
 * Asymmetric by design — leaving is slower than arriving. The exit is the moment that has to
 * teach, so it is given time to be read; the entry is a response to a tap and should feel
 * immediate.
 */
@Composable
private fun ChromeZone(
    visible: Boolean,
    edge: ChromeEdge,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Full travel: each zone hugs its edge, so its own width/height clears the screen.
    val enterSpec = tween<IntOffset>(CHROME_ENTER_MS)
    val exitSpec = tween<IntOffset>(CHROME_ZONE_EXIT_MS)
    // START/END are layout-relative and mirror under RTL (the rail sits on the right in
    // Arabic and Persian, which v2 ships), but these offsets are raw pixels and do not. Without
    // the flip a "start" zone would slide inward toward the middle of the screen instead of
    // off its own edge.
    val towardStart = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1 else -1
    val towardEnd = -towardStart
    val enter =
        when (edge) {
            ChromeEdge.START -> slideInHorizontally(enterSpec) { towardStart * it }
            ChromeEdge.END -> slideInHorizontally(enterSpec) { towardEnd * it }
            ChromeEdge.TOP -> slideInVertically(enterSpec) { -it }
            ChromeEdge.BOTTOM -> slideInVertically(enterSpec) { it }
        } + fadeIn(tween(CHROME_ENTER_MS))
    val exit =
        when (edge) {
            ChromeEdge.START -> slideOutHorizontally(exitSpec) { towardStart * it }
            ChromeEdge.END -> slideOutHorizontally(exitSpec) { towardEnd * it }
            ChromeEdge.TOP -> slideOutVertically(exitSpec) { -it }
            ChromeEdge.BOTTOM -> slideOutVertically(exitSpec) { it }
        } + fadeOut(tween(CHROME_ZONE_EXIT_MS))
    AnimatedVisibility(visible = visible, enter = enter, exit = exit, modifier = modifier) {
        content()
    }
}

/** Quick enough to feel like a direct response to the tap that asked for the chrome. */
private const val CHROME_ENTER_MS = 220

/**
 * Slow enough that the eye can follow each zone to the edge it parks at. `MapScreen` holds the
 * chrome in composition for exactly this long so the slide-out can finish before the content
 * is dropped, hence internal rather than private.
 */
internal const val CHROME_ZONE_EXIT_MS = 420

/**
 * Zone A: a slim vertical column of layer toggles in a translucent pill where v1's sliding
 * sidebar sat. State is shown v1-style by tint (checked = primary + faint pill; unchecked =
 * outline grey), and the expand button at the foot opens the Layers sheet — long-press on
 * any item is the bonus gesture for the same thing.
 *
 * Rail membership borrows the action-bar mechanism (D56): `always` items and the expand
 * button are unconditional; `ifroom` items drop from the end of the rail — never from the
 * Layers sheet — when 48 dp slots run out (landscape height is the binding constraint).
 * Layers in neither list (ecliptic today; new layers by default) are sheet-only.
 */
@Composable
private fun LayerRail(
    toggles: List<LayerToggle>,
    nightMode: Boolean,
    onToggleLayer: (LayerId, Boolean) -> Unit,
    onOpenLayersSheet: () -> Unit,
    tourTargetModifier: (ChromeTourTarget) -> Modifier,
    railLabels: RailLabelState?,
    modifier: Modifier = Modifier,
) {
    val byId = toggles.associateBy { it.id }
    val always = RAIL_ALWAYS_IDS.mapNotNull { byId[it] }
    val ifroom = RAIL_IFROOM_IDS.mapNotNull { byId[it] }
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        // Slots: the always toggles + the expand button + the divider above it are fixed;
        // ifroom toggles (and the divider setting them off) take whatever height remains.
        val fixed = RAIL_SLOT * (always.size + 1) + RAIL_DIVIDER_BLOCK + RAIL_CONTAINER_PAD
        var ifroomShown = 0
        for (n in 1..ifroom.size) {
            if (fixed + RAIL_DIVIDER_BLOCK + RAIL_SLOT * n <= maxHeight) ifroomShown = n
        }
        val shownIfroom = ifroom.take(ifroomShown)
        val outline = MaterialTheme.colorScheme.outline
        // While the teaching labels are up the expand button joins them in primary, then
        // settles back to outline grey on the labels' own fade curve. Grey is this rail's
        // "layer off" tint (see RailItem), so on the runs that teach the rail it miscodes the
        // one item that isn't a toggle as a switched-off one — exactly when a newcomer is
        // reading the column to learn what the tints mean.
        // The expand button borrows the action cluster's glyph colour (D90): it opens a sheet
        // rather than switching a layer, so it wears neither the rail's amber "on" nor its grey
        // "off". Both of those are toggle states, and this is not a toggle.
        val expandTint = MaterialTheme.colorScheme.onSecondaryContainer
        // The labels ride *beside* the rail rather than inside it: the pill keeps its
        // icon-only width and slot arithmetic, so turning labels off again is a pure
        // deletion with no layout consequences. Row + the label column's zero-width
        // contribution when hidden keeps the rail pinned to the same x either way.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        // The dividers fill the column's width; without an intrinsic-width
                        // bound they'd inflate the rail to the screen's width instead.
                        .width(IntrinsicSize.Max)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                        .border(1.dp, outline.copy(alpha = 0.3f), CircleShape)
                        .padding(vertical = 6.dp, horizontal = 2.dp),
            ) {
                for (toggle in always) {
                    RailToggle(
                        toggle,
                        nightMode,
                        onToggleLayer,
                        tourTargetModifier,
                        onOpenLayersSheet,
                    )
                }
                if (shownIfroom.isNotEmpty()) {
                    RailDivider()
                    for (toggle in shownIfroom) {
                        RailToggle(
                            toggle,
                            nightMode,
                            onToggleLayer,
                            tourTargetModifier,
                            onOpenLayersSheet,
                        )
                    }
                }
                RailDivider()
                RailItem(
                    icon = R.drawable.ic_layers,
                    contentDescription = stringResource(R.string.show_layers),
                    checked = null,
                    onClick = onOpenLayersSheet,
                    onLongClick = null,
                    modifier = tourTargetModifier(ChromeTourTarget.LayersExpand),
                    tint = expandTint,
                )
            }
            if (railLabels?.visible == true) {
                RailLabels(
                    always = always,
                    ifroom = shownIfroom,
                    fadingOut = railLabels.fadingOut,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * The floating name column beside the rail: one label per rail slot, at the same 48 dp
 * pitch so each sits on its icon's centre line, plus the dividers' spacing and a final
 * "Layers" for the expand button. Drawn over the sky rather than inside the rail pill —
 * see [LayerRail] — so it costs no permanent chrome width.
 *
 * On the last of the [MapViewModel.RAIL_LABEL_REVEALS] teaching reveals, [fadingOut] starts
 * dissolving them the moment the chrome arrives, so the user watches the labels retire
 * instead of finding them mysteriously absent next time. The fade begins immediately rather
 * than after a hold: by the third reveal the names have been read twice, and a long
 * full-opacity pause before an inevitable fade just delays the chrome settling.
 *
 * Purely decorative: the icons themselves already carry the same names as content
 * descriptions, so labelling these too would make TalkBack announce everything twice.
 */
@Composable
private fun RailLabels(
    always: List<LayerToggle>,
    ifroom: List<LayerToggle>,
    fadingOut: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = teachingFade(fadingOut)
    Column(
        modifier = modifier.graphicsLayer { this.alpha = alpha }.clearAndSetSemantics {},
        horizontalAlignment = Alignment.Start,
    ) {
        for (toggle in always) {
            RailLabel(layerName(toggle.id), toggle.enabled)
        }
        if (ifroom.isNotEmpty()) {
            Spacer(Modifier.height(RAIL_DIVIDER_BLOCK))
            for (toggle in ifroom) {
                RailLabel(layerName(toggle.id), toggle.enabled)
            }
        }
        Spacer(Modifier.height(RAIL_DIVIDER_BLOCK))
        // Matching its icon (D90) — and, unlike the grey it used to wear, above the AA
        // contrast threshold, which `outline` is deliberately below.
        RailLabel(
            R.string.show_layers,
            enabled = true,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * 1f while the teaching labels are up, dissolving to 0f over [RAIL_LABEL_FADE_MS] on the
 * farewell reveal.
 *
 * An explicit Animatable seeded at 1f, *not* `animateFloatAsState(if (fadingOut) 0f else 1f)`:
 * that helper initialises at its target on first composition, so on the farewell reveal alpha
 * would start at 0f and the labels would simply be absent — which is exactly what the bug
 * looked like.
 *
 * `withFrameNanos` before animating matters for the same reason. The host only composes the
 * caller after latching a non-null reveal state, so its *first* composition already carries
 * fadingOut = true; starting the animation in that same frame means the labels are never
 * painted at full opacity, and a 3 s dissolve that begins before the first frame still reads as
 * "they just aren't there". Waiting one frame guarantees a full-opacity paint to dissolve
 * *from*.
 */
@Composable
private fun teachingFade(fadingOut: Boolean): Float {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(fadingOut) {
        if (fadingOut) {
            withFrameNanos { }
            alpha.animateTo(0f, tween(RAIL_LABEL_FADE_MS))
        }
    }
    return alpha.value
}

/**
 * One rail name, boxed to [RAIL_SLOT] so the column stays in lockstep with the icons
 * regardless of font scale. Tracks its toggle's tint like the icon does, and carries a
 * faint scrim so the text survives a bright Milky Way behind it.
 */
@Composable
private fun RailLabel(
    @StringRes label: Int,
    enabled: Boolean,
    tint: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.height(RAIL_SLOT),
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = tint ?: if (enabled) colors.primary else colors.outline,
            modifier =
                Modifier
                    .clip(RailLabelShape)
                    .background(colors.surface.copy(alpha = 0.62f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RailToggle(
    toggle: LayerToggle,
    nightMode: Boolean,
    onToggleLayer: (LayerId, Boolean) -> Unit,
    tourTargetModifier: (ChromeTourTarget) -> Modifier,
    onLongClick: () -> Unit,
) {
    RailItem(
        icon = layerIcon(toggle.id),
        contentDescription = stringResource(layerName(toggle.id)),
        checked = toggle.enabled,
        onClick = { onToggleLayer(toggle.id, !toggle.enabled) },
        onLongClick = onLongClick,
        modifier = tourTargetModifier(ChromeTourTarget.Layer(toggle.id)),
        dataStatus = toggle.dataStatus,
        nightMode = nightMode,
    )
}

@Composable
private fun RailDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * One rail slot: the 48 dp minimum touch target (M3/accessibility baseline) around a ~36 dp
 * visual pill, so the rail still reads slim (D56). A hand-rolled `IconToggleButton`: the M3
 * component has no long-press slot, and long-press-to-open-the-Layers-sheet is part of the
 * agreed design.
 *
 * [tint] overrides the checked/unchecked icon colour; the expand button uses it to sit outside
 * the toggle vocabulary altogether (D90).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailItem(
    @DrawableRes icon: Int,
    contentDescription: String,
    checked: Boolean?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    dataStatus: LayerDataStatus? = null,
    nightMode: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .semantics {
                    if (checked != null) {
                        role = Role.Checkbox
                        toggleableState = ToggleableState(checked)
                    } else {
                        role = Role.Button
                    }
                },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked == true) {
                            colors.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
                    ),
        ) {
            Icon(
                painterResource(icon),
                contentDescription,
                tint = tint ?: if (checked == true) colors.primary else colors.outline,
                modifier = Modifier.size(22.dp),
            )
            // A corner dot, deliberately *beside* the glyph rather than altering it: the shape
            // answers "is this layer on?" and the badge answers "is its data any good?", and
            // overloading one icon with both makes neither readable (iconography bench, panel C).
            dataStatus?.let { status ->
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dataStatusColor(status, nightMode)),
                )
            }
        }
    }
}

/** Maps a layer's data health onto the two-tier status palette (AGENTS.md). */
private fun dataStatusColor(
    status: LayerDataStatus,
    nightMode: Boolean,
): Color {
    val colors = statusColors(nightMode)
    return when (status) {
        LayerDataStatus.AGEING -> colors.ok
        LayerDataStatus.STALE -> colors.warning
        LayerDataStatus.ABSENT -> colors.absent
    }
}

/**
 * Zone B: the four actions used while looking at the sky, plus overflow, in a deliberate
 * three-step hierarchy — Search is the sole filled-primary button (the marquee action),
 * Time travel / Night / Auto-Manual are tonal, and ⋮ is bare. In landscape the cluster is a
 * bottom-anchored column on the right edge (roughly v1's right panel position).
 */
@Composable
private fun ActionCluster(
    landscape: Boolean,
    nightMode: Boolean,
    referenceFrame: ReferenceFrame,
    sensorsAvailable: Boolean,
    onToggleReferenceFrame: () -> Unit,
    onToggleNightMode: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTimeTravel: () -> Unit,
    onOpenOverflow: () -> Unit,
    tourTargetModifier: (ChromeTourTarget) -> Modifier,
    modifier: Modifier = Modifier,
) {
    val buttons: @Composable () -> Unit = {
        ActionTooltip(stringResource(R.string.search_button)) { description ->
            FilledIconButton(
                onClick = onOpenSearch,
                modifier = tourTargetModifier(ChromeTourTarget.Search),
            ) {
                Icon(painterResource(R.drawable.ic_search), description)
            }
        }
        ActionTooltip(stringResource(R.string.time_travel_button)) { description ->
            FilledTonalIconButton(
                onClick = onOpenTimeTravel,
                modifier = tourTargetModifier(ChromeTourTarget.TimeTravel),
            ) {
                Icon(painterResource(R.drawable.ic_time_travel), description)
            }
        }
        ActionTooltip(stringResource(R.string.night_mode)) { description ->
            FilledTonalIconButton(
                onClick = onToggleNightMode,
                modifier = tourTargetModifier(ChromeTourTarget.NightMode),
            ) {
                Icon(
                    painterResource(if (nightMode) R.drawable.ic_sun else R.drawable.ic_moon),
                    description,
                )
            }
        }
        if (sensorsAvailable) {
            // Destination convention, like the night-mode button above: the glyph and label
            // describe the mode a tap switches INTO, not the mode the map is already in.
            val label =
                stringResource(
                    when (referenceFrame) {
                        ReferenceFrame.SENSOR -> R.string.switch_to_manual
                        ReferenceFrame.MANUAL -> R.string.switch_to_auto
                    },
                )
            ActionTooltip(label) { description ->
                FilledTonalIconButton(
                    onClick = onToggleReferenceFrame,
                    modifier = tourTargetModifier(ChromeTourTarget.AutoManual),
                ) {
                    Icon(
                        painterResource(
                            when (referenceFrame) {
                                ReferenceFrame.SENSOR -> R.drawable.ic_hand
                                ReferenceFrame.MANUAL -> R.drawable.ic_compass
                            },
                        ),
                        description,
                    )
                }
            }
        }
        // Tonal, not bare: ⋮ is the only way to reach Help, Settings and seven other
        // destinations, and as an unfilled glyph on the starfield it read as decoration and
        // went unfound (Hannah's feedback, 2026-08). Search keeps the sole filled-primary
        // slot, so promoting this to match its three neighbours costs no hierarchy.
        ActionTooltip(stringResource(R.string.more_button)) { description ->
            FilledTonalIconButton(
                onClick = onOpenOverflow,
                modifier = tourTargetModifier(ChromeTourTarget.Overflow),
            ) {
                Icon(painterResource(R.drawable.ic_more_vert), description)
            }
        }
    }
    if (landscape) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) { buttons() }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) { buttons() }
    }
}

/** The in-AR share shutter: a camera-style ring, tinted through the theme for night. */
@Composable
private fun ShareShutter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.onSurface
    val shutterDescription = stringResource(R.string.share_shutter_button)
    Box(
        modifier =
            modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ring.copy(alpha = 0.18f))
                .border(3.dp, ring.copy(alpha = 0.85f), CircleShape)
                .clickable(onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = shutterDescription
                },
    )
}

/** M3 tooltip carrying the label of an icon-only action button (D56 implementation notes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTooltip(
    label: String,
    content: @Composable (contentDescription: String) -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content(label)
    }
}

/**
 * Zone C: the ⋮ overflow sheet — destinations and one-shot actions, not sky-drawing
 * controls, so they don't earn permanent pixels. Sibling of [LayersSheet]: this sheet
 * navigates away from the map; the Layers sheet configures what it draws.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowSheet(
    onShareSky: () -> Unit,
    // Sharing is behind the SHARE_SKY experiment; off, the row simply isn't offered.
    shareEnabled: Boolean = true,
    onOpenGallery: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            // Ordered by when a user needs them, not by how the app is built: Help and
            // Tutorial were 7th and 6th of nine and fell below the fold on a short phone,
            // which is precisely where a lost newcomer stops scrolling (Hannah's feedback,
            // 2026-08). Diagnostics has left for Settings → Advanced entirely.
            OverflowRow(R.drawable.ic_help, R.string.help_button, onOpenHelp)
            OverflowRow(R.drawable.ic_tutorial, R.string.tutorial_button, onOpenTutorial)
            OverflowRow(R.drawable.ic_settings, R.string.settings_button, onOpenSettings)
            OverflowRow(R.drawable.ic_location, R.string.location_button, onOpenLocation)
            OverflowRow(R.drawable.ic_gallery, R.string.gallery_button, onOpenGallery)
            if (shareEnabled) {
                OverflowRow(R.drawable.ic_share, R.string.share_button, onShareSky)
            }
            OverflowRow(R.drawable.ic_whats_new, R.string.whats_new_button, onOpenWhatsNew)
            OverflowRow(R.drawable.ic_calibrate, R.string.calibration_button, onOpenCalibration)
        }
    }
}

@Composable
private fun OverflowRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The Layers sheet: the canonical, unbounded layer list (the rail is its primary-subset
 * shortcut). Same vector icons, labels, and M3 switches, grouped to mirror the rail:
 * object layers, then reference elements, then display state — the sky gradient is a
 * render-state, not a layer, but v1 listed it with the layers and it toggles here too.
 * Toggling a row re-tints the matching rail icon immediately (single source of truth:
 * the layer-visibility preferences behind [LayersViewModel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    layersViewModel: LayersViewModel,
    onDismiss: () -> Unit,
    // Through-camera mode (camera-ar-mode.md/D64): a Display-group mode, not a LayerId.
    // The row hides entirely on camera-less devices — and when the CAMERA_AR experiment is
    // off, which the caller folds into [hasCamera] — and disables without usable sensors.
    arModeOn: Boolean = false,
    hasCamera: Boolean = false,
    sensorsAvailable: Boolean = false,
    onSetArMode: (Boolean) -> Unit = {},
) {
    val toggles by layersViewModel.toggles.collectAsStateWithLifecycle()
    val parameters by layersViewModel.parameters.collectAsStateWithLifecycle()
    val skyGradientEnabled by layersViewModel.skyGradientEnabled.collectAsStateWithLifecycle()
    val hudEnabled by layersViewModel.hudEnabled.collectAsStateWithLifecycle()
    val satelliteDataMissing by
        layersViewModel.satelliteDataMissing.collectAsStateWithLifecycle()
    val satelliteRefreshWait by
        layersViewModel.satelliteRefreshWait.collectAsStateWithLifecycle()
    // Reference elements are a fixed set; anything else (all object layers, and whatever
    // future layers land) stays in the object group in registry order.
    val (reference, objects) = toggles.partition { it.id in REFERENCE_LAYER_IDS }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Scrollable: in landscape the rows are taller than the sheet.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.layers_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            LayerGroupHeader(R.string.layers_group_objects)
            for (toggle in objects) {
                LayerRow(
                    icon = layerIcon(toggle.id),
                    label = layerName(toggle.id),
                    checked = toggle.enabled,
                    onCheckedChange = { layersViewModel.setEnabled(toggle.id, it) },
                    // D87: a layer that declares parameters gets an inline expander, collapsed
                    // by default, so the sheet reads exactly as before until you go looking.
                    parameters = parameters.filter { it.id == toggle.id },
                    onParameterChange = { key, option ->
                        layersViewModel.setParameter(toggle.id, key, option)
                    },
                )
                if (toggle.id == SatelliteLayer.LAYER_ID && satelliteDataMissing) {
                    SatelliteEmptyStateCard(
                        refreshWait = satelliteRefreshWait,
                        onRefresh = layersViewModel::refreshSatelliteData,
                    )
                }
            }
            LayerGroupHeader(R.string.layers_group_reference)
            for (toggle in reference) {
                LayerRow(
                    icon = layerIcon(toggle.id),
                    label = layerName(toggle.id),
                    checked = toggle.enabled,
                    onCheckedChange = { layersViewModel.setEnabled(toggle.id, it) },
                )
            }
            LayerGroupHeader(R.string.layers_group_display)
            LayerRow(
                icon = R.drawable.ic_layer_sky_gradient,
                label = R.string.layer_sky_gradient,
                checked = skyGradientEnabled,
                onCheckedChange = { layersViewModel.setSkyGradientEnabled(it) },
            )
            LayerRow(
                icon = R.drawable.ic_layer_hud,
                label = R.string.layer_hud,
                checked = hudEnabled,
                onCheckedChange = { layersViewModel.setHudEnabled(it) },
            )
            if (hasCamera) {
                LayerRow(
                    icon = R.drawable.ic_layer_camera,
                    label = R.string.layer_camera,
                    checked = arModeOn && sensorsAvailable,
                    onCheckedChange = onSetArMode,
                    enabled = sensorsAvailable,
                    subtitle =
                        if (sensorsAvailable) null else R.string.layer_camera_needs_sensors,
                )
            }
        }
    }
}

@Composable
private fun LayerGroupHeader(
    @StringRes label: Int,
) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun LayerRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    @StringRes subtitle: Int? = null,
    parameters: List<LayerParameterState> = emptyList(),
    onParameterChange: (String, String) -> Unit = { _, _ -> },
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Column {
        LayerRowContent(
            icon = icon,
            label = label,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            subtitle = subtitle,
            expandable = parameters.isNotEmpty(),
            expanded = expanded,
            onExpandToggle = { expanded = !expanded },
        )
        if (expanded) {
            for (state in parameters) {
                LayerParameterChooser(
                    state = state,
                    onSelect = { onParameterChange(state.parameter.key, it) },
                )
            }
        }
    }
}

/**
 * The chooser a parameter expands into: one segmented row of options, with the selected option's
 * description beneath it, so the difference between "true scale" and "glyphs" is explained where
 * the choice is made rather than in help text nobody opens.
 */
@Composable
private fun LayerParameterChooser(
    state: LayerParameterState,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(start = 40.dp, top = 4.dp, bottom = 12.dp)) {
        when (val parameter = state.parameter) {
            is LayerParameter.Choice -> {
                Text(
                    stringResource(parameterLabel(parameter.key)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    parameter.options.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == state.selected,
                            onClick = { onSelect(option) },
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = parameter.options.size,
                                ),
                        ) {
                            Text(stringResource(parameterOptionLabel(option)))
                        }
                    }
                }
                Text(
                    stringResource(parameterOptionDescription(state.selected)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            // A switch, not a two-option segmented row: a boolean rendered as a radio pair reads
            // as a choice between two things rather than as something you turn on.
            is LayerParameter.Toggle -> {
                val context = LocalContext.current
                var pendingRevert by remember { mutableStateOf(false) }
                val permissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        // A denial flips the switch back rather than leaving it on and silently
                        // never notifying — the same contract the Settings notification rows use.
                        if (!granted && pendingRevert) onSelect(false.toString())
                        pendingRevert = false
                    }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(parameterLabel(parameter.key)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            stringResource(parameterDescription(parameter.key)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.selected.toBoolean(),
                        onCheckedChange = { enabled ->
                            onSelect(enabled.toString())
                            // Any toggle that leads to a notification has to ask, and this one is
                            // the first outside Settings → Notifications. Without it the switch
                            // reads as on while POST_NOTIFICATIONS is ungranted and the alert
                            // never arrives — a silent failure with no way for the user to
                            // diagnose it.
                            if (enabled &&
                                parameter.requiresNotificationPermission &&
                                Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingRevert = true
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerRowContent(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    @StringRes subtitle: Int?,
    expandable: Boolean,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
) {
    val contentColor =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    enabled = enabled,
                    onValueChange = onCheckedChange,
                )
                .padding(vertical = 8.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = contentColor,
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
            )
            if (subtitle != null) {
                Text(
                    stringResource(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expandable) {
            // Its own click target inside the toggleable row: tapping the chevron opens the
            // options, tapping anywhere else still toggles the layer.
            IconButton(onClick = onExpandToggle) {
                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription = stringResource(R.string.layer_param_expand, ""),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Layer-parameter keys → their sheet labels; the same split as [layerName]. */
@StringRes
private fun parameterLabel(key: String): Int =
    when (key) {
        LayerParameter.DISC_SIZE -> R.string.layer_param_disc_size
        LayerParameter.PASS_ALERTS -> R.string.layer_param_pass_alerts
        LayerParameter.ECLIPSE_ALERTS -> R.string.layer_param_eclipse_alerts
        else -> R.string.layer_param_disc_size
    }

/** The one-line explanation under a [LayerParameter.Toggle]'s switch. */
@StringRes
private fun parameterDescription(key: String): Int =
    when (key) {
        LayerParameter.PASS_ALERTS -> R.string.layer_param_pass_alerts_description
        LayerParameter.ECLIPSE_ALERTS -> R.string.layer_param_eclipse_alerts_description
        else -> R.string.layer_param_pass_alerts_description
    }

@StringRes
private fun parameterOptionLabel(option: String): Int =
    when (option) {
        LayerParameter.DISC_SIZE_TRUE -> R.string.layer_param_disc_size_true
        LayerParameter.DISC_SIZE_GLYPHS -> R.string.layer_param_disc_size_glyphs
        else -> R.string.layer_param_disc_size_auto
    }

@StringRes
private fun parameterOptionDescription(option: String): Int =
    when (option) {
        LayerParameter.DISC_SIZE_TRUE -> R.string.layer_param_disc_size_true_desc
        LayerParameter.DISC_SIZE_GLYPHS -> R.string.layer_param_disc_size_glyphs_desc
        else -> R.string.layer_param_disc_size_auto_desc
    }

/** Core object layers — `always` in the rail (D56). */
private val RAIL_ALWAYS_IDS: List<LayerId> =
    listOf(
        CatalogLayers.STARS_LAYER_ID,
        CatalogLayers.CONSTELLATIONS_LAYER_ID,
        CatalogLayers.DEEP_SKY_LAYER_ID,
        SolarSystemLayer.LAYER_ID,
    )

/** `ifroom` rail members, dropped from the end of the rail when height runs out (D56/D58). */
private val RAIL_IFROOM_IDS: List<LayerId> =
    listOf(
        MeteorShowerLayer.LAYER_ID,
        // After meteor showers so an install without the satellite experiment loses nothing from
        // the rail's order, and before the reference layers because it is an object layer.
        SatelliteLayer.LAYER_ID,
        GridLayer.LAYER_ID,
        HorizonLayer.LAYER_ID,
    )

/** The non-object reference elements: the Layers sheet's second group. */
private val REFERENCE_LAYER_IDS: Set<LayerId> =
    setOf(
        GridLayer.LAYER_ID,
        HorizonLayer.LAYER_ID,
        EclipticLayer.LAYER_ID,
    )

/** One rail slot: the 48 dp minimum touch target. */
private val RAIL_SLOT: Dp = 48.dp

/** A rail divider's height contribution: 1 dp hairline + 4 dp margin either side. */
private val RAIL_DIVIDER_BLOCK: Dp = 9.dp

/** The rail pill's own vertical padding. */
private val RAIL_CONTAINER_PAD: Dp = 12.dp

/**
 * The rail labels' scrim: a soft rectangle rather than the rail's own [CircleShape], whose
 * fully-round ends would eat the first and last character of a word.
 */
private val RailLabelShape = RoundedCornerShape(6.dp)

/**
 * The farewell dissolve, which begins the moment the chrome arrives on the last teaching
 * reveal. Slow on purpose: this is the one moment the labels are *saying* something by
 * leaving, and a quick fade reads as a glitch rather than as a farewell. It carries the whole
 * gesture now that no hold precedes it, so the labels stay legible for the first second or so
 * of it and a glance at the rail catches the change happening.
 */
private const val RAIL_LABEL_FADE_MS = 3000

@StringRes
private fun layerName(id: LayerId): Int =
    when (id) {
        CatalogLayers.STARS_LAYER_ID -> R.string.layer_stars
        CatalogLayers.CONSTELLATIONS_LAYER_ID -> R.string.layer_constellations
        CatalogLayers.DEEP_SKY_LAYER_ID -> R.string.layer_deep_sky
        SolarSystemLayer.LAYER_ID -> R.string.layer_solar_system
        MeteorShowerLayer.LAYER_ID -> R.string.layer_meteor_showers
        SatelliteLayer.LAYER_ID -> R.string.layer_satellites
        GridLayer.LAYER_ID -> R.string.layer_grid
        HorizonLayer.LAYER_ID -> R.string.layer_horizon
        EclipticLayer.LAYER_ID -> R.string.layer_ecliptic
        else -> error("No display name for layer ${id.id}")
    }

/** The warm-welcome tour's stops, in presentation order (rail top-to-bottom, then actions). */
val CHROME_TOUR_STOPS: List<ChromeTourTarget> by lazy {
    (RAIL_ALWAYS_IDS + RAIL_IFROOM_IDS).map { ChromeTourTarget.Layer(it) } +
        listOf(
            ChromeTourTarget.LayersExpand,
            ChromeTourTarget.Search,
            ChromeTourTarget.TimeTravel,
            ChromeTourTarget.NightMode,
            ChromeTourTarget.AutoManual,
            ChromeTourTarget.Overflow,
        )
}

/** The label the warm-welcome tour shows while spotlighting [target]. */
@StringRes
fun chromeTourLabel(target: ChromeTourTarget): Int =
    when (target) {
        is ChromeTourTarget.Layer -> layerName(target.id)
        ChromeTourTarget.LayersExpand -> R.string.show_layers
        ChromeTourTarget.Search -> R.string.search_button
        ChromeTourTarget.TimeTravel -> R.string.time_travel_button
        ChromeTourTarget.NightMode -> R.string.night_mode
        // The tour's canned chrome is in the sensor frame, so the button shows the hand.
        ChromeTourTarget.AutoManual -> R.string.switch_to_manual
        ChromeTourTarget.Overflow -> R.string.more_button
    }

/** Every-rail-layer-on toggle state for the warm-welcome tour's non-interactive chrome. */
fun demoChromeToggles(): List<LayerToggle> =
    (RAIL_ALWAYS_IDS + RAIL_IFROOM_IDS).map { LayerToggle(it, enabled = true) }

@DrawableRes
private fun layerIcon(id: LayerId): Int =
    when (id) {
        CatalogLayers.STARS_LAYER_ID -> R.drawable.ic_layer_stars
        CatalogLayers.CONSTELLATIONS_LAYER_ID -> R.drawable.ic_layer_constellations
        CatalogLayers.DEEP_SKY_LAYER_ID -> R.drawable.ic_layer_deep_sky
        SolarSystemLayer.LAYER_ID -> R.drawable.ic_layer_solar_system
        MeteorShowerLayer.LAYER_ID -> R.drawable.ic_layer_meteor_showers
        SatelliteLayer.LAYER_ID -> R.drawable.ic_layer_satellites
        GridLayer.LAYER_ID -> R.drawable.ic_layer_grid
        HorizonLayer.LAYER_ID -> R.drawable.ic_layer_horizon
        EclipticLayer.LAYER_ID -> R.drawable.ic_layer_ecliptic
        else -> error("No icon for layer ${id.id}")
    }

/**
 * Shown under the satellite row when the layer is on but nothing has been downloaded yet.
 *
 * **A card, not a missing layer** (iconography bench, panel C). Silently drawing nothing would be
 * indistinguishable from "there is nothing up there", which is both wrong and unfixable by the
 * user; saying why, and offering the fix, is the honest version.
 *
 * The Refresh button **respects the circuit breaker and the minimum query interval** — it calls
 * the ordinary `refresh()`, not the debug force path. Two affordances, two contracts: this one is
 * reachable by every user, so it must never be the one that can hammer CelesTrak.
 *
 * And when policy would refuse, the button is **replaced by the wait** rather than shown and
 * quietly doing nothing. A button that does nothing reads as a broken app; the real reason is a
 * rate limit that is nobody's fault, and saying roughly how long is both honest and actionable.
 * The wording is bucketed rather than a countdown — the difference that matters is "shortly"
 * versus "tomorrow" versus "in a few days", which is exactly the difference between the
 * two-hour minimum interval and an open circuit breaker.
 */
@Composable
private fun SatelliteEmptyStateCard(
    refreshWait: Duration,
    onRefresh: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.satellites_no_data),
                style = MaterialTheme.typography.bodySmall,
            )
            if (refreshWait == Duration.ZERO) {
                TextButton(
                    onClick = onRefresh,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.satellites_refresh))
                }
            } else {
                Text(
                    stringResource(retryWaitText(refreshWait)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp, end = 12.dp),
                )
            }
        }
    }
}

/**
 * Buckets a wait into a phrase, avoiding both a live countdown and plural grammar.
 *
 * The bands line up with the two things that actually cause a wait: CelesTrak's two-hour minimum
 * query interval, and the circuit breaker's 24 h / 48 h / 7 day ladder.
 */
@StringRes
private fun retryWaitText(wait: Duration): Int =
    when {
        wait <= 5.minutes -> R.string.satellites_retry_soon
        wait <= 90.minutes -> R.string.satellites_retry_within_hour
        wait <= 20.hours -> R.string.satellites_retry_hours
        wait <= 36.hours -> R.string.satellites_retry_tomorrow
        else -> R.string.satellites_retry_days
    }
