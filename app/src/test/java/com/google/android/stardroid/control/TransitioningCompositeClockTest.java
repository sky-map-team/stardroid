// Copyright 2010 Google Inc.
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

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;

/**
 * Tests for the {@link TransitioningCompositeClock}.
 * 
 * @author John Taylor
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest= Config.NONE)
public class TransitioningCompositeClockTest extends TestCase {
  /**
   * A fake clock for which we can set the time.
   * 
   * @author John Taylor
   */
  private static class FakeClock implements Clock {
    private long time;

    @Override
    public long getTimeInMillisSinceEpoch() {
      return time;
    }

    public void setTimeInMillisSinceEpoch(long time) {
      this.time = time;
    }

    public void advanceTimeByMillis(long deltaTime) {
      time += deltaTime;
    }
  }

  @Test
  public void testInterpolant() {
    double tol = 1e-3;
    assertEquals(0, TransitioningCompositeClock.interpolate(0, 10, 0), tol);
    assertEquals(1, TransitioningCompositeClock.interpolate(1, 10, 0), tol);
    assertEquals(10, TransitioningCompositeClock.interpolate(0, 10, 1), tol);
    assertEquals(5, TransitioningCompositeClock.interpolate(0, 10, 0.5), tol);
    // Test derivatives
    double epsilon = 1e-4;
    double dydx0 = (TransitioningCompositeClock.interpolate(0, 1, epsilon)
        - TransitioningCompositeClock.interpolate(0, 1, 0)) / epsilon;
    assertEquals(0.0, dydx0, tol);
    double dydx1 = (TransitioningCompositeClock.interpolate(0, 1, 1)
        - TransitioningCompositeClock.interpolate(0, 1, 1 - epsilon)) / epsilon;
    assertEquals(0.0, dydx1, tol);
  }

  @Test
  public void testTransition() {
    TimeTravelClock timeTravelClock = new TimeTravelClock();
    FakeClock fakeClock = new FakeClock();
    TransitioningCompositeClock transitioningClock = new TransitioningCompositeClock(
        timeTravelClock, fakeClock);
    fakeClock.setTimeInMillisSinceEpoch(1000);
    // Transitioning clock starts in real time
    assertEquals(1000, transitioningClock.getTimeInMillisSinceEpoch());
    fakeClock.setTimeInMillisSinceEpoch(2000);
    assertEquals(2000, transitioningClock.getTimeInMillisSinceEpoch());
    Date timeTravelDate = new Date(5000);
    transitioningClock.goTimeTravel(timeTravelDate);
    // We shouldn't have budged
    assertEquals(2000, transitioningClock.getTimeInMillisSinceEpoch());
    fakeClock.advanceTimeByMillis(TransitioningCompositeClock.TRANSITION_TIME_MILLIS / 2);
    // Half way there
    assertEquals(3500, transitioningClock.getTimeInMillisSinceEpoch());
    fakeClock.advanceTimeByMillis(TransitioningCompositeClock.TRANSITION_TIME_MILLIS / 2);
    // All the way there
    assertEquals(5000, transitioningClock.getTimeInMillisSinceEpoch());
    // Where we stay...
    fakeClock.advanceTimeByMillis(1000);
    assertEquals(5000, transitioningClock.getTimeInMillisSinceEpoch());

    transitioningClock.returnToRealTime();
    long destinationTime = fakeClock.getTimeInMillisSinceEpoch()
        + TransitioningCompositeClock.TRANSITION_TIME_MILLIS;
    // Shouldn't have moved yet
    assertEquals(5000, transitioningClock.getTimeInMillisSinceEpoch());
    fakeClock.advanceTimeByMillis(TransitioningCompositeClock.TRANSITION_TIME_MILLIS / 2);
    // Half way there
    assertEquals((5000 + destinationTime) / 2,
                 transitioningClock.getTimeInMillisSinceEpoch());
    fakeClock.advanceTimeByMillis(TransitioningCompositeClock.TRANSITION_TIME_MILLIS / 2);
    // All the way there
    assertEquals(destinationTime,
                 transitioningClock.getTimeInMillisSinceEpoch());
    fakeClock.advanceTimeByMillis(1000);
    // Continue to advance in real time.
    assertEquals(fakeClock.getTimeInMillisSinceEpoch(),
                 transitioningClock.getTimeInMillisSinceEpoch());
  }

}
