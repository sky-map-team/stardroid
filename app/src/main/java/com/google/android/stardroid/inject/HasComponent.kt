package com.google.android.stardroid.inject

/**
 * Implemented by activities to access their dagger component.
 * Created by johntaylor on 4/9/16.
 */
interface HasComponent<C> {
  val component: C
}