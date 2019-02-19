// Copyright 2009 Google Inc.
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

package com.google.android.stardroid.test.control;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.android.stardroid.control.Controller;
import com.google.android.stardroid.control.ControllerGroup;

import junit.framework.TestCase;

/**
 * A test suite for the {@link ControllerGroup}'s 'aggregate' methods such as
 * start(), stop().
 *
 * @author John Taylor
 */
public class ControllerGroupTest extends TestCase {
  private Controller controller1;
  private Controller controller2;
  private ControllerGroup controllerGroup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    controllerGroup = new ControllerGroup();
    controller1 = createMock(Controller.class);
    controller2 = createMock(Controller.class);
    controllerGroup.addController(controller1);
    controllerGroup.addController(controller2);
  }

  public void testStart_startsSubControllers() {
    controller1.start();
    controller2.start();
    replay(controller1, controller2);
    controllerGroup.start();
    verify(controller1, controller2);
  }

  public void testStop_stopsSubControllers() {
    controller1.stop();
    controller2.stop();
    replay(controller1, controller2);
    controllerGroup.stop();
    verify(controller1, controller2);
  }

  public void testSetEnabled_enablesSubControllers() {
    checkWhenControllerGroupEnabled(true);
  }

  public void testSetDisabled_disablesSubControllers() {
    checkWhenControllerGroupEnabled(false);
  }

  private void checkWhenControllerGroupEnabled(boolean enabled) {
    controller1.setEnabled(enabled);
    controller2.setEnabled(enabled);
    replay(controller1, controller2);
    controllerGroup.setEnabled(enabled);
    verify(controller1, controller2);
  }
}
