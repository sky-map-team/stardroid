/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class ObjectLinkTest {
    @Test
    fun `a built link reads back as its id`() {
        val link = ObjectLink.build("skymap", MOON_OBJECT_ID)
        assertThat(link).isEqualTo("skymap://object/planet/moon")
        assertThat(ObjectLink.objectIdOf(link)).isEqualTo(MOON_OBJECT_ID)
    }

    @Test
    fun `any scheme is read so dev and release builds share the parser`() {
        assertThat(ObjectLink.objectIdOf("skymap-dev://object/planet/moon"))
            .isEqualTo(MOON_OBJECT_ID)
    }

    @Test
    fun `a query or fragment or trailing slash is not part of the id`() {
        assertThat(ObjectLink.objectIdOf("skymap://object/planet/moon/?src=widget#x"))
            .isEqualTo(MOON_OBJECT_ID)
    }

    @Test
    fun `other links are not object links`() {
        assertThat(ObjectLink.objectIdOf("skymap://settings")).isNull()
        assertThat(ObjectLink.objectIdOf("skymap://object/")).isNull()
        assertThat(ObjectLink.objectIdOf("planet/moon")).isNull()
    }

    @Test
    fun `the tonight route builds and is recognized`() {
        assertThat(ObjectLink.tonight("skymap")).isEqualTo("skymap://tonight")
        assertThat(ObjectLink.isTonight("skymap://tonight")).isTrue()
        assertThat(ObjectLink.isTonight("skymap://tonight/?x=1")).isTrue()
        assertThat(ObjectLink.isTonight("skymap://object/moon")).isFalse()
        assertThat(ObjectLink.objectIdOf("skymap://tonight")).isNull()
    }
}
