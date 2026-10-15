// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import pm.asaf.inkpad.core.geometry.ViewportAnchor
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.EraserType
import pm.asaf.inkpad.core.model.NewPageTemplate
import pm.asaf.inkpad.core.model.PenSize
import pm.asaf.inkpad.core.model.SelectMode
import pm.asaf.inkpad.core.model.StylusButton
import pm.asaf.inkpad.core.model.Tool
import pm.asaf.inkpad.core.model.ToolSettings
import pm.asaf.inkpad.library.LibraryEntry
import pm.asaf.inkpad.library.LibraryRoot
import pm.asaf.inkpad.library.SortOrder

/** What a library grid does when something in it is chosen. */
class LibraryActions(
    val openFolder: (LibraryEntry.Folder) -> Unit,
    val openDocument: (LibraryEntry.Document) -> Unit,
)

/** Things [SettingsScreen] can change. */
class SettingsActions(
    val addFolder: () -> Unit,
    val remove: (LibraryRoot) -> Unit,
    val move: (LibraryRoot, Int) -> Unit,
    val sortBy: (SortOrder) -> Unit,
    /** Shows or hides the toolbar's "Draw with finger" button. */
    val fingerButton: (Boolean) -> Unit,
    val stylusButton: (StylusButton) -> Unit,
    /** How many fixed colours the toolbar offers. */
    val fixedCount: (Int) -> Unit,
    /** Changes fixed colour (index) to a colour. */
    val fixedColor: (Int, Color) -> Unit,
    /** How many recent colours are kept. */
    val recentLimit: (Int) -> Unit,
    /** Shows or hides a width in the toolbar. */
    val sizeVisible: (PenSize, Boolean) -> Unit,
    /** How new pages and notes look. */
    val newPages: (NewPageTemplate) -> Unit,
)

/** What the toolbar offers, as set in Settings. */
class ToolbarSettings(
    /** Colours offered for the pen and the highlighter. */
    val fixed: List<Color>,
    /** How many recent custom colours are kept. */
    val recentLimit: Int,
    /** Widths offered for every tool. */
    val sizes: Set<PenSize> = PenSize.entries.toSet(),
)

/** The tools and finger drawing, and how to change them. */
class ToolControls(
    val tools: ToolSettings,
    val fingerDraws: Boolean,
    /** Whether the toolbar has the "Draw with finger" button (Settings). */
    val fingerButton: Boolean,
    /** The colours always offered (Settings). */
    val fixedColors: List<Color>,
    /** Custom colours picked, most recent first. */
    val recentColors: List<Color>,
    /** The widths offered (Settings). */
    val visibleSizes: Set<PenSize>,
    val onTool: (Tool) -> Unit,
    /** Sets the chosen tool's colour. */
    val onColor: (Color) -> Unit,
    /** A colour from the picker: used, and remembered among the recent ones. */
    val onCustomColor: (Color) -> Unit,
    /** Sets the chosen tool's width. */
    val onSize: (PenSize) -> Unit,
    val onEraserType: (EraserType) -> Unit,
    val onSelectMode: (SelectMode) -> Unit,
    val onFingerDraws: (Boolean) -> Unit,
)

/** Where the viewer was, kept by the caller so that it survives rotation. */
class ViewerPosition(
    val get: () -> ViewportAnchor?,
    val set: (ViewportAnchor?) -> Unit,
)

/** What the page panel does. */
class PageActions(
    val goTo: (Int) -> Unit,
    val insertAfter: (Int) -> Unit,
    val duplicate: (Int) -> Unit,
    val delete: (Int) -> Unit,
    val move: (Int, Int) -> Unit,
    val background: (Int) -> Unit,
)

/** What the layer sheet does, for the page it shows. */
class LayerActions(
    val select: (Int) -> Unit,
    val setHidden: (Int, Boolean) -> Unit,
    val rename: (Int, String) -> Unit,
    val add: (below: Boolean) -> Unit,
    val delete: () -> Unit,
    val move: (up: Boolean) -> Unit,
    val mergeDown: () -> Unit,
)

/** What the selection bar does. */
class SelectionActions(
    val cut: () -> Unit,
    val copy: () -> Unit,
    val paste: () -> Unit,
    val canPaste: Boolean,
    val duplicate: () -> Unit,
    val delete: () -> Unit,
)
