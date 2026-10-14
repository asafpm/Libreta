// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import pm.asaf.inkpad.core.geometry.ViewportAnchor
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.EraserType
import pm.asaf.inkpad.core.model.PenSize
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
    val fingerDraws: (Boolean) -> Unit,
    val stylusButton: (StylusButton) -> Unit,
)

/** The tools and finger drawing, and how to change them. */
class ToolControls(
    val tools: ToolSettings,
    val fingerDraws: Boolean,
    /** Custom colours picked, most recent first. */
    val recentColors: List<Color>,
    val onTool: (Tool) -> Unit,
    /** Sets the chosen tool's colour. */
    val onColor: (Color) -> Unit,
    /** A colour from the picker: used, and remembered among the recent ones. */
    val onCustomColor: (Color) -> Unit,
    /** Sets the chosen tool's width. */
    val onSize: (PenSize) -> Unit,
    val onEraserType: (EraserType) -> Unit,
    val onFingerDraws: (Boolean) -> Unit,
)

/** Where the viewer was, kept by the caller so that it survives rotation. */
class ViewerPosition(
    val get: () -> ViewportAnchor?,
    val set: (ViewportAnchor?) -> Unit,
)
