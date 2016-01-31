// Copyright 2009 Google Inc.
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
 * This class defines all exceptions thrown by the Preconditions class.
 * PreconditionExceptions represent logical failures in the code or in specified
 * data, and hence should probably not be caught.
 * 
 * @author Brent Bryan
 */
public class PreconditionException extends RuntimeException {

  /** Serialization ID */
  private static final long serialVersionUID = -7933332361093863845L;

  /**
   * Constructs a new PreconditionException based on the failed Prediction check
   * described in the given message.
   * 
   * @param message reason this PreconditionException is being thrown.
   */
  public PreconditionException(String message) {
    super(message);
  }

  /**
   * Constructs a new PreconditionException based on the failed Prediction check
   * described in the given message.
   * 
   * @param messageFormat string format used to format this message
   * @param args arguments describing this Precondition failure to fill into the
   *        messageFormat.
   */
  public PreconditionException(String messageFormat, Object... args) {
    super(String.format(messageFormat, args));
  }
}
