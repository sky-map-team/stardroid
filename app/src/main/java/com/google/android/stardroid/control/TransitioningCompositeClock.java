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

import android.util.Log;

import com.google.android.stardroid.util.MiscUtil;

import java.util.Date;

/**
 * A clock that knows how to transition between a {@link TimeTravelClock}
 * and another {@link Clock}.  Usually this other
 * Clock will be a {@link RealClock}.
 *
 * @author John Taylor
 *
 */
public class TransitioningCompositeClock implements Clock {
  public static final long TRANSITION_TIME_MILLIS = 2500L;
  private static final String TAG = MiscUtil.getTag(TransitioningCompositeClock.class);
  private Clock realClock;
  private TimeTravelClock timeTravelClock;
  private enum Mode {REAL_TIME, TRANSITION, TIME_TRAVEL}
  private Mode mode = Mode.REAL_TIME;
  private long startTime;
  private long endTime;
  private long startTransitionWallTime;
  private Mode transitionTo;

  /**
   * Constructor.
   *
   * The realClock parameter serves two purposes - both as the clock to query
   * when in realtime mode, and also to count the beats during the transition
   * between realtime and timetravel modes to ensure a smooth transition.
   */
  public TransitioningCompositeClock(TimeTravelClock timeTravelClock,
                                     Clock realClock) {
    this.timeTravelClock = timeTravelClock;
    this.realClock = realClock;
  }

  public void goTimeTravel(Date targetDate) {
    startTime = getTimeInMillisSinceEpoch();
    endTime = targetDate.getTime();
    timeTravelClock.setTimeTravelDate(targetDate);
    mode = Mode.TRANSITION;
    transitionTo = Mode.TIME_TRAVEL;
    startTransitionWallTime = realClock.getTimeInMillisSinceEpoch();
  }

  public void returnToRealTime() {
    startTime = getTimeInMillisSinceEpoch();
    endTime = realClock.getTimeInMillisSinceEpoch() + TRANSITION_TIME_MILLIS;
    mode = Mode.TRANSITION;
    transitionTo = Mode.REAL_TIME;
    startTransitionWallTime = realClock.getTimeInMillisSinceEpoch();
  }

  @Override
  public long getTimeInMillisSinceEpoch() {
    if (mode == Mode.TRANSITION) {
      long elapsedTimeMillis = realClock.getTimeInMillisSinceEpoch() - startTransitionWallTime;
      if (elapsedTimeMillis > TRANSITION_TIME_MILLIS) {
        mode = transitionTo;
      } else {
        return (long) interpolate(startTime, endTime,
                                  ((double) elapsedTimeMillis) / TRANSITION_TIME_MILLIS);
      }
    }
    switch(mode) {
      case REAL_TIME:
        return realClock.getTimeInMillisSinceEpoch();
      case TIME_TRAVEL:
        return timeTravelClock.getTimeInMillisSinceEpoch();
    }
    Log.e(TAG, "Mode is neither realtime or timetravel - this should never happen");
    // While this will never happen - if it does let's just return real time.
    return realClock.getTimeInMillisSinceEpoch();
  }

  /**
   * An interpolation function to smoothly interpolate between start
   * at lambda = 0 and end at lambda = 1
   */
  public static double interpolate(double start, double end, double lambda) {
    return  (start + (3 * lambda * lambda - 2 * lambda * lambda * lambda) * (end - start));
  }
}
