// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import okio.Buffer
import pm.asaf.libreta.core.model.ForeignText
import kotlin.test.Test
import kotlin.test.assertEquals

class Utf8SourceReaderTest {
    @Test
    fun decodesMultiByteCharactersAcrossChunks() {
        // 2-, 3- and 4-byte sequences, shifted so that every alignment meets a 64 KB chunk edge.
        for (shift in 0 until 4) {
            val text = "x".repeat(shift) + "é测😀".repeat(30_000)
            val xml = "﻿<?xml version=\"1.0\"?><t>$text</t>"
            val root = XmlTree.parse(Buffer().writeUtf8(xml))
            assertEquals(text, (root.children.single() as ForeignText).text, "shift $shift")
            assertEquals(XmlTree.parse(xml), root)
        }
    }
}
