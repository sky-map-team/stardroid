// Copyright 2008 Google Inc.
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
package com.google.android.stardroid.util

import com.google.android.stardroid.ApplicationConstants
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

/**
 * A collection of miscellaneous utility functions.
 *
 * @author Brent Bryan
 */
object MiscUtil {
    /** Returns the Tag for a class to be used in Android logging statements  */
    @JvmStatic
    fun getTag(o: Any): String {
        return if (o is Class<*>) {
            ApplicationConstants.APP_NAME + "." + o.simpleName
        } else ApplicationConstants.APP_NAME + "." + o.javaClass.simpleName
    }
}

/** Returns a date given the year, month and day in UTC.
 * The month is specified sanely, ie from 1.
 */
fun dateFromUtcHmd(y: Int, m: Int, d: Int) : Date {
    val localdate = LocalDate.of(y, m, d)
    val zonedDateTime = ZonedDateTime.of(localdate, LocalTime.MIDNIGHT, ZoneId.of("UTC"))
    val date = Date.from(zonedDateTime.toInstant())
    return date
}