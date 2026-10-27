/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.help

import com.google.android.stardroid.ui.common.isAppLink
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The help.xml resources against the link parser: the half of the link tests that reads files. */
class HelpLinkResourcesTest {
    @Test
    fun `every app link in the English document has a target`() {
        val links = appLinksIn(helpXml("values"))

        assertTrue(links.isNotEmpty())
        for (link in links) {
            assertNotNull(parseHelpLink(link), link)
        }
    }

    @Test
    fun `no locale invents or mangles an app link`() {
        val english = appLinksIn(helpXml("values")).toSet()
        val locales = resDir().listFiles { file -> file.isDirectory && isLocaleDir(file.name) }

        assertTrue(!locales.isNullOrEmpty())
        for (locale in locales.orEmpty()) {
            val translated = File(locale, HELP_XML)
            if (!translated.isFile) continue
            // Containment, not equality: a locale whose help.xml predates an English copy edit
            // is simply stale, which `tm languages` reports and `tm translate` fixes. What must
            // never happen is a locale carrying an href English does not have — that means the
            // translator localised, invented or mangled a navigation target.
            val invented = appLinksIn(translated.readText()).toSet() - english
            assertTrue(invented.isEmpty(), "skymap:// links in ${locale.name}/$HELP_XML: $invented")
        }
    }

    private fun helpXml(qualifier: String): String =
        File(resDir(), "$qualifier/$HELP_XML").readText()

    private fun appLinksIn(xml: String): List<String> =
        HREF.findAll(xml).map { it.groupValues[1] }.filter(::isAppLink).toList()

    private fun isLocaleDir(name: String): Boolean =
        name == "values" || (name.startsWith("values-") && name != "values-night")

    private fun resDir(): File {
        // The test's working directory is a Gradle project directory, but don't rely on which
        // one: walk up until the resource tree appears.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val res = File(dir, "shared/ui/src/commonMain/res")
            if (res.isDirectory) return res
            val own = File(dir, "src/commonMain/res")
            if (own.isDirectory) return own
            dir = dir.parentFile
        }
        error("could not locate shared/ui/src/commonMain/res from ${File("").absolutePath}")
    }

    private companion object {
        const val HELP_XML = "help.xml"

        /** Matches the href of every anchor, ours or not. */
        val HREF = Regex("""href\s*=\s*"([^"]*)"""")
    }
}
