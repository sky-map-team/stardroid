# Sky Map — Troubleshooting & FAQ

For a general feature overview, see [help.md](help.md).

---

## The map is pointing in the wrong direction or is inaccurate

Sky Map needs three things to show the correct sky: the direction your phone is facing, where you
are on the planet, and the current time. If the map looks wrong, one of these is likely off.

### Phone direction (the most common cause by far)

Sky Map reads direction data from your phone's built-in compass sensor (magnetometer). If the
sensor gives a wrong reading, the map will point the wrong way — and that is a phone hardware
issue, not a Sky Map bug. Sky Map cannot automatically correct what the sensor reports, though a
manual offset is available in Settings for phones with a consistent directional error.

**"I calibrated it but it's still wrong."** This is the most common misunderstanding. Calibration
and accuracy are two different things:

- **Calibration** (the figure-8 gesture) asks your phone's hardware to normalize its sensor
  internally. It removes distortions caused by the phone's own components. Sky Map has no control
  over this process — it is handled entirely by the phone.
- **Accuracy** is whether that normalised sensor is actually pointing in the right direction. A
  calibrated compass is not necessarily an accurate one.

Even after a successful calibration, external magnetic interference will still shift the reading:

| Source | Effect |
|---|---|
| Metal structures, reinforced concrete | Can deflect the magnetic field by 5–30° |
| Car dashboards | Very common — many contain magnets and motor components |
| Magnetic phone cases | Extremely common cause of persistent compass errors |
| Nearby electronics | Smaller effect but can add up |

The Earth's magnetic field is a weak signal. Your phone's sensor has no way of knowing it's
sitting next to a source of interference — it just reports what it measures.

**Things to try, in order:**

1. **Move to open ground.** Go somewhere flat and away from buildings, vehicles, and metal
   structures. If the map improves, magnetic interference was the cause — not Sky Map.
2. **Perform the figure-8 gesture** — wave your phone slowly in a large, smooth figure-8 shape.
   You may need to repeat several times. The compass accuracy indicator in the calibration dialog
   shows whether the phone has accepted the calibration.
3. **Remove your phone case.** Magnetic cases are a very common culprit. Try without it.
4. **Toggle Magnetic Correction.** Go to **Settings → Location**. In some parts of the world,
   magnetic north and true north differ by 20° or more. Switching this on or off can sometimes
   dramatically improve alignment.
5. **Use a manual compass offset.** If your phone has a consistent directional error regardless of
   environment, go to **Settings → Sensor Settings (Experts)** and enter an offset in degrees.
   This is a workaround for phones with persistently biased hardware.

> **If a recent phone update broke your compass:** this is unfortunately common. Android updates
> and manufacturer firmware patches can reset or alter how the sensor is calibrated. Sky Map's
> sensor code has barely changed in years — if it worked before a system update, the update is
> almost certainly what changed, not Sky Map.

### Location

Sky Map must know where on Earth you are to draw the correct sky. Without location access it
defaults to 0° latitude / 0° longitude — a point in the ocean — and the map will look completely
wrong anywhere else.

**Telltale symptom:** Polaris (the Pole Star) appearing near the horizon rather than high in the
northern sky almost always means Sky Map doesn't know your location.

To fix:

- Grant location permission. Open **Settings → Apps → Sky Map → Permissions** and enable
  **Location**. If you declined the permission on first launch, this is the most likely cause.
- Open the **Diagnostics** page (overflow menu) and confirm the latitude and longitude shown there
  are correct for where you are.

See also: [Google's guide to app permissions](https://support.google.com/googleplay/answer/6270602?p=app_permissions_m)

### Time

Sky Map uses your device's clock and time zone. An incorrect time zone in particular can shift the
entire sky by several hours — making it look completely wrong even with a good compass and correct
location.

Open the **Diagnostics** page and check that the time and time zone shown are correct. If they
are wrong, fix them in your device's system settings.

---

## About phone compasses

This section explains the physics behind why phone compasses fail, for users who want a deeper
understanding.

### What the figure-8 gesture actually does

The figure-8 calibration gesture forces your phone's magnetometer to sample the magnetic field
from many different orientations. The sensor's firmware uses these samples to estimate and cancel
out "Hard Iron" distortions — constant magnetic fields produced by permanent magnets in the
phone's own components — and "Soft Iron" distortions from ferrous metals in the chassis that
distort external fields.

The goal is to make the sensor's response consistent as you rotate the phone through all
orientations. Think of it as the phone learning to ignore its own internal magnetic noise.

**What it does not do:** it cannot remove the effect of magnetic sources outside the phone. Once
calibrated, the sensor faithfully reports the field it is in — including any bias from a nearby
car dashboard or magnetic case.

### The calibrated-but-inaccurate compass

This is the crucial distinction most guides skip:

- Android reports compass *calibration status* as Unknown → Unreliable → Low → Medium → High.
  Sky Map displays this status and shows a warning when it is below Medium.
- Calibration status reflects internal consistency, not pointing accuracy. A sensor with
  "Accuracy: High" simply means the phone is confident in its own internal calibration — it says
  nothing about whether the phone is free from external interference.

A sensor that reads "High" accuracy near a refrigerator will confidently and consistently point in
the wrong direction.

### Why hardware quality matters

Not all magnetometers are equal. Some phones — particularly cheaper models or certain
manufacturer lines — have sensors with a persistent bias of 5–15° regardless of environment or
calibration. For these devices, the manual compass offset (**Settings → Sensor Settings
(Experts)**) is the best available workaround. No app can compensate for a fundamentally poor
sensor.

> Sky Map can only display what your phone reports. If the phone's compass is biased, the map
> will be biased by the same amount. This is a physical property of mobile hardware, not a
> software problem.

---

## The map doesn't move

- Make sure you haven't switched to Manual Mode — tap the sensor icon to return to Automatic Mode.
- Your phone needs at minimum a compass (magnetometer) and an accelerometer. Check the
  **Diagnostics** page — missing sensors appear as `--,--,--`.
- If sensors are present but the map is stuck, try the figure-8 calibration gesture.

---

## The map is jittery

- Some jitter is normal if your phone has no gyroscope. Enable **Disable Gyro** under
  **Settings → Sensor Settings (Experts)** to switch to the alternative sensor mode.
- Adjust **Sensor Speed** and **Sensor Damping** in the same section to tune the response.

---

## Do I need an internet connection?

No. Sky Map works fully offline. An internet connection is only needed to:

- Look up a location by place name (you can enter lat/long directly instead)
- Load Hubble Gallery images (previously cached images may still be available offline)

---

## Can I help test the latest features?

Yes! Join the [beta testing program](https://play.google.com/apps/testing/com.google.android.stardroid)
on Google Play.

---

## Still stuck?

Open the **Diagnostics** page from the overflow menu and email us at **skymapdevs@gmail.com** with
a screenshot. It contains your sensor readings, location, and time info which helps us understand
what's happening on your device.

---

# Find us elsewhere

- GitHub: https://github.com/sky-map-team/stardroid
- Facebook: https://www.facebook.com/groups/113507592330/
- X (Twitter): http://twitter.com/skymapdevs
