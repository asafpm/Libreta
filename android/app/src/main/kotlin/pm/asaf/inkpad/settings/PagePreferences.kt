// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.NewPageTemplate
import pm.asaf.inkpad.core.model.PageStyle
import pm.asaf.inkpad.core.model.PaperSize

/**
 * How new pages and notes look ([NewPageTemplate]), remembered in [preferences]. The paper
 * size and orientation are also the New note dialog's choice.
 */
class PagePreferences(
    private val preferences: SharedPreferences,
) {
    private val templateState = MutableStateFlow(read())
    val template: StateFlow<NewPageTemplate> = templateState.asStateFlow()

    fun setTemplate(template: NewPageTemplate) {
        templateState.value = template
        preferences
            .edit()
            .putBoolean(COPY_KEY, template.copyCurrent)
            .putInt(PAPER_KEY, template.paper.ordinal)
            .putBoolean(LANDSCAPE_KEY, template.landscape)
            .putString(STYLE_KEY, template.style.value)
            .putLong(COLOR_KEY, template.color.rgba.toLong())
            .apply()
    }

    private fun read(): NewPageTemplate {
        val defaults = NewPageTemplate()
        return NewPageTemplate(
            copyCurrent = preferences.getBoolean(COPY_KEY, defaults.copyCurrent),
            paper =
                PaperSize.entries.getOrNull(
                    preferences.getInt(PAPER_KEY, defaults.paper.ordinal),
                ) ?: defaults.paper,
            landscape = preferences.getBoolean(LANDSCAPE_KEY, defaults.landscape),
            style = preferences.getString(STYLE_KEY, null)?.let(::PageStyle) ?: defaults.style,
            color =
                preferences
                    .getLong(
                        COLOR_KEY,
                        -1L,
                    ).takeIf { it >= 0 }
                    ?.let { Color(it.toUInt()) } ?: defaults.color,
        )
    }

    private companion object {
        const val COPY_KEY = "page.copyCurrent"

        // The New note dialog's, from before the template.
        const val PAPER_KEY = "paper"
        const val LANDSCAPE_KEY = "landscape"
        const val STYLE_KEY = "page.style"
        const val COLOR_KEY = "page.color"
    }
}
