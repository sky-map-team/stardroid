/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.buildlogic

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Desugars jars for RoboVM (the iOS shell, `:ios`), the way D8 desugars for old Android.
 *
 * RoboVM's class library is Java-7-era libcore, so a call to a static method Java 8 added to an
 * existing class — `Boolean.hashCode(boolean)` in every Kotlin data class with a `Boolean`
 * property, `Math.addExact(long, long)` in kotlinx-datetime's `Instant` arithmetic — compiles
 * but throws `NoSuchMethodError` on the device. This rewrites each such call site to the
 * identical-signature method on `RoboVmBackports` in `:ios`. Semantics are unchanged, so only
 * the iOS runtime classpath is transformed; Android and the JVM tests never see it.
 *
 * The `checkRoboVmApi` task is what finds new entries for [BACKPORTED]: it fails the build on any
 * missing API, and a missing static method with a straightforward Java 8 implementation belongs
 * here (plus in `RoboVmBackports`) rather than in its allowlist.
 */
@CacheableTransform
abstract class RoboVmBackportTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val output = outputs.file("${input.nameWithoutExtension}-robovm.jar")
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val bytes = zip.getInputStream(entry).readBytes()
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(if (entry.name.endsWith(".class")) backport(bytes) else bytes)
                    out.closeEntry()
                }
            }
        }
    }

    companion object {
        const val BACKPORTS_CLASS = "com/google/android/stardroid/ios/compat/RoboVmBackports"

        /** `owner.name(desc)` of the Java 8+ statics RoboVM lacks and RoboVmBackports provides. */
        val BACKPORTED =
            setOf(
                "java/lang/Boolean.hashCode(Z)I",
                "java/lang/Math.addExact(JJ)J",
                "java/lang/Math.multiplyExact(JJ)J",
            )

        /** Rewrites one class; returns the input as-is when it calls nothing in [BACKPORTED]. */
        fun backport(classBytes: ByteArray): ByteArray {
            val reader = ClassReader(classBytes)
            val writer = ClassWriter(reader, 0)
            val visitor = BackportingClassVisitor(writer)
            reader.accept(visitor, 0)
            return if (visitor.changed) writer.toByteArray() else classBytes
        }
    }
}

private class BackportingClassVisitor(
    next: ClassVisitor,
) : ClassVisitor(Opcodes.ASM9, next) {
    var changed = false

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val next = super.visitMethod(access, name, descriptor, signature, exceptions)
        return object : MethodVisitor(Opcodes.ASM9, next) {
            override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
                isInterface: Boolean,
            ) {
                val backported =
                    opcode == Opcodes.INVOKESTATIC &&
                        "$owner.$name$descriptor" in RoboVmBackportTransform.BACKPORTED
                if (!backported) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    return
                }
                changed = true
                // Prefixed by owner so equal names on different classes (Integer.hashCode vs
                // Boolean.hashCode) cannot collide.
                val target = owner.substringAfterLast('/').lowercase() + '_' + name
                super.visitMethodInsn(
                    opcode,
                    RoboVmBackportTransform.BACKPORTS_CLASS,
                    target,
                    descriptor,
                    false,
                )
            }
        }
    }
}
