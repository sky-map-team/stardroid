// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.control;

import static com.google.android.stardroid.util.Geometry.addVectors;
import static com.google.android.stardroid.util.Geometry.calculateRADecOfZenith;
import static com.google.android.stardroid.util.Geometry.matrixMultiply;
import static com.google.android.stardroid.util.Geometry.matrixVectorMultiply;
import static com.google.android.stardroid.util.Geometry.scalarProduct;
import static com.google.android.stardroid.util.Geometry.scaleVector;
import static com.google.android.stardroid.util.Geometry.vectorProduct;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.units.GeocentricCoordinates;
import com.google.android.stardroid.units.LatLong;
import com.google.android.stardroid.units.Matrix33;
import com.google.android.stardroid.units.RaDec;
import com.google.android.stardroid.units.Vector3;
import com.google.android.stardroid.util.Geometry;
import com.google.android.stardroid.util.MiscUtil;

import java.util.Date;

/**
 * The model of the astronomer.
 *
 * <p>Stores all the data about where and when he is and where he's looking and
 * handles translations between three frames of reference:
 * <ol>
 * <li>Celestial - a frame fixed against the background stars with
 * x, y, z axes pointing to (RA = 90, DEC = 0), (RA = 0, DEC = 0), DEC = 90
 * <li>Phone - a frame fixed in the phone with x across the short side, y across
 * the long side, and z coming out of the phone screen.
 * <li>Local - a frame fixed in the astronomer's local position. x is due east
 * along the ground y is due north along the ground, and z points towards the
 * zenith.
 * </ol>
 *
 * <p>We calculate the local frame in phone coords, and in celestial coords and
 * calculate a transform between the two.
 * In the following, N, E, U correspond to the local
 * North, East and Up vectors (ie N, E along the ground, Up to the Zenith)
 *
 * <p>In Phone Space: axesPhone = [N, E, U]
 *
 * <p>In Celestial Space: axesSpace = [N, E, U]
 *
 * <p>We find T such that axesCelestial = T * axesPhone
 *
 * <p>Then, [viewDir, viewUp]_celestial = T * [viewDir, viewUp]_phone
 *
 * <p>where the latter vector is trivial to calculate.
 *
 * <p>Implementation note: this class isn't making defensive copies and
 * so is vulnerable to clients changing its internal state.
 *
 * @author John Taylor
 */
public class AstronomerModelImpl implements AstronomerModel {
  private static final String TAG = MiscUtil.getTag(AstronomerModelImpl.class);
  private static final Vector3 POINTING_DIR_IN_PHONE_COORDS = new Vector3(0, 0, -1);
  private static final Vector3 SCREEN_UP_IN_PHONE_COORDS = new Vector3(0, 1, 0);
  private static final Vector3 AXIS_OF_EARTHS_ROTATION = new Vector3(0, 0, 1);
  private static final long MINIMUM_TIME_BETWEEN_CELESTIAL_COORD_UPDATES_MILLIS = 60000L;

  private MagneticDeclinationCalculator magneticDeclinationCalculator;
  private boolean autoUpdatePointing = true;
  private float fieldOfView = 45;  // Degrees
  private LatLong location = new LatLong(0f, 0f);
  private Clock clock = new RealClock();
  private long celestialCoordsLastUpdated = -1;

  /**
   * The pointing comprises a vector into the phone's screen expressed in
   * celestial coordinates combined with a perpendicular vector along the
   * phone's longer side.
   */
  private Pointing pointing = new Pointing();

  /** The sensor acceleration in the phone's coordinate system. */
  private Vector3 acceleration = ApplicationConstants.INITIAL_DOWN;

  /** The sensor magnetic field in the phone's coordinate system. */
  private Vector3 magneticField = ApplicationConstants.INITIAL_SOUTH;

  /** North along the ground in celestial coordinates. */
  private Vector3 trueNorthCelestial = new Vector3(1, 0, 0);

  /** Up in celestial coordinates. */
  private Vector3 upCelestial = new Vector3(0, 1, 0);

  /** East in celestial coordinates. */
  private Vector3 trueEastCelestial = AXIS_OF_EARTHS_ROTATION;

  /** [North, Up, East]^-1 in phone coordinates. */
  private Matrix33 axesPhoneInverseMatrix = Matrix33.getIdMatrix();

  /** [North, Up, East] in celestial coordinates. */
  private Matrix33 axesMagneticCelestialMatrix = Matrix33.getIdMatrix();

  /**
   * @param magneticDeclinationCalculator A calculator that will provide the
   * magnetic correction from True North to Magnetic North.
   */
  public AstronomerModelImpl(MagneticDeclinationCalculator magneticDeclinationCalculator) {
    setMagneticDeclinationCalculator(magneticDeclinationCalculator);
  }

  @Override
  public void setAutoUpdatePointing(boolean autoUpdatePointing) {
    this.autoUpdatePointing = autoUpdatePointing;
  }

  @Override
  public float getFieldOfView() {
    return fieldOfView;
  }

  @Override
  public void setFieldOfView(float degrees) {
    fieldOfView = degrees;
  }

  @Override
  public Date getTime() {
    return new Date(clock.getTimeInMillisSinceEpoch());
  }

  @Override
  public LatLong getLocation() {
    return location;
  }

  @Override
  public void setLocation(LatLong location) {
    this.location = location;
    calculateLocalNorthAndUpInCelestialCoords(true);
  }

  @Override
  public Vector3 getPhoneAcceleration() {
    return acceleration;
  }

  @Override
  public void setPhoneSensorValues(Vector3 acceleration, Vector3 magneticField) {
    this.acceleration.assign(acceleration);
    this.magneticField.assign(magneticField);
  }

  @Override
  public GeocentricCoordinates getNorth() {
    calculateLocalNorthAndUpInCelestialCoords(false);
    return GeocentricCoordinates.getInstanceFromVector3(trueNorthCelestial);
  }

  @Override
  public GeocentricCoordinates getSouth() {
    calculateLocalNorthAndUpInCelestialCoords(false);
    return GeocentricCoordinates.getInstanceFromVector3(Geometry.scaleVector(trueNorthCelestial,
                                                                             -1));
  }

  @Override
  public GeocentricCoordinates getZenith() {
    calculateLocalNorthAndUpInCelestialCoords(false);
    return GeocentricCoordinates.getInstanceFromVector3(upCelestial);
  }

  @Override
  public GeocentricCoordinates getNadir() {
    calculateLocalNorthAndUpInCelestialCoords(false);
    return GeocentricCoordinates.getInstanceFromVector3(Geometry.scaleVector(upCelestial, -1));
  }

  @Override
  public GeocentricCoordinates getEast() {
    calculateLocalNorthAndUpInCelestialCoords(false);
    return GeocentricCoordinates.getInstanceFromVector3(trueEastCelestial);
  }

  @Override
  public GeocentricCoordinates getWest() {
    calculateLocalNorthAndUpInCelestialCoords(false);
    return GeocentricCoordinates.getInstanceFromVector3(Geometry.scaleVector(trueEastCelestial,
                                                                             -1));
  }

  @Override
  public void setMagneticDeclinationCalculator(MagneticDeclinationCalculator calculator) {
    this.magneticDeclinationCalculator = calculator;
    calculateLocalNorthAndUpInCelestialCoords(true);
  }

  /**
   * Updates the astronomer's 'pointing', that is, the direction the phone is
   * facing in celestial coordinates and also the 'up' vector along the
   * screen (also in celestial coordinates).
   *
   * <p>This method requires that {@link #axesMagneticCelestialMatrix} and
   * {@link #axesPhoneInverseMatrix} are currently up to date.
   */
  private void calculatePointing() {
    if (!autoUpdatePointing) {
      return;
    }

    Matrix33 transform = matrixMultiply(axesMagneticCelestialMatrix, axesPhoneInverseMatrix);

    Vector3 viewInSpaceSpace = matrixVectorMultiply(transform, POINTING_DIR_IN_PHONE_COORDS);
    Vector3 screenUpInSpaceSpace = matrixVectorMultiply(transform, SCREEN_UP_IN_PHONE_COORDS);

    pointing.updateLineOfSight(viewInSpaceSpace);
    pointing.updatePerpendicular(screenUpInSpaceSpace);
  }

  /**
   * Calculates local North, East and Up vectors in terms of the celestial
   * coordinate frame.
   */
  private void calculateLocalNorthAndUpInCelestialCoords(boolean forceUpdate) {
    long currentTime = clock.getTimeInMillisSinceEpoch();
    if (!forceUpdate &&
        Math.abs(currentTime - celestialCoordsLastUpdated) <
        MINIMUM_TIME_BETWEEN_CELESTIAL_COORD_UPDATES_MILLIS) {
      return;
    }
    celestialCoordsLastUpdated = currentTime;
    updateMagneticCorrection();
    RaDec up = calculateRADecOfZenith(getTime(), location);
    upCelestial = GeocentricCoordinates.getInstance(up);
    Vector3 z = AXIS_OF_EARTHS_ROTATION;
    float zDotu = scalarProduct(upCelestial, z);
    trueNorthCelestial = addVectors(z, scaleVector(upCelestial, -zDotu));
    trueNorthCelestial.normalize();
    trueEastCelestial = Geometry.vectorProduct(trueNorthCelestial, upCelestial);

    // Apply magnetic correction.  Rather than correct the phone's axes for
    // the magnetic declination, it's more efficient to rotate the
    // celestial axes by the same amount in the opposite direction.
    Matrix33 rotationMatrix = Geometry.calculateRotationMatrix(
        magneticDeclinationCalculator.getDeclination(), upCelestial);

    Vector3 magneticNorthCelestial = Geometry.matrixVectorMultiply(rotationMatrix,
                                                                   trueNorthCelestial);
    Vector3 magneticEastCelestial = vectorProduct(magneticNorthCelestial, upCelestial);

    axesMagneticCelestialMatrix = new Matrix33(magneticNorthCelestial,
                                               upCelestial,
                                               magneticEastCelestial);
  }

  /**
   * Calculates local North and Up vectors in terms of the phone's coordinate
   * frame.
   */
  private void calculateLocalNorthAndUpInPhoneCoords() {
    // TODO(johntaylor): we can reduce the number of vector copies done in here.
    Vector3 down = acceleration.copy();
    down.normalize();
    // Magnetic field goes *from* North to South, so reverse it.
    Vector3 magneticFieldToNorth = magneticField.copy();
    magneticFieldToNorth.scale(-1);
    magneticFieldToNorth.normalize();
    // This is the vector to magnetic North *along the ground*.
    Vector3 magneticNorthPhone = addVectors(magneticFieldToNorth,
                                 scaleVector(down, -scalarProduct(magneticFieldToNorth, down)));
    magneticNorthPhone.normalize();
    Vector3 upPhone = scaleVector(down, -1);
    Vector3 magneticEastPhone = vectorProduct(magneticNorthPhone, upPhone);

    // The matrix is orthogonal, so transpose it to find its inverse.
    // Easiest way to do that is to construct it from row vectors instead
    // of column vectors.
    axesPhoneInverseMatrix = new Matrix33(magneticNorthPhone, upPhone, magneticEastPhone, false);
  }

  /**
   * Updates the angle between True North and Magnetic North.
   */
  private void updateMagneticCorrection() {
    magneticDeclinationCalculator.setLocationAndTime(location, getTimeMillis());
  }

  /**
   * Returns the user's pointing.  Note that clients should not usually modify this
   * object as it is not defensively copied.
   */
  @Override
  public Pointing getPointing() {
    calculateLocalNorthAndUpInPhoneCoords();
    calculatePointing();
    return pointing;
  }

  @Override
  public void setPointing(Vector3 lineOfSight, Vector3 perpendicular) {
    this.pointing.updateLineOfSight(lineOfSight);
    this.pointing.updatePerpendicular(perpendicular);
  }

  @Override
  public void setClock(Clock clock) {
    this.clock = clock;
    calculateLocalNorthAndUpInCelestialCoords(true);
  }

  @Override
  public long getTimeMillis() {
    return clock.getTimeInMillisSinceEpoch();
  }
}
