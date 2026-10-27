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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Turns an Android-format `res` directory into Compose Multiplatform resources: every
 * `values*` XML file's strings, plurals and string arrays get their text through
 * [AndroidStringText] (aapt2's escapes and whitespace rules) and are written back with only the
 * escaping Compose's own converter undoes. Folder names carry over through [ComposeQualifiers],
 * which maps the `b+` ones. Anything outside `values*` is copied as is.
 *
 * Attributes other than `name` (tm's `translation_description`, say) are dropped: they are for
 * translators, not the app.
 */
@CacheableTask
abstract class ConvertAndroidStrings : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidResources: DirectoryProperty

    @get:OutputDirectory
    abstract val composeResources: DirectoryProperty

    @TaskAction
    fun convert() {
        val source = androidResources.get().asFile
        val target = composeResources.get().asFile
        target.deleteRecursively()
        // Resource folders are one level deep, as in an Android res directory.
        source.listFiles().orEmpty().filter { it.isDirectory }.forEach { folder ->
            val inValues = folder.name.startsWith("values")
            folder.listFiles().orEmpty().filter { it.isFile }.forEach { file ->
                val converted =
                    if (inValues && file.extension == "xml") convertValues(file) else null
                ComposeQualifiers.foldersFor(folder.name).forEach { name ->
                    val out = File(target, "$name/${file.name}").apply { parentFile.mkdirs() }
                    if (converted != null) out.writeText(converted) else file.copyTo(out, true)
                }
            }
        }
    }

    private fun convertValues(file: File): String {
        val document =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(file)
        val out = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
        document.documentElement.childElements().forEach { element ->
            val name = element.getAttribute("name")
            val tag = element.tagName
            when (tag) {
                "string" -> out.append("    <string name=\"$name\">${element.text()}</string>\n")
                "plurals", "string-array" -> {
                    out.append("    <$tag name=\"$name\">\n")
                    element.childElements().filter { it.tagName == "item" }.forEach { item ->
                        val quantity = item.getAttribute("quantity")
                        val attribute = if (quantity.isEmpty()) "" else " quantity=\"$quantity\""
                        out.append("        <item$attribute>${item.text()}</item>\n")
                    }
                    out.append("    </$tag>\n")
                }
                else -> error("${file.path}: unsupported resource <$tag name=\"$name\">")
            }
        }
        return out.append("</resources>\n").toString()
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    /**
     * The processed text, escaped for Compose's converter: it turns `\\` back into one backslash
     * (and would read a lone one as the start of `\n`, `\t` or `\u`), and is XML underneath.
     */
    private fun Element.text(): String =
        AndroidStringText.process(textContent)
            .replace("\\", "\\\\")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
