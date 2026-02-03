# Dialogs Specification

## Purpose

Defines all **dialog fragments** in Stardroid, their purpose, UI patterns, and user interactions.

## Dialog Overview

| Dialog | Purpose | Type | Parent | Trigger |
|--------|---------|------|--------|--------|
| `EulaBottomSheetFragment` | Terms of Service | Bottom Sheet | SplashScreenActivity | First launch or EULA update |
| `WhatsNewBottomSheetFragment` | Release notes | Bottom Sheet | SplashScreenActivity | App update |
| `TimeTravelDialogFragment` | Time travel | Modal | DynamicStarMapActivity | Menu/button |
| `NoSearchResultsDialogFragment` | No results | Modal | DynamicStarMapActivity | Search failed |
| `MultipleSearchResultsDialogFragment` | Multiple results | Modal | DynamicStarMapActivity | Search ambiguity |
| `ObjectInfoDialogFragment` | Object details | Modal | DynamicStarMapActivity | Tap on object |
| `NoSensorsDialogFragment` | No sensors warning | Modal | DynamicStarMapActivity | Sensor missing |
| `HelpDialogFragment` | Help content | Modal | DynamicStarMapActivity | Menu |
| `LocationPermissionDeniedDialogFragment` | Permission denied | Modal | DynamicStarMapActivity | Location refused |

## Bottom Sheet Dialogs

### EulaBottomSheetFragment

**Purpose:** Display Terms of Service on first launch or when updated.

**UI Structure:**
```
┌──────────────────────────────────────────┐
│ Terms of Service             ← × to close │
├──────────────────────────────────────────┤
│ [Scrollable Content]                     │
│                                           │
│ Introduction...                          │
│                                           │
│ [Callout] Language Apology...           │
│                                           │
│ Terms of Service...                       │
│ [Scroll indicator]                        │
├──────────────────────────────────────────┤
│ [Decline]                    [Accept]     │
└──────────────────────────────────────────┘
```

**Content Source:** Converted from HTML to native views.

**User Actions:**
- **Accept:** Sets preference, dismisses, continues to app
- **Decline:** Closes app (cannot use without accepting)
- **Drag down:** Dismisses (but can't continue)

**Listener Pattern:**
```kotlin
interface EulaAcceptanceListener {
    fun eulaAccepted()
    fun eulaRejected()
}
```

### WhatsNewBottomSheetFragment

**Purpose:** Show release notes when app is updated.

**UI Structure:**
```
┌──────────────────────────────────────────┐
│ What's New                ← × to close    │
├──────────────────────────────────────────┤
│ [Scrollable Release Notes]                │
│                                           │
│ Version 1.10.11                            │
│ - Added Material 3 theme                  │
│ - Improved AR mode                        │
│ - Bug fixes                                │
│                                           │
├──────────────────────────────────────────┤
│ [OK]                                     │
└──────────────────────────────────────────┘
```

**User Actions:**
- **OK:** Marks as read, dismisses, continues to app

**Listener Pattern:**
```kotlin
interface CloseListener {
    fun dialogClosed()
}
```

## Modal Dialogs

### TimeTravelDialogFragment

**Purpose:** Control time travel - view sky at different dates/times.

**UI Pattern:** Custom time dial UI with slider.

**User Actions:**
- **Slider:** Move through time
- **Play/Pause:** Animate through time
- **Now:** Return to current time
- **Sound Effects:** Time travel whoosh

### Search Result Dialogs

**NoSearchResultsDialogFragment:**
- Message: "No objects found"
- Action: OK button

**MultipleSearchResultsDialogFragment:**
- List of matching objects
- User selects one → Navigate to it

### ObjectInfoDialogFragment

**Purpose:** Show information about a celestial object.

**UI Structure:**
```
┌──────────────────────────────────────────┐
│ [Object Name]              ← × to close  │
├──────────────────────────────────────────┤
│ [Image if available]                      │
│                                           │
│ Description...                            │
│                                           │
│ Scientific Data:                          │
│ • Distance: ...                            │
│ • Magnitude: ...                          │
│ • Size: ...                                │
│                                           │
│ Fun fact: ...                             │
├──────────────────────────────────────────┤
│ [OK]                                     │
└──────────────────────────────────────────┘
```

**Data Source:** `ObjectInfo` from domain layer.

## Common Dialog Patterns

### Fragment-Based Architecture

**All dialogs extend:** `androidx.fragment.app.DialogFragment` (or MaterialBottomSheetDialogFragment)

**Hilt Integration:**
```kotlin
@AndroidEntryPoint
class EulaBottomSheetFragment : MaterialBottomSheetDialogFragment() {
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var activity: Activity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inject dependencies work here
    }
}
```

### Listener Interfaces

**Pattern:** Callbacks to parent activity/fragment

```kotlin
// Define listener
interface EulaAcceptanceListener {
    fun eulaAccepted()
    fun eulaRejected()
}

// Parent activity implements listener
class SplashScreenActivity : AppCompatActivity(), EulaAcceptanceListener {
    override fun eulaAccepted() { /* ... */ }
    override fun eulaRejected() { /* ... */ }
}

// Fragment calls listener
private var listener: EulaAcceptanceListener? = null

fun setEulaAcceptanceListener(listener: EulaAcceptanceListener) {
    this.listener = listener
}

override fun onAcceptClick() {
    listener?.eulaAccepted()
    dismiss()
}
```

### Dialog State Management

**Survive configuration changes:** Use fragment arguments

```kotlin
companion object {
    private const val ARG_EULA_VERSION = "eula_version"

    fun newInstance(version: Int): EulaBottomSheetFragment {
        return EulaBottomSheetFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_EULA_VERSION, version)
            }
        }
    }
}

override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    // Retrieve arguments
    val eulaVersion = requireArguments().getInt(ARG_EULA_VERSION)
    // ...
}
```

## Bottom Sheet Behavior

### Peeking and Expanding

**Peek Height:** 250dp (shows title + first paragraph)

**Full Expansion:**
- User drags up
- User clicks "Accept" or "Decline" (auto-expands first)
- Keyboard appears (auto-expands)

**Corner Radius:** 16dp at top, 0dp at bottom

### Dismissal

**Methods:**
- Drag down past threshold
- Tap outside (if cancelable = true)
- Tap X button
- Programmatic: `dismiss()`

## Modal Dialog Behavior

**Standard Material 3 behavior:**
- Centered on screen
- Width: `match_parent` with 16dp margins
- Corner radius: 28dp (Material 3 default)
- Background: Dark surface (#1E1E1E)

**Button Layout:**
- Horizontal: Buttons side-by-side
- Vertical: Buttons stacked (mobile)

## Related Specifications

- [activities.md](activities.md) - Activities that use dialogs
- [material-3.md](material-3.md) - Dialog styling
- [../features/search.md](../features/search.md) - Search dialog usage
