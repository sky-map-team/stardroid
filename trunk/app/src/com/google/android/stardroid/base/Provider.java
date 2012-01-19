// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.base;

/**
 * An object capable of providing instances of types T.
 *
 * @param <T> type of object to be provided
 *
 * @author Brent Bryan
 */
public interface Provider<T> {

  /**
   * Provides an instance of type T. Implementors may choose to either return a
   * new instance upon every call, or provide the same instance for all calls.
   */
  T get();
}
