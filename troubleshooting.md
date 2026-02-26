# Troubleshooting/FAQ
## The Map doesn't move
*   Make sure you haven't switched into manual mode.
*   Does your phone have a compass? If not, Sky Map cannot tell your orientation. Look it up here: http://www.gsmarena.com/


## The Map doesn't is pointing in the wrong place

*   Try calibrating your compass by moving it in a figure of 8 motion or as described in https://www.youtube.com/watch?v=k1EPbAapaeI.
*   Are there any magnets or metal nearby that might interfere with the compass? <br />
*   Try switching off "magnetic correction" (in settings) and see if that is more accurate.
  
### Why is my compass reporting as "Low Accuracy"?
Sky Map doesn't "calculate" your orientation from scratch; it simply reads the data provided by your phone's built-in magnetometer. If your phone reports that its sensor is uncalibrated, Sky Map will show an accuracy warning.

### What Calibration Actually Does
Calibration is a hardware-level process that helps the sensor account for "Hard Iron" and "Soft Iron" distortions caused by the components inside the phone itself.

**The Goal:** To ensure the sensor sees a "circle" of magnetic data as you rotate it.

**The Reality:** Calibration makes the sensor internally consistent, but it does not guarantee it is pointing to True North.

### Why it might still be "Off" (The Bias)
Even a perfectly calibrated sensor can suffer from Magnetic Bias. Because your phone is measuring a relatively weak signal (the Earth's magnetic field), it is easily "distracted" by:

* **Local Interference:** Metal structures, car dashboards, or magnets in phone cases.
* **The Offset:** A calibrated phone knows how to read its own sensor, but it doesn't know if you're standing next to a refrigerator. This creates a constant "bias" where the star map might be shifted a few degrees to the left or right regardless of calibration status.
* **A "bad" phone:** Some phones just have bad sensors. If your phone has a consistent bias of a few degrees try the manual offset fix below.

### How to Fix It
* **Clear the Area:** Move away from large metal objects or magnets.
* **The Figure-8:** Wave your phone in a large, smooth figure-8 motion ([see video](https://www.youtube.com/watch?v=-Uq7AmSAjt8)). This forces the sensor to sample the magnetic field from multiple angles, allowing the hardware to filter out its own internal noise.
* **Manual offset:** As a last resort go to settings and manually enter an estimate of the offset (near the magnetic correction setting). This might take some trial and error to get right.

**Note:** This is a physical property of mobile hardware. If the map remains slightly shifted, your environment is likely causing a magnetic bias that the software cannot "code away."

## Why is autolocation not supported for my phone?
* Most likely because you haven't granted Sky Map permission to see your approximate location.  It should have asked you when you first ran it, but if not, check to see if you need to enable the location permission setting for Sky Map as described in https://support.google.com/googleplay/answer/6270602?p=app_permissons_m

## The Map is jittery
*    If you have a phone that lacks a gyro then some jitter is to be expected. Try adjusting the sensor speed and damping (in settings).

## Do I need an internet connection?
*    No, but some functions (like entering your location manually) won't work without one. You'll have to use the GPS or enter a latitude and longitude instead.

## Can I help test the latest features?
*    Sure! Join our [beta testing program](https://play.google.com/apps/testing/com.google.android.stardroid) and get the latest version. 

# Find us elsewhere
*    ⭐ GitHub: https://github.com/sky-map-team/stardroid
*    ⭐ Facebook: https://www.facebook.com/groups/113507592330/
*    ⭐ Twitter: http://twitter.com/skymapdevs
