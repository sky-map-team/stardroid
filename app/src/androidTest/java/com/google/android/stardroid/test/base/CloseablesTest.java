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

package com.google.android.stardroid.test.base;

import java.io.Closeable;
import java.io.IOException;

import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;

import com.google.android.stardroid.base.Closeables;

import junit.framework.TestCase;

/**
 * Unit tests for the Closeables class.
 *
 * @author Brent Bryan
 */
public class CloseablesTest extends TestCase {
  private static final String ERROR_STRING = "An Error String";
  
  private Closeable newMock(boolean throwException) throws IOException {
    Closeable closeable = EasyMock.createMock(Closeable.class);
    closeable.close();
    IExpectationSetters<Closeable> setter = EasyMock.expectLastCall();
    if (throwException) {
      setter.andThrow(new IOException(ERROR_STRING));
    }
    EasyMock.replay(closeable);
    return closeable;
  }
  
  public void testCloseSilently() throws IOException {
    // Case 1: Closeable is null.
    Closeables.closeSilently(null);
    
    // Case 2: Closeable is non-null and closes without exception. 
    Closeable c = newMock(false);
    Closeables.closeSilently(c);
    EasyMock.verify(c);

    // Case 3: Closeable is non-null and closes with exception. 
    c = newMock(true);
    Closeables.closeSilently(c);
    EasyMock.verify(c);
  }

  public void testCloseWithLog() throws IOException {
    // Case 1: Closeable is null.
    Closeables.closeWithLog(null);
    
    // Case 2: Closeable is non-null and closes without exception. 
    Closeable c = newMock(false);
    Closeables.closeWithLog(c);
    EasyMock.verify(c);

    // Case 3: Closeable is non-null and closes with exception. 
    // TODO(brent): Shall we write our own logging framework?
    c = newMock(true);
    Closeables.closeWithLog(c);
    EasyMock.verify(c);
  }
}
