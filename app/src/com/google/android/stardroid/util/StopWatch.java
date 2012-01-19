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

package com.google.android.stardroid.util;

/**
 * Interface for StopWatchImpl, extracted purely for testing purposes.
 * @author Brent Bryan
 * @author John Taylor
 */
public interface StopWatch {

  StopWatch start();

  StopWatch stop();

  boolean isRunning();

  StopWatch clear();

  /**
   * Returns the total elapsed time the watch has been running since creation, or since the last
   * clear() call was made.
   */
  long getElapsedTime();

  /**
   * Return the time since start was called. If the StopWatch is not running, returns 0.
   */
  long getRunningTime();

  String formatTime();

  /**
   * Calls stop() and then clear()'s the watch, returning a human readable version of the elapsed
   * time (before it was cleared).
   */
  String end();

  String endStart();

}
