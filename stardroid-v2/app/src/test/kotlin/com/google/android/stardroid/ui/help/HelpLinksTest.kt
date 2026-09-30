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
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.io.File

class HelpLinksTest {
    @Test
    fun `each target parses to its destination`() {
        assertThat(parseHelpLink("skymap://widgets")).isEqualTo(HelpLink.Widgets)
        assertThat(parseHelpLink("skymap://settings")).isEqualTo(HelpLink.Destination.SETTINGS)
        assertThat(parseHelpLink("skymap://diagnostics"))
            .isEqualTo(HelpLink.Destination.DIAGNOSTICS)
        assertThat(parseHelpLink("skymap://calibrate")).isEqualTo(HelpLink.Destination.CALIBRATE)
        assertThat(parseHelpLink("skymap://gallery")).isEqualTo(HelpLink.Destination.GALLERY)
        assertThat(parseHelpLink("skymap://tutorial")).isEqualTo(HelpLink.Destination.TUTORIAL)
        assertThat(parseHelpLink("skymap://app-settings"))
            .isEqualTo(HelpLink.Destination.APP_SETTINGS)
    }

    @Test
    fun `an anchor naming a real section parses`() {
        assertThat(parseHelpLink("skymap://help#troubleshooting"))
            .isEqualTo(HelpLink.Anchor("troubleshooting"))
    }

    @Test
    fun `unrecognised links are inert rather than fatal`() {
        // These arrive from translated resources, so a mangled href must not throw.
        assertThat(parseHelpLink("skymap://help#no-such-section")).isNull()
        assertThat(parseHelpLink("skymap://teleport")).isNull()
        assertThat(parseHelpLink("skymap://")).isNull()
        assertThat(parseHelpLink("https://stardroid.app")).isNull()
        assertThat(parseHelpLink("mailto:skymapdevs@gmail.com")).isNull()
    }

    @Test
    fun `only our own scheme is routed internally`() {
        // The fork that keeps the document's real links reaching the platform URI handler
        // once a link listener is installed.
        assertThat(isAppLink("skymap://settings")).isTrue()
        assertThat(isAppLink("https://stardroid.app")).isFalse()
        assertThat(isAppLink("https://ts.stardroid.app")).isFalse()
        assertThat(isAppLink("mailto:skymapdevs@gmail.com")).isFalse()
    }

    @Test
    fun `anchors are unique so a link can only mean one section`() {
        assertThat(HELP_ANCHORS).hasSize(HELP_DOCUMENT.size)
    }

    @Test
    fun `every app link in the English document has a target`() {
        val links = appLinksIn(helpXml("values"))

        assertThat(links).isNotEmpty()
        for (link in links) {
            assertThat(parseHelpLink(link)).isNotNull()
        }
    }

    @Test
    fun `no locale invents or mangles an app link`() {
        val english = appLinksIn(helpXml("values")).toSet()
        val locales = resDir().listFiles { file -> file.isDirectory && isLocaleDir(file.name) }

        assertThat(locales).isNotEmpty()
        for (locale in locales.orEmpty()) {
            val translated = File(locale, HELP_XML)
            if (!translated.isFile) continue
            // Containment, not equality: a locale whose help.xml predates an English copy edit
            // is simply stale, which `tm languages` reports and `tm translate` fixes. What must
            // never happen is a locale carrying an href English does not have — that means the
            // translator localised, invented or mangled a navigation target.
            assertWithMessage("skymap:// links in ${locale.name}/$HELP_XML")
                .that(english)
                .containsAtLeastElementsIn(appLinksIn(translated.readText()).toSet())
        }
    }

    private fun helpXml(qualifier: String): String =
        File(resDir(), "$qualifier/$HELP_XML").readText()

    private fun appLinksIn(xml: String): List<String> =
        HREF.findAll(xml).map { it.groupValues[1] }.filter(::isAppLink).toList()

    private fun isLocaleDir(name: String): Boolean =
        name == "values" || (name.startsWith("values-") && name != "values-night")

    private fun resDir(): File {
        // The unit-test working directory is the Gradle project directory, but don't rely on
        // which one: walk up until the resource tree appears.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val res = File(dir, "app/src/main/res")
            if (res.isDirectory) return res
            val own = File(dir, "src/main/res")
            if (own.isDirectory) return own
            dir = dir.parentFile
        }
        error("could not locate app/src/main/res from ${File("").absolutePath}")
    }

    private companion object {
        const val HELP_XML = "help.xml"

        /** Matches the href of every anchor, ours or not. */
        val HREF = Regex("""href\s*=\s*"([^"]*)"""")
    }
}
