/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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
package com.google.android.stardroid.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiscUtilTest {
    @Test
    fun testCapitalize() {
        assertThat(MiscUtil.capitalize("mars")).isEqualTo("Mars")
        assertThat(MiscUtil.capitalize("Mars")).isEqualTo("Mars")
        assertThat(MiscUtil.capitalize("MARS")).isEqualTo("Mars")
        assertThat(MiscUtil.capitalize("m31")).isEqualTo("M31")
        assertThat(MiscUtil.capitalize("")).isEqualTo("")
        assertThat(MiscUtil.capitalize(null)).isEqualTo("")
        assertThat(MiscUtil.capitalize("alpha centauri")).isEqualTo("Alpha centauri")
        assertThat(MiscUtil.capitalize("ALPHA CENTAURI")).isEqualTo("Alpha centauri")
    }
}
