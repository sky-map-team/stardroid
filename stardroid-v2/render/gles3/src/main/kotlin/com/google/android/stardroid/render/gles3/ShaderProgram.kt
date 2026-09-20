/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import android.content.res.AssetManager
import android.opengl.GLES30
import android.util.Log

/**
 * A compiled, linked GL program with its uniform locations cached.
 *
 * Uniform lookup by name is a driver string comparison, so doing it per draw is the kind of cost
 * that does not show up in a profile until there are hundreds of draws. Locations are resolved
 * once at link time and looked up from a map thereafter.
 *
 * **Failures are loud.** A shader that does not compile produces a black screen and nothing else
 * — historically the hardest GL bug to diagnose in the field — so a failure logs the driver's
 * info log at `Log.e` and throws. The shader-compilation instrumented test exists to turn that
 * into a CI failure rather than a field report.
 *
 * Must be constructed, used and released on the GL thread. GL programs die with the EGL context,
 * so after context loss these are simply rebuilt; there is nothing to free.
 */
class ShaderProgram private constructor(
    val name: String,
    val id: Int,
) {
    private val uniformLocations = HashMap<String, Int>()
    private val attribLocations = HashMap<String, Int>()

    fun use() {
        GLES30.glUseProgram(id)
    }

    /**
     * The location of [uniform], or -1 if the compiler optimised it away (which is legal and
     * common — `glUniform*` on -1 is a documented no-op, so callers need not branch).
     */
    fun uniform(uniform: String): Int =
        uniformLocations.getOrPut(uniform) {
            GLES30.glGetUniformLocation(id, uniform)
        }

    /** The location of vertex attribute [attrib], or -1 if it is unused. */
    fun attrib(attrib: String): Int =
        attribLocations.getOrPut(attrib) {
            GLES30.glGetAttribLocation(id, attrib)
        }

    fun release() {
        GLES30.glDeleteProgram(id)
    }

    companion object {
        private const val TAG = "SkyMapGles3"

        /** The GL ES version every shader in this backend is written against. */
        const val VERSION_DIRECTIVE = "#version 300 es"

        /** Helpers spliced into every stage, since GLSL ES has no `#include`. */
        const val COMMON_ASSET = "shaders/common.glsl"

        /** Every program this backend compiles at surface creation. */
        val PROGRAM_NAMES = listOf("point", "line", "sprite", "skyquad", "glow", "sky", "scrim")

        /**
         * Compiles and links `shaders/[name].vert` and `shaders/[name].frag` from [assets].
         *
         * The version directive and [COMMON_ASSET] are prepended here rather than repeated in
         * every file: `#version` must be the first line, so a literal include would have to come
         * after it in each of fourteen sources, and one file drifting out of step with the rest
         * is the kind of bug that shows up on one driver and nowhere else.
         *
         * @throws IllegalStateException if either stage fails to compile or the program fails to
         *   link, with the driver's info log attached.
         */
        fun fromAssets(
            assets: AssetManager,
            name: String,
            common: String = assets.open(COMMON_ASSET).use { String(it.readBytes()) },
        ): ShaderProgram =
            fromSource(
                name,
                compose(common, assets.readShader("$name.vert"), fragment = false),
                compose(common, assets.readShader("$name.frag"), fragment = true),
            )

        private fun AssetManager.readShader(fileName: String): String =
            open("shaders/$fileName").use { String(it.readBytes()) }

        /**
         * Assembles one stage: version directive, a default precision, the shared prelude, then
         * the stage's own source.
         *
         * The precision has to come before the prelude and it has to be stage-dependent. GLSL ES
         * gives a fragment shader no default `float` precision, so a prelude function returning
         * a float fails to compile unless something has declared one first — and there is no
         * standard macro a single prelude could branch on. A stage's own source may still
         * redeclare precision for its own bodies, which several do.
         */
        fun compose(
            common: String,
            source: String,
            fragment: Boolean,
        ): String =
            buildString {
                appendLine(VERSION_DIRECTIVE)
                if (fragment) {
                    appendLine("precision highp float;")
                    appendLine("precision highp int;")
                }
                appendLine(common)
                append(source)
            }

        fun fromSource(
            name: String,
            vertexSource: String,
            fragmentSource: String,
        ): ShaderProgram {
            val vertex = compile(name, GLES30.GL_VERTEX_SHADER, vertexSource)
            val fragment = compile(name, GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES30.glCreateProgram()
            check(program != 0) { "glCreateProgram failed for '$name'" }
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)

            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            // The shader objects are reference-counted by the program; detaching and deleting
            // them here frees the sources whether or not the link succeeded.
            GLES30.glDetachShader(program, vertex)
            GLES30.glDetachShader(program, fragment)
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
            if (status[0] == 0) {
                val log = GLES30.glGetProgramInfoLog(program)
                GLES30.glDeleteProgram(program)
                Log.e(TAG, "Failed to link program '$name': $log")
                error("Failed to link program '$name': $log")
            }
            return ShaderProgram(name, program)
        }

        private fun compile(
            name: String,
            type: Int,
            source: String,
        ): Int {
            val shader = GLES30.glCreateShader(type)
            check(shader != 0) { "glCreateShader failed for '$name'" }
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(shader)
                GLES30.glDeleteShader(shader)
                val stage = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
                Log.e(TAG, "Failed to compile $stage shader '$name': $log")
                error("Failed to compile $stage shader '$name': $log")
            }
            return shader
        }
    }
}
