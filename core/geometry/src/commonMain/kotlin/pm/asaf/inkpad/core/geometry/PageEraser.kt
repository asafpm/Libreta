// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/control/tools/EraseHandler.cpp, © Xournal++ contributors.

package pm.asaf.inkpad.core.geometry

import pm.asaf.inkpad.core.model.Bounds
import pm.asaf.inkpad.core.model.Element
import pm.asaf.inkpad.core.model.Page
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * One eraser gesture on a page. Like Xournal++ it erases strokes of one layer, the selected
 * one ([layerIndex], by default the top one): cutting them where the eraser goes
 * ([deleteStrokes] false), or deleting each stroke it touches. Erased strokes are replaced in
 * place by what is left of them.
 */
class PageEraser(
    /** The page as the gesture found it. */
    val original: Page,
    private val deleteStrokes: Boolean,
    private val layerIndex: Int = original.layers.lastIndex,
) {
    private val elements = original.layers[layerIndex].elements
    private val bounds: List<Bounds?> = elements.map { (it as? Element.Stroke)?.let(StrokeGeometry::bounds) }
    private val erasables = arrayOfNulls<ErasableStroke>(elements.size)

    /** What replaces each element so far; null: unchanged. */
    private val replacements = arrayOfNulls<List<Element>>(elements.size)

    /** The page as erased so far. */
    var page: Page = original
        private set

    /**
     * Erases the square of half side [halfSize] around ([x], [y]). Returns the area (page
     * units) where the page changed, or null if nothing was erased.
     */
    fun erase(
        x: Double,
        y: Double,
        halfSize: Double,
    ): Bounds? {
        val square = Bounds(x - halfSize, y - halfSize, x + halfSize, y + halfSize)
        var changed: Bounds? = null
        for (i in elements.indices) {
            val area = eraseElement(i, square, x, y, halfSize)
            if (area != null) changed = changed?.union(area) ?: area
        }
        if (changed != null) page = rebuilt()
        return changed
    }

    /**
     * Erases along the line from ([fromX], [fromY]) to ([toX], [toY]), in steps no longer than
     * [halfSize] so that fast moves leave nothing behind. Returns the area that changed.
     */
    fun eraseAlong(
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
        halfSize: Double,
    ): Bounds? {
        val steps = max(1, ceil(hypot(toX - fromX, toY - fromY) / halfSize).toInt())
        var changed: Bounds? = null
        for (step in 1..steps) {
            val t = step.toDouble() / steps
            val area = erase(fromX + t * (toX - fromX), fromY + t * (toY - fromY), halfSize)
            if (area != null) changed = changed?.union(area) ?: area
        }
        return changed
    }

    /** Erases element [i] if it is a stroke the eraser reaches; returns where it changed, or null. */
    private fun eraseElement(
        i: Int,
        square: Bounds,
        x: Double,
        y: Double,
        halfSize: Double,
    ): Bounds? {
        val stroke = elements[i] as? Element.Stroke
        val strokeBounds = bounds[i]
        val reached = stroke != null && strokeBounds != null && strokeBounds.intersects(square)
        return when {
            !reached || replacements[i]?.isEmpty() == true -> {
                null
            }

            deleteStrokes -> {
                strokeBounds.takeIf { StrokeEraser.touches(stroke, x, y, halfSize) }?.also {
                    replacements[i] =
                        emptyList()
                }
            }

            else -> {
                cut(i, stroke, strokeBounds, x, y, halfSize)
            }
        }
    }

    /** Cuts element [i]; returns where it changed, or null. */
    private fun cut(
        i: Int,
        stroke: Element.Stroke,
        strokeBounds: Bounds,
        x: Double,
        y: Double,
        halfSize: Double,
    ): Bounds? {
        val erasable = erasables[i] ?: ErasableStroke(stroke).also { erasables[i] = it }
        if (!erasable.erase(x, y, halfSize)) return null
        replacements[i] = erasable.pieces()
        // A fill or a dash pattern changes along the whole stroke.
        val filled = (stroke.fill ?: -1) > 0
        // Otherwise what was cut lies in the padded eraser square, drawn up to half the widest width around.
        val reach = StrokeEraser.boxFor(stroke, x, y, halfSize).halfSizeWithPadding + maxWidth(stroke) / 2
        return if (filled || stroke.lineStyle != null) {
            strokeBounds
        } else {
            Bounds(
                max(x - reach, strokeBounds.left),
                max(y - reach, strokeBounds.top),
                min(x + reach, strokeBounds.right),
                min(y + reach, strokeBounds.bottom),
            )
        }
    }

    private fun maxWidth(stroke: Element.Stroke): Double {
        val widths = stroke.segmentWidths ?: return stroke.width
        return max(stroke.width, (0 until widths.size).maxOfOrNull { widths[it] } ?: 0.0)
    }

    private fun rebuilt(): Page {
        val erased = elements.indices.flatMap { replacements[it] ?: listOf(elements[it]) }
        val layers = original.layers.toMutableList()
        layers[layerIndex] = layers[layerIndex].copy(elements = erased)
        return original.copy(layers = layers)
    }
}
