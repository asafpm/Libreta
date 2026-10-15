// SPDX-License-Identifier: GPL-2.0-or-later
// Follows Xournal++ v1.3.8 src/core/control/tools/TextEditor.cpp (initializeEditionAt: a tap on
// text edits it, elsewhere starts new text with its first line centred on the tap;
// finalizeEdition: one undoable change per edit, empty text deleted) and
// src/core/gui/PageView.cpp (startText, Escape), © Xournal++ contributors.

package pm.asaf.libreta.viewer

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import pm.asaf.libreta.core.geometry.ElementGeometry
import pm.asaf.libreta.core.model.Document
import pm.asaf.libreta.core.model.Element
import pm.asaf.libreta.core.model.SelectionOperations
import pm.asaf.libreta.core.model.TextSettings
import kotlin.math.roundToInt

/**
 * Typing and changing text with the text tool, in [field] (an EditText over the page): a tap on
 * text of the page's selected layer edits it, anywhere else starts new text. While it is
 * edited, the text is left out of the page and shown in the field, which follows the page as
 * it scrolls and zooms. Finishing is one edit; text left empty is deleted.
 */
internal class TextEditing(
    private val host: SelectionTool.Host,
) {
    /** The text edited: on [page]'s [layer], at [index] there unless it is new. */
    private class Session(
        val page: Int,
        val layer: Int,
        val index: Int?,
        val original: Element.Text?,
        val x: Double,
        val y: Double,
        var style: TextSettings,
    )

    private var session: Session? = null

    /** The field text is typed in, over the page; hidden while nothing is edited. */
    var field: EditText? = null
        set(value) {
            field = value
            value?.let(::setUp)
        }

    /** Called with the style of the text taken to edit, so that the toolbar shows it. */
    var onStyle: ((TextSettings) -> Unit)? = null

    val isEditing: Boolean get() = session != null

    /** Pages are shown dark: text being typed is recoloured like them. */
    var darkPages: Boolean = false
        set(value) {
            field = value
            place()
        }

    /**
     * A tap at a view point with the text tool: finishes the text edited, then edits the text
     * under the point, or starts new text there in [style].
     */
    fun tap(
        viewX: Float,
        viewY: Float,
        style: TextSettings,
    ) {
        finish()
        val mapping = host.pageAt(viewX, viewY) ?: return
        val renderer = host.renderer ?: return
        val page = host.page(mapping.page)
        val layer = host.layer(mapping.page)
        val x = mapping.toPageX(viewX)
        val y = mapping.toPageY(viewY)
        val elements = page.layers[layer].elements
        // The topmost text under the tap (Xournal++ takes the oldest).
        val index =
            elements.indices.lastOrNull { i ->
                val text = elements[i] as? Element.Text
                val bounds = text?.let { ElementGeometry.bounds(it, renderer.textMeasurer) }
                bounds != null && x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
            }
        session =
            if (index != null) {
                val text = elements[index] as Element.Text
                host.hide(HiddenElements(mapping.page, layer, setOf(index)))
                val own = TextSettings(text.font, text.size, text.color)
                onStyle?.invoke(own)
                Session(mapping.page, layer, index, text, text.x, text.y, own)
            } else {
                // The first line centred on the tap.
                val lineHeight = renderer.lineMetrics(style.font, style.size).lineHeight
                Session(mapping.page, layer, null, null, x, y - lineHeight / 2, style)
            }
        show()
    }

    /** The text edited takes [style] (from the toolbar). */
    fun restyle(style: TextSettings) {
        val active = session ?: return
        if (active.style == style) return
        active.style = style
        place()
    }

    /** Finishes the text edited, if any: one edit adding, changing or deleting it. */
    fun finish() {
        val active = session ?: return
        val typed = field?.text?.toString().orEmpty()
        session = null
        hideField()
        val document = Document(host.pages)
        val text =
            Element.Text(
                typed,
                active.style.font,
                active.style.size,
                active.x,
                active.y,
                active.style.color,
                audio = active.original?.audio,
                extraAttributes = active.original?.extraAttributes.orEmpty(),
            )
        val index = active.index
        val edit =
            when {
                index == null && typed.isBlank() -> {
                    null
                }

                index == null -> {
                    SelectionOperations.add(document, active.page, active.layer, listOf(text)).edit
                }

                typed.isBlank() -> {
                    SelectionOperations.delete(document, active.page, active.layer, listOf(index))
                }

                text == active.original -> {
                    null
                }

                else -> {
                    SelectionOperations
                        .replace(
                            document,
                            active.page,
                            active.layer,
                            listOf(index),
                            listOf(text),
                        ).edit
                }
            }
        if (edit != null) host.edit(edit) else host.hide(null)
    }

    /** Drops the text edited without changing anything (e.g. the document was replaced). */
    fun cancel() {
        if (session == null) return
        session = null
        hideField()
        host.hide(null)
    }

    /** The page moved on screen: the field follows. */
    fun viewportChanged() = place()

    private fun show() {
        val view = field ?: return
        view.setText(session?.original?.text.orEmpty())
        view.setSelection(view.text.length)
        view.visibility = View.VISIBLE
        place()
        view.requestFocus()
        view.post {
            view.context.inputMethods().showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Puts the field where the text is, looking as it will on the page at the current zoom. */
    private fun place() {
        val active = session ?: return
        val view = field
        val mapping = host.mappingOf(active.page)
        val renderer = host.renderer
        if (view == null || mapping == null || renderer == null) return
        val zoom = mapping.zoom
        val style = active.style
        val lines = renderer.lineMetrics(style.font, style.size)
        view.typeface = renderer.typeface(style.font)
        val color = android.graphics.Color.argb(style.color.alpha, style.color.red, style.color.green, style.color.blue)
        view.setTextColor(if (darkPages) PageRecolor.map(color) else color)
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, (style.size * zoom).toFloat())
        view.firstBaselineToTopHeight = (lines.firstBaseline * zoom).roundToInt()
        view.setLineHeight((lines.lineHeight * zoom).roundToInt().coerceAtLeast(1))
        view.minWidth = (style.size * zoom).roundToInt()
        view.translationX = mapping.toViewX(active.x)
        view.translationY = mapping.toViewY(active.y)
    }

    private fun hideField() {
        val view = field ?: return
        view.context.inputMethods().hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
        view.visibility = View.GONE
    }

    private fun setUp(view: EditText) {
        view.visibility = View.GONE
        view.setPadding(0, 0, 0, 0)
        view.includeFontPadding = false
        view.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        // Lines break only where Return is pressed, as in Xournal++.
        view.setHorizontallyScrolling(true)
        view.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        view.background =
            GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(BORDER_PX, BORDER_COLOR, DASH_PX, DASH_PX)
            }
        view.setOnKeyListener { _, keyCode, event ->
            (keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_UP).also { if (it) finish() }
        }
    }

    private fun Context.inputMethods() = getSystemService(InputMethodManager::class.java)

    private companion object {
        const val BORDER_PX = 2
        const val DASH_PX = 8f

        /** The selection's colour. */
        const val BORDER_COLOR = 0xFF729FCF.toInt()
    }
}
