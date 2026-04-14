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

package com.google.android.stardroid.control;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * Test suite for the {@link ZoomController}.
 *
 * @author John Taylor
 *
 * Tests that require roboelectric for API calls.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public class ZoomControllerTest {
  private static final float INITIAL_FIELD_OF_VIEW = 30.0f;

  private AstronomerModel astronomerModel;
  private ZoomController zoomController;

  @Before
  public void setUp() throws Exception {
    astronomerModel = createMock(AstronomerModel.class);
    zoomController = new ZoomController();
    zoomController.setModel(astronomerModel);
  }

  /**
   * Tests that the maximum field of view is not exceeded.
   */
  @Test
  public void testZoomOut_tooFar() {
    float newFieldOfView = ZoomController.MAX_ZOOM_OUT;
    expect(astronomerModel.getFieldOfView()).andStubReturn(INITIAL_FIELD_OF_VIEW);
    astronomerModel.setFieldOfView(newFieldOfView);

    replay(astronomerModel);

    zoomController.zoomBy(1000);
    verify(astronomerModel);
  }

  @Test
  public void testZoomIn_modelNotUpdatedWhenControllerNotEnabled() {
    expect(astronomerModel.getFieldOfView()).andReturn(INITIAL_FIELD_OF_VIEW);
    // Note that setFieldOfView will not be called

    replay(astronomerModel);

    zoomController.setEnabled(false);
    zoomController.zoomBy(0.9f);
    verify(astronomerModel);
  }
}
