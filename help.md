# Sky Map Help

Sky Map turns your Android device into a window on the night sky. Point it in any direction and you'll see a real-time star map of what's there — stars, planets, constellations, and more.

---

## Introduction and Quick Start

Hold your phone up and look through it like a window into space. Sky Map uses your phone's compass, accelerometer, and gyroscope to track where you're pointing and update the map in real time.

If this is your first time:
1. Allow location access when prompted — Sky Map needs your approximate location to draw the right sky for where you are
2. Point your phone at the sky in any direction
3. The map will move to match where you're pointing
4. Tap anywhere on the screen to show the controls

---

## Navigating the Sky

### Automatic Mode
In Automatic Mode (the default), Sky Map uses your phone's sensors to show whatever is in the direction your phone is pointing. Just move your phone around and the map follows.

### Manual Mode
Tap the sensor/compass icon in the controls to switch to Manual Mode. In this mode you can:
- Drag the map with one finger to pan around
- Pinch to zoom in or out
- Rotate with two fingers to change orientation

Manual mode is handy when you want to explore the sky without holding your phone up — or when you're lying on your back.

---

## Visible Layers

Sky Map organizes what it shows into layers, each of which can be toggled on or off independently. Tap the screen to reveal the layer controls on the side — they glow orange when a layer is on and appear dim when off.

| Layer | What it shows |
|---|---|
| Stars | The brightest stars, with labels for notable ones |
| Constellations | Constellation outlines and names |
| Messier Objects | Deep-sky objects: galaxies, nebulae, and star clusters |
| Planets | The planets of our solar system, displayed as images |
| Meteor Showers | Active and upcoming meteor shower radiant points |
| Comets | Positions of notable comets |
| Satellites | The International Space Station and selected satellites |
| Ecliptic | The Sun's apparent annual path through the sky |
| RA/Dec Grid | Right Ascension and Declination coordinate grid |
| Horizon | The horizon line and cardinal direction labels (N, S, E, W) |

> **Worth knowing:** Sky Map shows the geometric horizon — a perfectly flat, unobstructed view. Objects just above it may still be hidden by buildings, hills, or trees in practice.

---

## On-Screen Controls

Tap anywhere on the screen to show or hide the controls. On one side you'll find the layer toggles described above. On the other side is the button to switch between Automatic and Manual modes.

The controls fade out automatically after a few seconds to keep the view clear.

---

## Search

Tap the magnifying glass icon to search for anything in the sky. You can search for:

- **Stars** — by name, e.g. "Sirius", "Betelgeuse", "Polaris"
- **Planets** — e.g. "Mars", "Saturn"
- **Constellations** — e.g. "Orion", "Cassiopeia"
- **Messier objects** — e.g. "M31", "Andromeda Galaxy", "Pleiades"
- **Comets and meteor showers** — by name

**In Automatic Mode:** after selecting a result, Sky Map shows a targeting circle and a directional arrow. Follow the arrow by rotating your phone — the circle changes from blue to red, then orange when the object is within your field of view. Tap the **✕** to exit search.

**In Manual Mode:** the map simply jumps to center on the object.

---

## Time Travel

Time Travel lets you see the sky at any moment from the year 1900 to 2100. Select **Time Travel** from the main menu.

You can choose a preset date (solstices, eclipses, famous celestial events, historical moments) or enter your own date and time. Tap **Go!** to fly there.

Once in Time Travel mode, the date is shown on screen and playback controls let you move through time:
- Tap the forward or backward play buttons to start moving
- Tap them again to go faster
- Tap the stop button to pause

Some things you can use it for:
- *What can I see just after sunset tonight?*
- *When is the next good chance to spot Mercury?*
- *What did the sky look like the night I was born?*

Accuracy decreases for dates far from the present, mainly due to the gravitational influence of Jupiter.

---

## Hubble Gallery

The Gallery contains a collection of images from the Hubble Space Telescope. Open it from the main menu.

Browse thumbnails by scrolling, and tap one to see the full image. Use the **Find in sky** button to jump to Search mode and locate that object in your current sky map.

---

## Night Vision Mode

If you're outside doing real observing, Night Vision mode helps preserve your dark-adapted eyesight. Toggle it from the main menu.

In Night Vision mode, Sky Map:
- Switches the display to deep red tones
- Significantly dims the screen
- Reduces button backlights

Toggle it again to return to normal. The **Screen Dimming** option under **Settings → Appearance** gives you additional control over how dark the screen gets.

---

## Setting Your Location

Sky Map needs your approximate location to draw the correct sky. By default it uses your device's location services (mobile network or GPS).

If location access is unavailable or you'd rather set it manually, go to **Settings → Location** and either:
- Enter a **place name** (requires an internet connection to look up the coordinates), or
- Enter a **latitude and longitude** directly in degrees (no internet needed)

If you've previously denied Sky Map location permission, you may need to re-enable it in your device's **App Settings → Permissions**.

---

## Telescope Users — Pointer Mode

Under **Settings → Sensor Settings**, change the **View Direction** to **Pointer Mode**. In this mode, Sky Map shows what the *long edge* of the phone is pointing at, rather than what the screen is facing.

This lets you mount your phone along the side of a telescope tube so the screen is perpendicular to the tube while the map still tracks what the telescope is aimed at.

---

## Tips and Settings Worth Knowing

- **Diagnostics:** If something looks wrong, open the overflow menu and tap **Diagnostics**. This page shows your current location, time, sensor status, and accuracy readings — very useful if you need to report a problem.
- **Messier images:** Under **Settings → Appearance**, you can choose whether Messier objects appear as realistic thumbnail images or simple dots.
- **Font size:** Adjust label size under **Settings → Appearance**.
- **Jittery map:** Experiment with **Sensor Speed** and **Sensor Damping** under **Settings → Sensor Settings (Experts)**.
- **No gyroscope?** If your phone lacks a gyroscope, enable **Disable Gyro** in Sensor Settings to use an alternative sensor mode.
- **Magnetic correction:** Sky Map can apply a magnetic declination correction so the map aligns with True North rather than Magnetic North. Toggle this under **Settings → Location**. In some parts of the world the difference can be 20 degrees or more.

---

## Troubleshooting

See [troubleshooting.md](troubleshooting.md) for detailed troubleshooting — especially for compass accuracy issues. Here's a quick reference:

### The map doesn't move
- Check you're in Automatic Mode (not Manual)
- Try the figure-8 calibration gesture: wave your phone slowly in a large, smooth figure-8 shape to help the compass resample the magnetic field
- Check the Diagnostics page to confirm your device has the required sensors (compass and accelerometer are essential; gyroscope is recommended)

### The map is pointing the wrong way
- Perform the figure-8 calibration gesture
- Toggle **Magnetic Correction** in Settings (try on if it's off, or off if it's on)
- Move away from metal objects, car dashboards, or magnetic phone cases
- See [troubleshooting.md](troubleshooting.md) for advanced options including a manual compass offset

### Location isn't working
- Make sure Sky Map has location permission in your device's **App Settings**
- If GPS is slow to acquire, allow network-based location as a fallback, or enter your location manually in Settings

### The map is jittery
- Adjust **Sensor Speed** and **Sensor Damping** under **Settings → Sensor Settings (Experts)**
- If your phone has no gyroscope, enable **Disable Gyro** in sensor settings

---

If you're still stuck, email us at **skymapdevs@gmail.com** — a screenshot of your Diagnostics page is very helpful.
