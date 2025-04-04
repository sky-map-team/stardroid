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

import android.os.SystemClock;

import java.util.Calendar;

/**
 * Provides the current time.
 *
 * @author John Taylor
 */
public class RealClock implements Clock {
  @Override
  public long getTimeInMillisSinceEpoch() {
    Calendar currentTime = Calendar.getInstance();
    // For getting the time in UTC
    long offset = currentTime.get(Calendar.ZONE_OFFSET) +
            currentTime.get(Calendar.DST_OFFSET);
    return currentTime.getTimeInMillis() + offset;
  }
}
