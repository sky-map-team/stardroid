# Material 3 Theme System

## Purpose

Defines the **Material Design 3 theme system** for Stardroid, including dynamic color (Material You), night mode handling, and component styling.

## Design Goals

1. **Astronomy-First Dark Mode** - The app is used at night, dark backgrounds are essential
2. **Material You Integration** - Accent colors adapt to user's wallpaper
3. **Night Vision Preservation** - Red tint mode for outdoor stargazing
4. **High Outdoor Visibility** - Large touch targets, high contrast text

## Theme Structure

### Base Theme

```xml
<!-- res/values/themes.xml -->
<resources>
    <style name="Theme.Stardroid" parent="Theme.Material3.DayNight.NoActionBar">
        <!-- Primary colors (accent for us, since background is dark) -->
        <item name="colorPrimary">@color/dynamic_primary</item>
        <item name="colorPrimaryDark">@color/dynamic_primary_dark</item>
        <item name="colorAccent">@color/dynamic_accent</item>

        <!-- Background colors -->
        <item name="android:colorBackground">@color/background_dark</item>
        <item name="colorSurface">@color/surface_dark</item>

        <!-- Status bar -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">false</item>

        <!-- Text colors -->
        <item name="android:textColorPrimary">@color/text_primary</item>
        <item name="android:textColorSecondary">@color/text_secondary</item>
    </style>

    <style name="Theme.Stardroid.Splash" parent="Theme.Stardroid">
        <item name="android:windowBackground">@color/background_black</item>
        <item name="android:windowFullscreen">true</item>
    </style>

    <!-- Night Vision Mode (Red tint) -->
    <style name="Theme.Stardroid.NightVision" parent="Theme.Stardroid">
        <item name="android:colorBackground">@color/background_red_tint</item>
        <item name="colorPrimary">@color/accent_red</item>
        <item name="colorAccent">@color/accent_red</item>
    </style>
</resources>
```

### Color Values

```xml
<!-- res/values/colors.xml -->
<resources>
    <!-- Dark background colors (for astronomy) -->
    <color name="background_black">#000000</color>
    <color name="background_dark">#121212</color>
    <color name="background_red_tint">#1A0505</color>

    <!-- Surface colors -->
    <color name="surface_dark">#1E1E1E</color>
    <color name="surface_variant">#2C2C2C</color>

    <!-- Text colors (high contrast) -->
    <color name="text_primary">#E0E0E0</color>
    <color name="text_secondary">#B0B0B0</color>
    <color name="text_hint">#808080</color>

    <!-- Dynamic color fallbacks (used when Material You unavailable) -->
    <color name="dynamic_primary">#BB86FC</color>      <!-- Light purple -->
    <color name="dynamic_primary_dark">#3700B3</color>   <!-- Deep purple -->
    <color name="dynamic_accent">#03DAC6</color>       <!-- Teal -->
    <color name="accent_red">#FF5252</color>            <!-- Red for night mode -->

    <!-- Legacy colors -->
    <color name="accent_blue">#2196F3</color>
    <color name="accent_orange">#FF9800</color>
</resources>
```

## Dynamic Color (Material You)

### Purpose

Extract accent colors from user's wallpaper to make the app feel personalized while maintaining the dark background required for astronomy.

### Implementation

```kotlin
// com/google/android/stardroid/ui/DynamicColorExtractor.kt
object DynamicColorExtractor {
    fun applyDynamicColors(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+ (Material You)
            applyDynamicColorsModern(activity)
        } else {
            // Fallback to predefined colors
            applyFallbackColors(activity)
        }
    }

    @RequiresApi(31)
    private fun applyDynamicColorsModern(activity: Activity) {
        val dynamicColors = DynamicColors.Builder()
            .setColorScheme {
                // Use user's wallpaper colors
                setColorScheme(activity, ColorScheme.Builder()
                    .setTheme(Theme.DARK)
                    .build())
            }
            .build()

        // Apply to activity's theme
        dynamicColors.applyToActivityIfAvailable(activity)
    }

    private fun applyFallbackColors(activity: Activity) {
        // Use predefined purple/teal scheme
        // Theme.xml already handles this via colorPrimary
    }
}
```

### Color Scheme

**Primary Colors (accents for buttons, highlights):**
- **Dynamic:** Extracted from wallpaper (Android 12+)
- **Fallback:** Purple (#BB86FC) / Teal (#03DAC6)

**Surface Colors (remain dark):**
- Background: Black (#000000) - Essential for night sky
- Surface: Dark gray (#1E1E1E) - For cards, dialogs
- Surface Variant: Medium gray (#2C2C2C) - For elevated surfaces

**Rationale:** The dark background is non-negotiable for astronomy. Dynamic colors personalize the accents while preserving night vision.

## Night Vision Mode

### Purpose

Red-tinted display mode preserves dark adaptation (night vision) while using the app outdoors.

### Implementation

```kotlin
// com/google/android/stardroid/ui/ActivityLightLevelManager.kt
class ActivityLightLevelManager @Inject constructor(...) {

    fun setNightVisionMode(enabled: Boolean) {
        if (enabled) {
            // Apply red tint theme
            delegate.setTheme(R.style.Theme_Stardroid_NightVision)

            // Apply red tint to window
            delegate.window?.decorView?.overlayRedTint(0x30FF0000)
        } else {
            // Revert to normal dark theme
            delegate.setTheme(R.style.Theme_Stardroid)
            delegate.window?.decorView?.overlayRedTint(0x00000000)
        }
    }
}
```

**Visual Effect:**
- Background becomes dark red (#1A0505)
- Text becomes red-tinted white
- Buttons become red accent
- OpenGL surface gets red tint overlay

## Component Styling

### Buttons

```xml
<style name="Widget.Stardroid.Button" parent="Widget.Material3.Button">
    <item name="android:textSize">16sp</item>
    <item name="cornerRadius">8dp</item>
    <item name="android:paddingTop">12dp</item>
    <item name="android:paddingBottom">12dp</item>
    <item name="android:paddingStart">24dp</item>
    <item name="android:paddingEnd">24dp</item>
</style>

<style name="Widget.Stardroid.Button.TextButton" parent="Widget.Material3.Button.TextButton">
    <item name="android:textSize">16sp</item>
    <item name="android:textColor">?attr/colorPrimary</item>
</style>
```

### Bottom Sheets

```xml
<style name="Widget.Stardroid.BottomSheet" parent="Widget.Material3.BottomSheet">
    <item name="shapeAppearanceOverlay">@style/ShapeAppearance.Stardroid.LargeComponent</item>
    <item name="android:backgroundTint">@color/surface_dark</item>
    <item name="android:paddingTop">16dp</item>
    <item name="android:paddingStart">16dp</item>
    <item name="android:paddingEnd">16dp</item>
</style>

<style name="ShapeAppearance.Stardroid.LargeComponent" parent="">
    <item name="cornerSize">16dp</item>
</style>
```

### Cards

```xml
<style name="Widget.Stardroid.Card" parent="Widget.Material3.Card.Elevated">
    <item name="cardBackgroundColor">@color/surface_dark</item>
    <item name="cardCornerRadius">12dp</item>
    <item name="cardElevation">2dp</item>
    <item name="contentPadding">16dp</item>
</style>
```

### Typography

```xml
<style name="TextAppearance.Stardroid.Headline" parent="TextAppearance.Material3.HeadlineSmall">
    <item name="android:textSize">24sp</item>
    <item name="android:textStyle">bold</item>
    <item name="android:textColor">@color/text_primary</item>
</style>

<style name="TextAppearance.Stardroid.Title" parent="TextAppearance.Material3.TitleLarge">
    <item name="android:textSize">20sp</item>
    <item name="android:textStyle">bold</item>
    <item name="android:textColor">@color/text_primary</item>
</style>

<style name="TextAppearance.Stardroid.Body" parent="TextAppearance.Material3.BodyLarge">
    <item name="android:textSize">16sp</item>
    <item name="android:textColor">@color/text_primary</item>
</style>
```

## Splash Screen Theme

### Design

**Astronomy-themed splash with animated starfield**

```xml
<!-- res/layout/splash_material3.xml -->
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_black"
    android:theme="@style/Theme.Stardroid.Splash">

    <!-- Starfield animation -->
    <com.google.android.stardroid.ui.StarfieldView
        android:id="@+id/starfield"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />

    <!-- App logo -->
    <ImageView
        android:id="@+id/logo"
        android:layout_width="120dp"
        android:layout_height="120dp"
        android:src="@drawable/stardroid_big_image"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- Tagline -->
    <TextView
        android:id="@+id/tagline"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/app_tagline"
        android:textAppearance="@style/TextAppearance.Stardroid.Body"
        android:alpha="0"
        app:layout_constraintTop_toBottomOf="@id/logo"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:marginTop="16dp" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### Animation

```kotlin
// StarfieldView.kt - Custom view for star twinkling
class StarfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val stars = mutableListOf<Star>()
    private val random = Random(System.currentTimeMillis())

    init {
        // Generate 100 random stars
        repeat(100) {
            stars.add(Star(
                x = random.nextFloat() * width,
                y = random.nextFloat() * height,
                size = random.nextFloat() * 3 + 1,  // 1-4dp
                baseAlpha = random.nextFloat() * 0.5 + 0.3  // 0.3-0.8
            ))
        }
    }

    fun startTwinkling() {
        // Animate each star's alpha independently
        stars.forEach { star ->
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = random.nextLong(1000, 3000)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { animation ->
                    star.alpha = star.baseAlpha * (animation.animatedValue as Float)
                    invalidate()
                }
            }
            animator.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        stars.forEach { star ->
            paint.alpha = (star.alpha * 255).toInt()
            canvas.drawCircle(star.x, star.y, star.size, paint)
        }
    }

    private data class Star(
        var x: Float, var y: Float, val size: Float,
        val baseAlpha: Float, var alpha: Float = baseAlpha
    )
}
```

## Dialog Theme

### Bottom Sheet Style

**All dialogs use Material 3 BottomSheetDialogFragment:**

- **Peek height:** 250dp (shows header + first paragraph)
- **Full height:** Drag to expand
- **Background:** Dark surface (#1E1E1E)
- **CornerRadius:** 16dp at top
- **Divider:** Material divider color

### Dialog Content

**Title:**
- TextAppearance: HeadlineSmall (24sp, bold)
- Color: text_primary (#E0E0E0)
- Padding: 16dp

**Body:**
- TextAppearance: BodyLarge (16sp)
- Color: text_primary (#E0E0E0)
- Line spacing: 1.5
- Padding: 16dp
- Max height: 70% of screen

**Buttons:**
- Accept: MaterialButton filled, primary color
- Decline: MaterialButton text button, primary color
- Height: 48dp (touch target)
- Padding: 12dp top/bottom, 24dp sides

## Performance Considerations

### Theme Switching Cost

**Applying dynamic colors:** ~50ms one-time on app start
**Night vision toggle:** ~100ms (theme change + tint overlay)

**Optimization:** Apply themes in `onCreate()`, not per-frame

### Memory

**Theme objects:** ~5MB cached
**Color resources:** ~1MB
**Starfield stars:** ~50KB

## Related Specifications

- [activities.md](activities.md) - How activities use themes
- [dialogs.md](dialogs.md) - Dialog-specific styling
- [../features/settings.md](../features/settings.md) - Theme settings UI
