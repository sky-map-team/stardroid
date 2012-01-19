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

package com.google.android.stardroid.test.util;

import com.google.android.stardroid.base.Provider;
import com.google.android.stardroid.util.StopWatch;

import java.util.List;

/**
 * Implementation of a {@link StopWatch} {@link Provider} which sequentially
 * passes out the given StopWatch instances.
 *
 * @author Brent Bryan
 */
public class ListStopWatchProvider implements Provider<StopWatch> {
  private final List<StopWatch> watches;
  private int index = 0;

  public ListStopWatchProvider(List<StopWatch> watches) {
    this.watches = watches;
  }

  @Override
  public StopWatch get() {
    return watches.get(index++);
  }
}
