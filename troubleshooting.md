# Sky Map — Troubleshooting & FAQ

For general help and a feature overview, see [help.md](help.md).


---

## The map is pointing in the wrong direction or is inaccurate

To produce an accurate sky map, Sky Map needs three things: the direction your phone is facing,
where you are on the planet, and the current time. If the map looks wrong, one of these is likely
off.

### Phone direction

The most common cause is the device's compass providing Sky Map with an incorrect direction.
**This is a hardware issue — it has nothing to do with Sky Map itself.** Sky Map can only work
with the data the sensor provides.

Things to try:

- **Calibrate the compass:** Wave your phone in a slow, smooth figure-8 motion. You may need to
  repeat this occasionally — it is a hardware-level process, not something Sky Map controls.
- **Remove magnetic interference:** Metal structures, car dashboards, and magnets in phone cases
  are common culprits. Move away from them and try again.
- **Toggle Magnetic Correction:** Go to **Settings → Location** and try switching this on or off.
  In some parts of the world, magnetic north and true north differ by 20 degrees or more — this
  setting corrects for that.
- **Manual compass offset:** If your compass has a consistent offset, go to
  **Settings → Sensor Settings (Experts)** and enter an estimate of the offset in degrees. This
  requires some trial and error.

See [About phone compasses](#about-phone-compasses) for a deeper explanation of how phone compasses
work and why they can go wrong.

### Location

Sky Map must know where on Earth you are to draw the correct sky. Without this, Sky Map defaults
to 0° latitude / 0° longitude — a point in the middle of the ocean — and the map will look
completely wrong wherever you are.

**A telltale symptom:** if Polaris (the Pole Star) appears near the horizon instead of high in the
northern sky, Sky Map almost certainly doesn't know your location.

To fix:

- Grant Sky Map location permission. Open your device's **Settings → Apps → Sky Map → Permissions**
  and enable **Location**. Sky Map should have requested this when it first launched — if you
  declined, this is the most likely cause.
- Open the **Diagnostics** page (overflow menu) and confirm that the latitude and longitude shown
  there look correct for where you are.

See also: [Google's guide to app permissions](https://support.google.com/googleplay/answer/6270602?p=app_permissions_m)

### Time

Sky Map uses your device's clock and time zone to calculate where objects appear in the sky. Time
errors are uncommon, but an incorrect time zone in particular can shift the entire sky by several
hours.

Open the **Diagnostics** page and confirm that the time and time zone shown there are correct. If
they are wrong, that is a device clock issue rather than a Sky Map problem — correct it in your
device's system settings.

---

## About phone compasses

Sky Map doesn't calculate your orientation from scratch — it reads the data provided by your
phone's built-in magnetometer. If the phone reports that its sensor is uncalibrated, Sky Map
displays an accuracy warning.

### What calibration actually does

Calibration is a hardware-level process that helps the sensor account for "Hard Iron" and "Soft
Iron" distortions caused by the components inside the phone itself.

**The goal:** to ensure the sensor sees a consistent "circle" of magnetic data as you rotate the
phone through all orientations.

**The reality:** calibration makes the sensor internally consistent, but it does not guarantee that
it is pointing to True North.

### Why the map might still be off — magnetic bias

Even a perfectly calibrated sensor can suffer from magnetic bias. The Earth's magnetic field is a
relatively weak signal, easily disturbed by:

- **Local interference:** metal structures, car dashboards, or magnets in phone cases
- **Environmental offset:** a calibrated sensor knows how to read itself, but has no way of knowing
  you're standing next to a refrigerator. This creates a constant bias that shifts the star map
  regardless of calibration status.
- **Hardware quality:** some phones simply have poor magnetometers. If yours has a consistent
  offset of several degrees, the manual compass offset in
  **Settings → Sensor Settings (Experts)** is your best option.

The figure-8 calibration gesture ([see video](https://www.youtube.com/watch?v=-Uq7AmSAjt8)) forces
the sensor to sample the magnetic field from many angles, allowing the hardware to filter out
internal noise. Move to a clear area away from metal objects and magnetic sources before performing
it.

> This is a physical property of mobile hardware. If the map remains slightly shifted, your
> environment is likely causing a magnetic bias that the software cannot fully correct.

---

## The map doesn't move

- Make sure you haven't switched into Manual Mode — tap the sensor icon to return to Automatic Mode.
- Your phone must have at minimum a compass (magnetometer) and an accelerometer. A gyroscope is
  strongly recommended. Check the **Diagnostics** page (overflow menu) to see which sensors your
  device has — missing sensors show as `--,--,--`.
- If you have all the required sensors but the map is still stuck, try the figure-8 compass
  calibration — see [About phone compasses](#about-phone-compasses).

---

## The map is jittery

- If your phone lacks a gyroscope, some jitter is normal. Enable **Disable Gyro** under
  **Settings → Sensor Settings (Experts)** to use the alternative sensor mode.
- Try adjusting **Sensor Speed** and **Sensor Damping** in the same settings section.

---

## Do I need an internet connection?

No. Sky Map works fully offline. An internet connection is only needed to:
- Look up a location by place name (you can enter lat/long manually instead)
- Load Hubble Gallery images (previously cached images may still be available offline)

---

## Can I help test the latest features?

Yes! Join the [beta testing program](https://play.google.com/apps/testing/com.google.android.stardroid)
on Google Play to get the latest version before it goes public.


---

## Still stuck?

Open the **Diagnostics** page from the overflow menu and email us at **skymapdevs@gmail.com** with
a screenshot. It contains your sensor status, location, and time info which helps us diagnose
problems quickly.

---

# Find us elsewhere

- GitHub: https://github.com/sky-map-team/stardroid
- Facebook: https://www.facebook.com/groups/113507592330/
- X (Twitter): http://twitter.com/skymapdevs
