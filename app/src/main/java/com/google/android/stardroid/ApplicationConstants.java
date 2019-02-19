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

package com.google.android.stardroid;

import com.google.android.stardroid.units.Vector3;

/**
 * A home for the application's few global constants.
 */
public class ApplicationConstants {

  public static final String APP_NAME = "Stardroid";
  /** Default value for 'south' in phone coords when the app starts */
  public static final Vector3 INITIAL_SOUTH = new Vector3(0, -1, 0);
  /** Default value for 'down' in phone coords when the app starts */
  public static final Vector3 INITIAL_DOWN = new Vector3(0, -1, -9);

}
