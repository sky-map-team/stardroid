# How does the sensor calculation work in Sky Map?

The task of the sensor code (mostly in the `AstronomerModel` class) is to convert the direction
of the phone's screen into celestial coordinates (ie coordinates fixed in the Sky). There are two
vectors that we need: 1) the direction the phone is pointing 2) the direction of 'up' on the phone.

There are two coordinate systems we care about: a coordinate system fixed in the phone and a 
coordinate system fixed in the Sky. The latter can be easily converted into Ra, Dec if needed.

The way we figure out how to transform the coordinates is to calculate local North, Up and East in
each coordinate system and use that to calculate the matrix transformation between the two. We can
then use this matrix to transform the phone's pointing direction in phone coordinates (which is
trivial) into celestial coordinates.

## Coordinate systems
The phone coordinate system consists of X, Y, Z in such a way that X is along the short edge of
the phone to the right, Y along the long edge to the top and Z coming out of the phone's screen. 
See https://developer.android.com/guide/topics/sensors/sensors_overview#sensors-coords

The celestial coordinate system consists of X, Y, Z in such a way that [1, 0, 0] has Ra and Dec of
(0, 0) and [0, 0, 1] has a Dec of 90 degrees.

First we need to calculate local North, Up, and East in the phone's coordinates from the sensors.

We'll use `N`, `U`, `E` to represent the North, Up and East vectors and `_p` for phone and 
`_c` for celestial coordinates.

## The old way

Back in the dawn of time when Sky Map was first written this hadn't really been done before
and we calculated these vectors directly from the raw sensor measurements, using a liberal
amount of exponential smoothing to deal with the noise (we had plans to implement fancy Kalman
filters that we never got round to.)  The two sensors we cared about were the accelerometer, which
gives us "down" in phone coordinates, and the magnetic sensor which sort of points at magnetic 
North.

`U_p = A`

where `A` is the acceleration vector from the sensor. Why `A`? Because if the phone is at rest the 
sensor will only feel gravity and it will feel as if the phone is accelerating UP at 9.81m/s.

The magnetic sensor gives a vector from magnetic North to South, but it's not flat along the
surface of the Earth as we need it, so:

`N_p = M - (U_p . M) U_p`

where `M` is the magnetic field vector from south to north and the vectors are all normalized.

Finally

`E_p = N_p X U_p`

## The new way

Now that gyros are more common on phones Android provides a much nicer "rotation sensor" which 
uses sensor fusion to create an accurate and stable representation of the phone's orientation in
space. Fortunately, our previous code can be adapted because `E_p`, `N_p` and `U_p` are basically
the row vectors of the rotation matrix.

## Celestial coordinates

Next step is to calculate those same vectors (N, U, E) in celestial coordiantes.
`U_c` is the Zenith - its declination is the same as the user's latitude, while its RA depends on 
the user's time. Once Ra and Dec are know it's just simple trigonometry to convert those angles
into a vector.

`N_c` is straightforward: the axis of the Earth points North of course and that's just [0, 0, 1] 
in celestial coordinates. However, we need the vector towards North _along the ground_.  So as 
with the phone coordinates you need to take the 'vector rejection':

`N_c = z - (U_c . z) U_c`

where `z` = [0, 0, 1] and all the vectors are assumed to be normalized.
Lastly, `E_c = N_c X U_c`

## Putting it altogether

The direction the phone is pointing is `P` (with another vector `Q` to define the rotation of 
the phone about the `P` axis).  We want to use a transformation matrix `M` so that we can calculate

`P_c = M P_p`

and 

`Q_c = M Q_p`

How to find `M`?  We can calculate it from:

`[N_c | U_c | E_c] = M [N_p | U_p | E_p]`

Since these vectors are all normalised we can invert them by taking the transpose:

`M = [N_c | U_c | E_c] * [N_p | U_p | E_p]_T` 

So the only thing remaining is to figure out `P_p` and `Q_p` which are usually going to be

[0, 0, -1] and [0, 1, 0] respectively.

'Usually' because in certain circumstances you might want them to be different. For example if you
set `P_p` to be [0, 1, 0] and `Q_p` to be [0, 0, 1] you now will have the screen showing what the
long edge of the phone is pointing at. This can be useful if using Sky Map with a telescope - 
you can strap it to the telescope tube.

## Magnetic correction

All of the above assumes that the phones sensors give you true North, which they don't.
Instead we apply a magnetic correction (confusingly also called `declination`).

`R_mag` = rotation of declination degrees about `U`.

Rather than apply that correction to the magnetic north vector in phone coords we apply the 
opposite correction to (true) north in celestial coords. This is because the phone coordinate 
vectors are updated many times per second as the phone moves, while the celestial coordinates 
need only be updated when location or wall clock time changes significantly.


## The end
That's how the calculation is done in Sky Map. This is in part for historical reasons: when Sky 
Map was first written we had to figure everything out from scratch. These days there are plenty 
of nice helper functions in Android that we could almost certainly be using but are not.


## Legacy stuff

The Android team screwed up with the sensors initially.  They defined acceleration backwards
so that the phone lying flat on the table would have a *negative* acceleration. And they
inexplicably used a left hand coordinate system for the magnetic field sensor so Sky Map was
forced to negate the z-axis. This resulted in some bizarrely contorted documenation - read more 
here:

https://developer.android.com/reference/android/hardware/SensorListener

Relevant part:
"When the device is pushed on its left side toward the right, the x acceleration value is negative (the device applies a reaction force to the push toward the left)
When the device lies flat on a table, the acceleration value is -STANDARD_GRAVITY, which correspond to the force the device applies on the table in reaction to gravity.
SENSOR_MAGNETIC_FIELD:

All values are in micro-Tesla (uT) and measure the ambient magnetic field in the X, Y and -Z axis.

Note: the magnetic field's Z axis is inverted."

They fixed this when `SensorListener` was deprecated in favor of `SensorEventListener`.  This
was cleaned up in Sky Map in f47d668f9bf8264f6187d0312e6a35c108fa64d6 (after much head-scratching
trying to figure out why down was now up).



