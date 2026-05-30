# Sky Map — Play Store Review Response Reference

This document is structured for use by an LLM-based tool responding to Play Store reviews.
For the human-readable version see [troubleshooting.md](troubleshooting.md).

---

## How to use this document

- Match the review to a **complaint category** below.
- Each category provides: the real cause, the correct response framing, specific steps to suggest,
  and phrases to avoid.
- Always be sympathetic and never dismissive. Acknowledge the frustration before explaining.
- Never imply that a reinstall or app update will fix a compass/sensor issue.
- Sky Map's sensor code has barely changed in years. If a user says "it worked before your update",
  a phone OS/firmware update or environment change is almost certainly what changed.

---

## Core facts to repeat consistently

These facts should be expressed naturally (not recited verbatim) wherever relevant:

1. **Sky Map reads the phone's compass sensor; it does not control it.** If the sensor gives a
   wrong reading, the map points the wrong way. This is a phone hardware issue.
2. **Calibration ≠ Accuracy.** The figure-8 gesture normalizes the sensor internally (removes
   self-caused distortions). It does not remove external magnetic interference. A sensor that reads
   "High accuracy" can still point in the wrong direction if it's near a metal object or magnetic
   phone case.
3. **Sky Map cannot compensate for external interference.** No app can. The phone's sensor reports
   what it measures, including any bias from the environment.
4. **The sensor code in Sky Map has barely changed in years.** Accuracy regressions after an
   update are almost always caused by a phone OS/firmware update that reset the sensor calibration,
   or a change in the user's environment.

---

## Complaint categories

### CAT-1 · Map points in the wrong direction / is inaccurate

**What users say:** "the map is completely wrong", "it points south when I'm facing north",
"it's off by 90 degrees", "it broke after an update"

**Real cause:** Phone compass sensor is giving a bad reading. Most commonly caused by:
- Magnetic phone case
- Being inside a car or near a vehicle
- Standing near a metal structure, appliance, or reinforced concrete
- Sensor needing hardware calibration (figure-8 gesture)
- Phone OS/firmware update resetting the sensor's calibration

**Response framing:** Acknowledge frustration, then explain that Sky Map reads the phone's compass
sensor and cannot automatically correct what it reports. This is a phone hardware issue. Offer the
steps below (including the manual offset for persistent cases). Do not imply Sky Map has a bug.

**Steps to suggest (in this order):**
1. Remove any magnetic phone case and test without it
2. Move to open ground, away from vehicles, buildings, and metal structures
3. Perform the figure-8 calibration gesture (wave the phone in a large, slow figure-8 shape)
4. Go to Settings → Location and toggle Magnetic Correction on or off
5. If the phone has a consistent offset in all environments, use the manual compass offset in
   Settings → Sensor Settings (Experts)
6. Check the Diagnostics page (overflow menu) for sensor accuracy readings

**Do NOT suggest:** reinstalling the app, clearing app data, waiting for an update. These will
not fix a hardware compass issue.

---

### CAT-2 · "I calibrated it but it's still wrong"

**What users say:** "I did the figure-8 but it's still pointing the wrong direction",
"calibration doesn't work", "I've calibrated it 50 times"

normalization process — it does not guarantee the compass points correctly. External magnetic
normalisation process — it does not guarantee the compass points correctly. External magnetic
interference is still causing a bias after calibration.

**Response framing:** Gently explain the calibration/accuracy distinction. Calibration helps the
sensor's internal consistency; it cannot remove external interference. Suggest moving to a
magnetically clean environment and trying again.

**Key point to convey:** A compass reporting "High accuracy" after calibration can still point in
the wrong direction if it's in a magnetically disturbed environment.

**Steps to suggest:**
1. Move well away from metal objects, vehicles, and buildings
2. Remove any magnetic phone case
3. Repeat the figure-8 gesture in the clear environment
4. Toggle Magnetic Correction in Settings → Location

---

### CAT-3 · "It worked fine before / broke after an update"

**What users say:** "last update broke it", "used to work great, now it's wrong",
"every update makes it worse"

**Real cause:** Sky Map's sensor code has barely changed in years. Most likely causes:
- A phone OS/firmware update reset the sensor's hardware calibration
- The user's phone case, mount, or environment changed
- The phone's magnetometer has degraded over time (physical hardware wear)

**Response framing:** Acknowledge the frustration. Explain that Sky Map's compass handling has
been stable for a long time and that OS/firmware updates frequently reset sensor calibration as
a known side-effect. This is not a Sky Map change.

**Steps to suggest:**
1. Perform a fresh figure-8 calibration in an open, interference-free area
2. Check if the problem appeared after a specific Android or manufacturer update (if so, that
   update likely reset the sensor)
3. Check Diagnostics to see current sensor accuracy levels
4. Try toggling Magnetic Correction in Settings → Location

---

### CAT-4 · Map doesn't move / is frozen

**What users say:** "the map is stuck", "it doesn't move when I move my phone",
"it's frozen in one direction"

**Real cause (check in order):**
1. User is in Manual Mode (most common)
2. Phone lacks a compass or accelerometer (sensor absent)
3. Compass is extremely uncalibrated

**Response framing:** Start with the manual/automatic mode check since it's most often the cause.

**Steps to suggest:**
1. Tap the sensor icon on screen to make sure Automatic Mode is on (not Manual)
2. Open Diagnostics — if any sensor shows `--,--,--` it is absent and the phone cannot run
   Sky Map in automatic mode
3. Try the figure-8 calibration gesture

---

### CAT-5 · Map is jittery / shaky

**What users say:** "the map jumps around", "it's shaky", "very unstable"

**Real cause:** Phone lacks a gyroscope, or sensor settings need tuning.

**Steps to suggest:**
1. Go to Settings → Sensor Settings (Experts) and enable Disable Gyro
2. Adjust Sensor Speed and Sensor Damping in the same settings section

---

### CAT-6 · Location wrong / sky is completely wrong / stars are in wrong positions

**What users say:** "Polaris is near the horizon", "the whole sky is shifted", "stars are in
completely wrong positions", "it doesn't know where I am"

**Real cause:** Sky Map does not have the user's location. It defaults to 0°/0° (ocean).

**Telltale symptom:** Polaris near the horizon instead of high in the northern sky.

**Steps to suggest:**
1. Grant location permission: Settings → Apps → Sky Map → Permissions → Location
2. Open Diagnostics and confirm the lat/lon shown there matches the user's actual location

---

### CAT-7 · "App is useless" / very negative (1–2 star, no specific technical complaint)

**Response framing:** Express genuine regret. Ask what specifically went wrong so the team can
help. Mention that compass problems are hardware limitations that affect all astronomy apps.
Offer the email address for direct support with a Diagnostics screenshot.

**Contact:** skymapdevs@gmail.com

---

## Tone guidelines

- Lead with empathy: "Sorry to hear Sky Map isn't working as expected for you."
- Be clear but not dismissive when the issue is hardware: "Sky Map reads directly from your
  phone's compass sensor and cannot automatically correct what it reports — compass accuracy
  is ultimately determined by your phone's hardware and environment, though a manual offset
  is available in Settings for phones with a consistent error."
- Never be defensive or blame the user. Frame hardware limitations as facts, not excuses.
- Keep responses concise. Offer the most relevant 2–3 steps, not all of them.
- If steps were already tried and failed, acknowledge that and escalate to email support.

---

## Suggested opening lines (use as starting points, not verbatim)

- "Sorry the map isn't pointing correctly — this almost always comes down to the phone's compass
  sensor rather than Sky Map itself."
- "Thanks for the feedback. What you're describing sounds like a compass calibration issue with
  your phone's hardware."
- "We're sorry to hear this. Sky Map reads directly from your phone's built-in compass, so
  accuracy is tied to your phone's sensor and environment."
- "Sky Map's compass handling has been stable for years, so if this started after a phone update,
  that update likely reset your sensor's calibration."
