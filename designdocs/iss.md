# Implementing ISS Tracking

Much asked for feature (e.g. https://github.com/sky-map-team/stardroid/issues/318)

To do it properly would like to revamp the datamodel and the GUI (certainly before we support many more satellites). But should be straightforward to do something
simple in the meantime - just a labelled dot on the screen will do.

### Not in scope
   * Reminders and notifications.
   * Other satellites like the Iridium ones.

## Notes
   * There's some existing code for a layer [here](https://github.com/sky-map-team/stardroid/blob/master/app/src/main/java/com/google/android/stardroid/layers/IssLayer.java) that even includes
code to get over the network updates to the Orbital Elements, but it was never completed.
   * Unlike our other objects, the user's location must be taken into account
   * It's going to move a lot faster
   
The current object location code is a bit of a tangled mess - it was thrown together in a hurry
by 3 different developers (including me). Before adding ISS support it makes sense to try to
clean that up a little bit.  See [ephemeris.md](ephemeris.md).

## Data sources

https://wheretheiss.at/w/developer
https://spaceflight.nasa.gov/realdata/sightings/SSapplications/Post/JavaSSOP/orbit/ISS/SVPOST.html (used in the existing code - ironically it's going to be shut down in October).
