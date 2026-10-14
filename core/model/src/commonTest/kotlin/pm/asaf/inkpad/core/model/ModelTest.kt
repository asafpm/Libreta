// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ModelTest {
    @Test
    fun pointListExposesInterleavedCoordinates() {
        val points = PointList.of(1.0, 2.0, 3.0, 4.0)
        assertEquals(2, points.size)
        assertEquals(3.0, points.x(1))
        assertEquals(4.0, points.y(1))
    }

    @Test
    fun pointListRejectsOddCoordinateCounts() {
        assertFailsWith<IllegalArgumentException> { PointList.of(1.0, 2.0, 3.0) }
    }

    @Test
    fun pointListIsImmutable() {
        val source = doubleArrayOf(1.0, 2.0)
        val points = PointList.fromInterleaved(source)
        source[0] = 99.0
        points.toInterleavedArray()[1] = 99.0
        assertEquals(PointList.of(1.0, 2.0), points)
    }

    @Test
    fun pointListEqualityComparesContents() {
        assertEquals(PointList.of(1.0, 2.0), PointList.of(1.0, 2.0))
        assertEquals(PointList.of(1.0, 2.0).hashCode(), PointList.of(1.0, 2.0).hashCode())
        assertNotEquals(PointList.of(1.0, 2.0), PointList.of(1.0, 2.5))
    }

    @Test
    fun doubleListEqualityIsBitwise() {
        assertEquals(DoubleList.of(Double.NaN, 1.0), DoubleList.of(Double.NaN, 1.0))
        assertNotEquals(DoubleList.of(0.0), DoubleList.of(-0.0))
        val negativeNan = Double.fromBits(Double.NaN.toRawBits() or Long.MIN_VALUE)
        assertNotEquals(DoubleList.of(Double.NaN), DoubleList.of(negativeNan))
    }

    @Test
    fun pageNeedsALayer() {
        assertFailsWith<IllegalArgumentException> {
            Page(width = 100.0, height = 100.0, background = null, layers = emptyList())
        }
    }

    @Test
    fun elementsCompareByValueIncludingBytes() {
        fun image() = Element.Image(Bounds(0.0, 0.0, 10.0, 10.0), data = "png".encodeUtf8())
        assertEquals(image(), image())
        assertNotEquals<Element>(image(), image().copy(data = "jpg".encodeUtf8()))
    }

    @Test
    fun documentsCompareByValue() {
        fun document() =
            Document(
                pages =
                    listOf(
                        Page(
                            width = 595.0,
                            height = 842.0,
                            background = Background.Solid(Color.WHITE, PageStyle.LINED),
                            layers =
                                listOf(
                                    Layer(
                                        elements =
                                            listOf(
                                                Element.Stroke(
                                                    tool = StrokeTool.PEN,
                                                    color = Color.BLACK,
                                                    width = 0.85,
                                                    points = PointList.of(1.0, 2.0, 3.0, 4.0),
                                                    segmentWidths = DoubleList.of(0.4),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        assertEquals(document(), document())
        assertEquals(document().hashCode(), document().hashCode())
    }
}
