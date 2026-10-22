// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

import pm.asaf.libreta.core.geometry.ViewportAnchor
import pm.asaf.libreta.core.model.Color
import pm.asaf.libreta.core.model.EraserType
import pm.asaf.libreta.core.model.NewPageTemplate
import pm.asaf.libreta.core.model.PenSize
import pm.asaf.libreta.core.model.SelectMode
import pm.asaf.libreta.core.model.StylusButton
import pm.asaf.libreta.core.model.Tool
import pm.asaf.libreta.core.model.ToolSettings
import pm.asaf.libreta.library.FolderRef
import pm.asaf.libreta.library.LibraryEntry
import pm.asaf.libreta.library.LibraryRoot
import pm.asaf.libreta.library.SortOrder
import pm.asaf.libreta.settings.ThemeMode

/** What a library grid does when something in it is chosen. */
class LibraryActions(
    val openFolder: (LibraryEntry.Folder) -> Unit,
    val openDocument: (LibraryEntry.Document) -> Unit,
    /** Opens a PDF's note, made the first time. */
    val openPdf: (LibraryEntry.Pdf) -> Unit,
    /** Renaming, moving and deleting; null where entries can't be changed (no card menus). */
    val manage: ManageActions? = null,
)

/** Asking to rename, move or delete an entry, and moving one dropped on a folder. */
class ManageActions(
    val rename: (LibraryEntry) -> Unit,
    val move: (LibraryEntry) -> Unit,
    val delete: (LibraryEntry) -> Unit,
    val drop: (LibraryEntry, FolderRef) -> Unit,
    /** Copies a note next to it. */
    val duplicate: (LibraryEntry.Document) -> Unit = {},
)

/** What the dialogs about an entry do once answered. */
class EntryDialogActions(
    val rename: (stem: String) -> Unit,
    val move: (FolderRef) -> Unit,
    val delete: (alsoPdf: Boolean) -> Unit,
    val dismiss: () -> Unit,
)

/** Where help was asked for: its part comes first. */
enum class HelpTopic { NOTE, LIBRARY }

/** What can be done with a note closed while it couldn't be saved. */
class UnsavedNoteActions(
    val retry: () -> Unit,
    val saveCopy: () -> Unit,
    val keepEditing: () -> Unit,
    val discard: () -> Unit,
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

/** How the app looks, as set in Settings. */
class AppearanceSettings(
    val theme: ThemeMode,
    /** Pages shown dark while the app is. */
    val darkPages: Boolean,
    val onTheme: (ThemeMode) -> Unit,
    val onDarkPages: (Boolean) -> Unit,
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
    /** Changes other settings, e.g. the drawing type or text's font. */
    val onChange: ((ToolSettings) -> ToolSettings) -> Unit = {},
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
