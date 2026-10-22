// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ElementsXmlTest {
    @Test
    fun elementsSurviveTheClipboard() {
        // Every kind of element in the samples, as they are.
        val elements =
            XoppReader
                .read(TestData.read("xournalpp/packaged_xopp/suite.xopp"))
                .document.pages
                .flatMap { it.layers }
                .flatMap { it.elements }
        check(elements.size > 3)
        assertEquals(elements, ElementsXml.decode(ElementsXml.encode(elements)))
        assertNull(ElementsXml.decode("hello"))
        // A whole note isn't elements.
        assertNull(ElementsXml.decode(XoppWriter.toXml(XoppReader.read(TestData.read("libreta-sample.xopp")).document)))
    }
}
