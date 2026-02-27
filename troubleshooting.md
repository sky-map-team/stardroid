# Sky Map — Troubleshooting & FAQ

For general help and a feature overview, see [help.md](help.md).

---

## The map doesn't move

- Make sure you haven't switched into Manual Mode — tap the sensor icon to return to Automatic Mode.
- Your phone must have at minimum a compass (magnetometer) and an accelerometer. A gyroscope is strongly recommended. Check the **Diagnostics** page (overflow menu) to see which sensors your device has.
- If you have all the sensors but the map is still stuck, try the figure-8 compass calibration below.

---

## The map is pointing in the wrong direction

- Try calibrating your compass by waving your phone in a slow, smooth figure-8 motion (see the [video](https://www.youtube.com/watch?v=-Uq7AmSAjt8)). You may need to repeat this occasionally — it's a hardware-level limitation, not a Sky Map bug.
- Check for magnetic interference: metal structures, car dashboards, and magnets in phone cases are common culprits.
- Try toggling **Magnetic Correction** in Settings (enable it if it's off, or disable it if it's on) to see which gives a better result in your location.

### Why is my compass reported as "Low Accuracy"?

Sky Map doesn't calculate your orientation from scratch — it reads the data provided by your phone's built-in magnetometer. If the phone reports that its sensor is uncalibrated, Sky Map displays an accuracy warning.

#### What calibration actually does

Calibration is a hardware-level process that helps the sensor account for "Hard Iron" and "Soft Iron" distortions caused by components inside the phone itself.

**The goal:** to ensure the sensor sees a "circle" of magnetic data as you rotate the phone through all orientations.

**The reality:** calibration makes the sensor internally consistent, but it does not guarantee it is pointing to True North.

#### Why the map might still be "off" — magnetic bias

Even a perfectly calibrated sensor can suffer from magnetic bias. The Earth's magnetic field is a relatively weak signal, easily disturbed by:

- **Local interference:** metal structures, car dashboards, or magnets in phone cases
- **Environmental offset:** a calibrated sensor knows how to read itself, but it has no way of knowing you're standing next to a refrigerator. This creates a constant "bias" that shifts the star map a few degrees regardless of calibration status.
- **Hardware quality:** some phones simply have poor magnetometers. If yours has a consistent offset of several degrees, try the manual correction below.

#### How to fix it

- **Clear the area:** move away from large metal objects and anything magnetic.
- **The figure-8:** wave your phone in a large, smooth figure-8 motion ([see video](https://www.youtube.com/watch?v=-Uq7AmSAjt8)). This forces the sensor to sample the magnetic field from many angles, allowing the hardware to filter out internal noise.
- **Manual compass offset:** as a last resort, go to **Settings → Sensor Settings (Experts)** and enter an estimate of the offset in degrees (positive or negative). This requires some trial and error. It's near the Magnetic Correction setting.

> This is a physical property of mobile hardware. If the map remains slightly shifted, your environment is likely causing a magnetic bias that the software cannot correct.

---

## Why isn't automatic location working?

Most likely Sky Map doesn't have location permission. It should have asked when you first launched it. If you declined, go to your device's **Settings → Apps → Sky Map → Permissions** and enable Location.

See also: [Google's guide to app permissions](https://support.google.com/googleplay/answer/6270602?p=app_permissons_m)

---

## The map is jittery

- If your phone lacks a gyroscope, some jitter is normal. Enable **Disable Gyro** under **Settings → Sensor Settings (Experts)**.
- Try adjusting **Sensor Speed** and **Sensor Damping** in the same settings section.

---

## Do I need an internet connection?

No. Sky Map works fully offline. An internet connection is only needed to:
- Look up a location by place name (you can enter lat/long manually instead)
- Load Hubble Gallery images (cached images may still be available)

---

## Can I help test the latest features?

Yes! Join the [beta testing programme](https://play.google.com/apps/testing/com.google.android.stardroid) on Google Play to get the latest version before it goes public.

---

## Still stuck?

Open the **Diagnostics** page from the overflow menu and email us at **skymapdevs@gmail.com** with a screenshot. It contains your sensor status, location, and time info which helps us diagnose problems quickly.

---

# Find us elsewhere

- GitHub: https://github.com/sky-map-team/stardroid
- Facebook: https://www.facebook.com/groups/113507592330/
- X (Twitter): http://twitter.com/skymapdevs
