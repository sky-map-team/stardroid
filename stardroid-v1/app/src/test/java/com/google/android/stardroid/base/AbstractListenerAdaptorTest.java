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

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.HashSet;
import java.util.List;

/**
 * Unittests for the AbstractListenerAdaptor.
 * 
 * @author Brent Bryan
 */
public class AbstractListenerAdaptorTest extends TestCase {
  private SimpleListenerAdapter adaptor = new SimpleListenerAdapter();

  /** A simple listener that just responds if an event has fired. */
  private interface SimpleListener {
    /** Indicates that an Event has fired. */
    public void eventFired();
  };

  /**
   * A Simple concrete implementation of an AbstractListenerAdapator for use in
   * testing.
   */
  private static class SimpleListenerAdapter extends AbstractListenerAdaptor<SimpleListener> {
    @Override
    protected void fireNewListenerAdded(SimpleListener listener) {
      listener.eventFired();
    }
  }

  /**
   * Creates a new mock SimpleListener that will expect to have eventFired call
   * numEventsFired number of times. If numEventsFired is negative, then the
   * mock will expect that eventFired to be called any number of times.
   * 
   * @param numEventsFired number of times the mock should expect eventFired to
   *        be called. If numEvents is negative, then the mock will allow
   *        eventFired to be called any number of times.
   * @return a new mock SimpleListener expecting eventFired to be called the
   *         specified number of times.
   */
  private SimpleListener createMockSimpleListener(int numEventsFired) {
    SimpleListener listener = EasyMock.createMock(SimpleListener.class);
    listener.eventFired();
    if (numEventsFired < 0) {
      EasyMock.expectLastCall().anyTimes();
    } else {
      EasyMock.expectLastCall().times(numEventsFired);
    }
    EasyMock.replay(listener);
    return listener;
  }

  /**
   * Ensures that when adding a listener that the count of the number of
   * listeners is correctly incremented and that eventFired is called
   * immediately on the listener.
   */
  public void testAddListener() {
    SimpleListener listener1 = createMockSimpleListener(1);
    SimpleListener listener2 = createMockSimpleListener(1);

    assertEquals(0, adaptor.getNumListeners());
    adaptor.addListener(listener1);
    assertEquals(1, adaptor.getNumListeners());
    adaptor.addListener(listener2);
    assertEquals(2, adaptor.getNumListeners());
    adaptor.addListener(listener1);
    assertEquals(2, adaptor.getNumListeners());
    
    List<SimpleListener> listeners = Lists.asList(adaptor.getListeners());
    assertEquals(2, listeners.size());
    assertTrue(listeners.contains(listener1));
    assertTrue(listeners.contains(listener2));
    
    EasyMock.verify(listener1, listener2);
  }

  public void testRemoveListener() {
    SimpleListener listener1 = createMockSimpleListener(1);
    SimpleListener listener2 = createMockSimpleListener(1);

    assertEquals(0, adaptor.getNumListeners());
    adaptor.addListener(listener1);
    adaptor.addListener(listener2);
    assertEquals(2, adaptor.getNumListeners());

    adaptor.removeListener(listener1);
    assertEquals(1, adaptor.getNumListeners());
    adaptor.removeListener(listener1);
    assertEquals(1, adaptor.getNumListeners());
    
    List<SimpleListener> listeners = Lists.asList(adaptor.getListeners());
    assertEquals(1, listeners.size());
    assertEquals(listener2, listeners.get(0));
    
    EasyMock.verify(listener1, listener2);
  }
  
  public void testRemoveAllListeners() {
    SimpleListener listener1 = createMockSimpleListener(1);
    SimpleListener listener2 = createMockSimpleListener(1);

    assertEquals(0, adaptor.getNumListeners());
    adaptor.addListener(listener1);
    adaptor.addListener(listener2);
    assertEquals(2, adaptor.getNumListeners());

    adaptor.removeAllListeners();
    assertEquals(0, adaptor.getNumListeners());
    assertTrue(Lists.asList(adaptor.getListeners()).isEmpty());

    EasyMock.verify(listener1, listener2);
  }

  public void testGetListeners() {
    HashSet<SimpleListener> expectedListeners = new HashSet<SimpleListener>();
    for (int i = 0; i < 4 ; i++) {
      SimpleListener listener = createMockSimpleListener(1); 
      expectedListeners.add(listener);
      
      assertEquals(i, adaptor.getNumListeners());
      adaptor.addListener(listener);
      assertEquals(i + 1, adaptor.getNumListeners());
    }
    
    List<SimpleListener> observedListeners = Lists.asList(adaptor.getListeners());
    assertEquals(4, observedListeners.size());
    
    for (SimpleListener listener : observedListeners) {
      assertTrue(String.format("observed unexpected listener: %s", listener), expectedListeners.remove(listener));
      EasyMock.verify(listener);
    }
    
    assertTrue(expectedListeners.isEmpty());
  }
}
