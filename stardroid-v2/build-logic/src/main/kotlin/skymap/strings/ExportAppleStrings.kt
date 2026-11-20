/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package skymap.strings

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Writes `<lproj>/Localizable.strings` for a chosen set of [keys], read from Android-format `res`
 * directories, for an Apple target that can't read Compose resources (the iOS widget extension).
 * The XML stays the one source that tm translates; this is a build output, never checked in.
 *
 * A locale that lacks a key (tm leaves out a translation identical to the English) gets the
 * English text, since a `.strings` lookup that misses shows the key rather than falling back.
 */
@CacheableTask
abstract class ExportAppleStrings : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidResources: ConfigurableFileCollection

    @get:Input
    abstract val keys: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun export() {
        val wanted = keys.get()
        val byLocale = mutableMapOf<String, MutableMap<String, String>>()
        androidResources.files.filter { it.isDirectory }.forEach { res ->
            res.listFiles().orEmpty().filter { it.isDirectory }.forEach { folder ->
                val lproj = AppleStrings.lprojFor(folder.name) ?: return@forEach
                val strings = byLocale.getOrPut(lproj) { mutableMapOf() }
                folder.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
                    strings.putAll(readStrings(file, wanted.toSet()))
                }
            }
        }
        val english = byLocale["en"].orEmpty()
        val missing = wanted.filterNot { it in english }
        require(missing.isEmpty()) { "no English string for $missing" }

        val target = outputDirectory.get().asFile
        target.deleteRecursively()
        byLocale.forEach { (lproj, strings) ->
            val complete = wanted.associateWith { strings[it] ?: english.getValue(it) }
            File(target, "$lproj.lproj/Localizable.strings").apply {
                parentFile.mkdirs()
                writeText(AppleStrings.file(complete))
            }
        }
    }

    private fun readStrings(
        file: File,
        wanted: Set<String>,
    ): Map<String, String> {
        val root =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(file)
                .documentElement
        return (0 until root.childNodes.length)
            .map { root.childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == "string" && it.getAttribute("name") in wanted }
            .associate { it.getAttribute("name") to AndroidStringText.process(it.textContent) }
    }
}
