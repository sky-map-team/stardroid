/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.apicheck

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import java.io.File
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/**
 * Checks that every JDK API the iOS app can reach exists in RoboVM's class library.
 *
 * RoboVM's runtime is Android's Java-7-era libcore: no `java.util.function`, no streams, no
 * `java.time` (the relocated ThreeTen-BP supplies that), and none of the Java 8+ methods added
 * to existing classes. The RoboVM compiler only warns about missing *classes*; a missing *method
 * or field* compiles fine and fails as `NoSuchMethodError` on the device. This walks the call
 * graph from the project's own classes through the app classpath (Kotlin stdlib, kotlinx-*,
 * ...) and resolves every reference that lands in the boot classpath, so a Java 8+ API creeping
 * into a pure module fails the build on any OS — no Mac required.
 *
 * Reachability is method-level but conservative about dynamic dispatch: once a class is
 * instantiated all its methods count as reachable, since any may be called through an
 * interface or a supertype (`toString`, `run`, `compareTo`, ...).
 *
 * Usage: `RoboVmApiCheck <boot-cp> <app-cp> <root-prefixes> <allowlist> <report>`, the paths
 * `File.pathSeparator`-separated, the prefixes comma-separated in internal (`a/b/`) form.
 */
fun main(args: Array<String>) {
    require(args.size == 5) { "usage: <boot-cp> <app-cp> <root-prefixes> <allowlist> <report>" }
    val boot = ClassPool(args[0].splitPaths(), withCode = false)
    val app = ClassPool(args[1].splitPaths(), withCode = true)
    val roots = args[2].split(',').filter { it.isNotBlank() }
    val allowlist = Allowlist.read(File(args[3]))

    val problems = ApiChecker(boot, app).check(roots)
    val (allowed, failures) = problems.partition { allowlist.allows(it.ref) }

    val report =
        buildString {
            appendLine("RoboVM API check: ${failures.size} problem(s), ${allowed.size} allowlisted")
            for (p in failures) appendLine("MISSING ${p.describe()}")
            for (p in allowed) appendLine("allowed ${p.describe()}")
        }
    File(args[4]).apply { parentFile.mkdirs() }.writeText(report)
    if (failures.isNotEmpty()) {
        System.err.print(report)
        exitProcess(1)
    }
    print(report.lineSequence().first() + "\n")
}

private fun String.splitPaths() = split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)

/** A missing class (`java/util/function/Function`) or member (`java/lang/Math.floorMod(II)I`). */
data class Problem(val ref: String, val from: String) {
    fun describe() = "$ref  (first reached from $from)"
}

/** Lines are prefixes of [Problem.ref]s to accept; `#` starts a comment. */
class Allowlist(private val prefixes: List<String>) {
    fun allows(ref: String) = prefixes.any { ref.startsWith(it) }

    companion object {
        fun read(file: File) =
            Allowlist(
                file.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() },
            )
    }
}

/** Every class on a classpath, parsed lazily. Later entries never shadow earlier ones. */
class ClassPool(
    entries: List<File>,
    private val withCode: Boolean,
) {
    private val bytes = HashMap<String, ByteArray>()
    private val parsed = HashMap<String, ClassNode>()

    init {
        for (entry in entries) {
            when {
                entry.isDirectory ->
                    entry
                        .walkTopDown()
                        .filter { it.isFile && it.name.endsWith(".class") }
                        .forEach { f ->
                            bytes.putIfAbsent(
                                f.relativeTo(entry).invariantSeparatorsPath.removeSuffix(".class"),
                                f.readBytes(),
                            )
                        }
                entry.isFile && entry.name.endsWith(".jar") ->
                    ZipFile(entry).use { zip ->
                        for (e in zip.entries()) {
                            val name = e.name
                            if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue
                            val key = name.removeSuffix(".class")
                            if (key !in bytes) bytes[key] = zip.getInputStream(e).readBytes()
                        }
                    }
            }
        }
    }

    val names: Set<String> get() = bytes.keys

    operator fun contains(name: String) = name in bytes

    operator fun get(name: String): ClassNode? =
        parsed[name] ?: bytes[name]?.let { b ->
            ClassNode().also {
                val flags = if (withCode) 0 else ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG
                ClassReader(b).accept(it, flags)
                parsed[name] = it
            }
        }
}

class ApiChecker(
    private val boot: ClassPool,
    private val app: ClassPool,
) {
    private val reachedMethods = HashSet<String>()
    private val instantiated = HashSet<String>()
    private val initialised = HashSet<String>()
    private val queue = ArrayDeque<Pair<ClassNode, MethodNode>>()
    private val problems = LinkedHashMap<String, Problem>()

    fun check(rootPrefixes: List<String>): List<Problem> {
        app.names
            .filter { name -> rootPrefixes.any { name.startsWith(it) } }
            .forEach { name -> app[name]?.let { cls -> cls.methods.forEach { enqueue(cls, it) } } }
        while (queue.isNotEmpty()) {
            val (cls, method) = queue.removeFirst()
            scan(cls, method)
        }
        return problems.values.toList()
    }

    private fun enqueue(
        cls: ClassNode,
        method: MethodNode,
    ) {
        if (reachedMethods.add("${cls.name}.${method.name}${method.desc}")) queue += cls to method
    }

    private fun scan(
        cls: ClassNode,
        method: MethodNode,
    ) {
        val site = "${cls.name}.${method.name}"
        method.tryCatchBlocks.forEach { it.type?.let { t -> requireClass(t, site) } }
        for (insn in method.instructions) {
            when (insn) {
                is MethodInsnNode -> reachMethod(insn.owner, insn.name, insn.desc, site)
                is FieldInsnNode -> reachField(insn.owner, insn.name, insn.desc, site)
                is TypeInsnNode -> {
                    requireType(insn.desc, site)
                    if (insn.opcode == Opcodes.NEW) instantiate(insn.desc)
                }
                is LdcInsnNode -> (insn.cst as? Type)?.let { requireType(it.descriptor, site) }
                is MultiANewArrayInsnNode -> requireType(insn.desc, site)
                is InvokeDynamicInsnNode -> {
                    reachHandle(insn.bsm, site)
                    insn.bsmArgs.filterIsInstance<Handle>().forEach { reachHandle(it, site) }
                }
            }
        }
    }

    private fun reachHandle(
        handle: Handle,
        site: String,
    ) {
        if (handle.tag == Opcodes.H_NEWINVOKESPECIAL) instantiate(handle.owner)
        if (handle.tag <= Opcodes.H_PUTSTATIC) {
            reachField(handle.owner, handle.name, handle.desc, site)
        } else {
            reachMethod(handle.owner, handle.name, handle.desc, site)
        }
    }

    private fun instantiate(name: String) {
        if (!instantiated.add(name)) return
        var current: String? = name
        while (current != null && current in app) {
            val cls = app[current]!!
            cls.methods.forEach { enqueue(cls, it) }
            current = cls.superName
        }
    }

    private fun initialise(name: String) {
        if (!initialised.add(name)) return
        val cls = app[name] ?: return
        cls.methods.find { it.name == "<clinit>" }?.let { enqueue(cls, it) }
    }

    private fun reachMethod(
        owner: String,
        name: String,
        desc: String,
        site: String,
    ) {
        if (owner.startsWith("[")) return requireType(owner, site) // clone() etc. on arrays
        if (!requireClass(owner, site)) return
        if (owner == METHOD_HANDLE || owner == VAR_HANDLE) return // signature-polymorphic
        initialise(owner)
        when (
            val found =
                resolve(owner) { cls ->
                    cls.methods.any {
                        it.name == name && it.desc == desc
                    }
                }
        ) {
            null -> report("$owner.$name$desc", site)
            else ->
                if (found.first === app) {
                    app[found.second]?.let { cls ->
                        cls.methods.find { it.name == name && it.desc == desc }?.let {
                            enqueue(
                                cls,
                                it,
                            )
                        }
                    }
                }
        }
    }

    private fun reachField(
        owner: String,
        name: String,
        desc: String,
        site: String,
    ) {
        if (!requireClass(owner, site)) return
        initialise(owner)
        val declaresField = {
                cls: ClassNode ->
            cls.fields.any { it.name == name && it.desc == desc }
        }
        if (resolve(owner, declaresField) == null) report("$owner.$name:$desc", site)
    }

    /**
     * Finds the class declaring a member by JVM resolution order — the superclass chain, then
     * every superinterface — across both pools. Returns the pool and class name, or null.
     */
    private fun resolve(
        start: String,
        declares: (ClassNode) -> Boolean,
    ): Pair<ClassPool, String>? {
        val seen = HashSet<String>()
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (!seen.add(name)) continue
            val pool = if (name in boot) boot else app
            val cls = pool[name] ?: continue
            if (declares(cls)) return pool to name
            cls.superName?.let { pending.addFirst(it) }
            pending.addAll(cls.interfaces)
        }
        return null
    }

    private fun requireType(
        descriptor: String,
        site: String,
    ) {
        val type =
            if (descriptor.startsWith("[") || descriptor.endsWith(";")) {
                Type.getType(
                    descriptor,
                )
            } else {
                Type.getObjectType(descriptor)
            }
        val element = if (type.sort == Type.ARRAY) type.elementType else type
        if (element.sort == Type.OBJECT) requireClass(element.internalName, site)
    }

    /** Boot classes win: an app-classpath copy of a `java.*` class never ships on iOS. */
    private fun requireClass(
        name: String,
        site: String,
    ): Boolean {
        if (name in boot || (name in app && !name.startsWith("java/"))) return true
        report(name, site)
        return false
    }

    private fun report(
        ref: String,
        site: String,
    ) {
        problems.putIfAbsent(ref, Problem(ref, site))
    }

    private companion object {
        const val METHOD_HANDLE = "java/lang/invoke/MethodHandle"
        const val VAR_HANDLE = "java/lang/invoke/VarHandle"
    }
}
