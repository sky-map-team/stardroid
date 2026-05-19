package com.google.android.stardroid.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an instrumented test that should only be invoked from a local developer machine, not
 * from the GitHub Actions CI matrix. CI's {@code connectedGmsDebugAndroidTest} task is invoked
 * with {@code -Pandroid.testInstrumentationRunnerArguments.notAnnotation=...LocalOnlyTest} so
 * tests bearing this annotation are skipped.
 *
 * <p>Two categories of test currently use it:
 * <ul>
 *   <li>Screenshot tests driven by fastlane's screengrab, which need a screenshot pipeline
 *       configured outside of CI.
 *   <li>End-to-end paths that reach {@code DynamicStarMapActivity}, which saturates the main
 *       looper loading star catalogs and initialising OpenGL — see the comment on those tests
 *       in {@link StartUpTest} for details.
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LocalOnlyTest {
}
