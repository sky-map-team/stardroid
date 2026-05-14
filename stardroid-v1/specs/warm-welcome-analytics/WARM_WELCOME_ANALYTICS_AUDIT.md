# Warm Welcome Analytics Audit

## Summary
The warm welcome onboarding feature has **no analytics instrumentation**. This creates a complete blind spot in the funnel—we cannot measure abandonment rates at any stage of the onboarding flow.

## Current Flow
```
SplashScreenActivity (checks if warm welcome seen)
  → WarmWelcomeActivity (no analytics)
    → Slide 1: Feature highlights + Skip/Next buttons
    → Slide 2: Deep sky objects image
    → Slide 3: Sensor status checks
    → Finish → DynamicStarMapActivity
```

## Critical Gaps

### 1. **No funnel entry event**
- When does the warm welcome start?
- How many first-time users enter the flow?
- We have no baseline to calculate drop-off percentages

### 2. **No slide-level tracking**
| Slide | Current Tracking | What We Need |
|-------|------------------|--------------|
| Slide 1 (highlights) | None | `warm_welcome_slide_viewed` with `slide: 1` |
| Slide 2 (astronomy) | None | `warm_welcome_slide_viewed` with `slide: 2` |
| Slide 3 (sensors) | None | `warm_welcome_slide_viewed` with `slide: 3` |

### 3. **No skip events**
- User clicks "Skip" on slide 1: abandonment at 0% progress
- User clicks "Skip" on slide 2: abandonment at 33% progress
- No event to track this critical drop-off signal

### 4. **No completion event**
- User finishes all 3 slides and clicks "Finish"
- No way to distinguish: completed → main app vs skipped onboarding
- Cannot measure onboarding completion rate

### 5. **No user properties set**
- We don't mark users who went through the warm welcome
- We don't track first-time user vs returning user when they skip/complete
- Cannot segment users by onboarding path

## Recommended Instrumentation

### Events to add:
```
WARM_WELCOME_STARTED_EVENT = "warm_welcome_started_ev"
  → params: is_manual_invocation (bool)

WARM_WELCOME_SLIDE_VIEWED_EVENT = "warm_welcome_slide_viewed_ev"
  → params: slide_number (1, 2, 3)

WARM_WELCOME_SKIPPED_EVENT = "warm_welcome_skipped_ev"
  → params: slide_number (which slide they skipped on)

WARM_WELCOME_COMPLETED_EVENT = "warm_welcome_completed_ev"
```

### User properties to add:
```
COMPLETED_WARM_WELCOME = "completed_warm_welcome_prop"
  → "true" / "false" when they finish or skip
```

## Expected Funnel (after instrumentation)
```
100% → warm_welcome_started (baseline)
 ↓
~95% → warm_welcome_slide_viewed, slide: 1
 ↓
~80% → warm_welcome_slide_viewed, slide: 2
 ↓
~60% → warm_welcome_slide_viewed, slide: 3
 ↓
~40% → warm_welcome_completed (rest skipped)
```

This would reveal:
- If users are abandoning on the content-heavy slide 1
- If sensor status (slide 3) discourages completion
- Whether the warm welcome is actually reaching users effectively

## Files to modify
- `AnalyticsInterface.java` - add event constants
- `WarmWelcomeActivity.kt` - inject analytics and log events
- `SplashScreenActivity.java` - optionally log entry point
