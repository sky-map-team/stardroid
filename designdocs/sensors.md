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
surface of the Earth as we need it.




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

They fixed this when `SensorListener` was deprecated in favor of `SensorEventListener`.



