/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [WidgetScheduler]'s "jobs run iff a widget is placed" rule (D75), against the real
 * WorkManager. Placement is passed in rather than faked on the launcher — a test can't pin a
 * widget — so this covers the scheduling decision, not the placement query.
 */
@RunWith(AndroidJUnit4::class)
class WidgetSchedulerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workManager get() = WorkManager.getInstance(context)
    private val periodicJobs =
        listOf(WidgetScheduler.PERIODIC_WORK_NAME, WidgetScheduler.MIDNIGHT_WORK_NAME)

    @Before
    fun clean() = cancelAll()

    /** Puts the app's own placement-driven schedule back for whatever runs next. */
    @After
    fun restore() {
        cancelAll()
        WidgetScheduler.syncSchedule(context)
    }

    private fun cancelAll() {
        for (name in periodicJobs + WidgetScheduler.REFRESH_ONCE_WORK_NAME) {
            workManager.cancelUniqueWork(name).result.get()
        }
        workManager.pruneWork().result.get()
    }

    private fun live(name: String) =
        workManager
            .getWorkInfosForUniqueWork(name)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    @Test
    fun a_placed_widget_keeps_both_periodic_jobs() {
        WidgetScheduler.syncSchedule(context, anyPlaced = true)

        for (name in periodicJobs) assertThat(live(name)).hasSize(1)
    }

    @Test
    fun removing_the_last_widget_cancels_both_periodic_jobs() {
        WidgetScheduler.syncSchedule(context, anyPlaced = true)

        WidgetScheduler.syncSchedule(context, anyPlaced = false)

        for (name in periodicJobs) assertThat(live(name)).isEmpty()
    }

    @Test
    fun syncing_twice_does_not_duplicate_the_jobs() {
        WidgetScheduler.syncSchedule(context, anyPlaced = true)
        WidgetScheduler.syncSchedule(context, anyPlaced = true)

        for (name in periodicJobs) assertThat(live(name)).hasSize(1)
    }

    @Test
    fun app_start_refreshes_when_a_widget_is_placed() {
        WidgetScheduler.refreshIfPlaced(context, anyPlaced = true)

        assertThat(workManager.getWorkInfosForUniqueWork(WidgetScheduler.REFRESH_ONCE_WORK_NAME).get())
            .isNotEmpty()
    }

    @Test
    fun app_start_does_nothing_when_no_widget_is_placed() {
        WidgetScheduler.refreshIfPlaced(context, anyPlaced = false)

        assertThat(workManager.getWorkInfosForUniqueWork(WidgetScheduler.REFRESH_ONCE_WORK_NAME).get())
            .isEmpty()
    }
}
