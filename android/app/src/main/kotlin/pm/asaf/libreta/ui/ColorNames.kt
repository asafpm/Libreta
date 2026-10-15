// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pm.asaf.libreta.R
import pm.asaf.libreta.core.model.Palette
import pm.asaf.libreta.core.model.Color as InkColor

/** The name of [color]: the tools' defaults and Xournal++'s palette have one, others are "#rrggbb". */
@Composable
fun colorName(color: InkColor): String = NAMES[color]?.let { stringResource(it) } ?: color.hex()

private val NAMES: Map<InkColor, Int> =
    mapOf(
        Palette.DEFAULT_PEN to R.string.color_royal_blue,
        Palette.HIGHLIGHTER to R.string.color_bright_yellow,
    ) +
        Palette.XOURNALPP.zip(
            listOf(
                R.string.color_yellow,
                R.string.color_orange,
                R.string.color_purple,
                R.string.color_light_green,
                R.string.color_light_blue,
                R.string.color_gray,
                R.string.color_dark_green,
                R.string.color_red,
                R.string.color_dark_blue,
                R.string.color_black,
                R.string.color_white,
            ),
        )
