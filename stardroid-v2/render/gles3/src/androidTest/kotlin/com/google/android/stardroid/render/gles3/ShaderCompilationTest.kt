/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every shader this backend ships compiles and links against a real driver.
 *
 * A shader that fails to compile produces a black screen, no crash and nothing in the log —
 * historically the hardest GL failure to diagnose, and the one most likely to reach a user
 * before it reaches us. This turns that whole class of failure into a build failure.
 *
 * It runs against whatever driver the device or emulator provides, which is the point: the
 * GLSL ES compiler is part of the driver, so "it compiled on my machine" means very little.
 */
@RunWith(AndroidJUnit4::class)
class ShaderCompilationTest {
    private var gl: OffscreenGlContext? = null

    @Before
    fun setUp() {
        gl = OffscreenGlContext.createOrNull()
        // A device with no GL ES 3.0 runs the GLES1 backend and has no shaders to check. CI's
        // software-rendered images do support it; if one ever does not, this skips rather than
        // failing a build for something the device was never going to run.
        assumeTrue("No GL ES 3.0 context available on this device", gl != null)
    }

    @After
    fun tearDown() {
        gl?.release()
        gl = null
    }

    @Test
    fun everyProgramCompilesAndLinks() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val common =
            assets.open(ShaderProgram.COMMON_ASSET).use { String(it.readBytes()) }
        for (name in ShaderProgram.PROGRAM_NAMES) {
            // fromAssets throws with the driver's info log attached if either stage fails.
            val program = ShaderProgram.fromAssets(assets, name, common)
            assertThat(program.id).isNotEqualTo(0)
            program.release()
        }
    }

    @Test
    fun theSpriteProgramDeclaresTheAttributesTheBatchBinds() {
        // SpriteBatch's interleaved layout is written out by hand against these names; a rename
        // on either side would otherwise fail silently, as an unbound attribute reads zeroes and
        // draws every quad at the origin.
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val program = ShaderProgram.fromAssets(assets, "sprite")
        for (attrib in listOf("aCenterPx", "aSizePx", "aUv0", "aUv1", "aTint", "aMode")) {
            assertThat(program.attrib(attrib)).isAtLeast(0)
        }
        program.release()
    }

    @Test
    fun thePointProgramDeclaresTheAttributesTheDrawerBinds() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val program = ShaderProgram.fromAssets(assets, "point")
        for (attrib in listOf("aPos", "aColor", "aSizeDp", "aMagnitude")) {
            assertThat(program.attrib(attrib)).isAtLeast(0)
        }
        program.release()
    }
}
