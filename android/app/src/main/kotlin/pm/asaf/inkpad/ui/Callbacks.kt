// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import pm.asaf.inkpad.core.geometry.ViewportAnchor
import pm.asaf.inkpad.core.model.Color
import pm.asaf.inkpad.core.model.PenSettings
import pm.asaf.inkpad.core.model.PenSize
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
)

/** The pen and finger drawing, and how to change them. */
class PenControls(
    val pen: PenSettings,
    val fingerDraws: Boolean,
    val onColor: (Color) -> Unit,
    val onSize: (PenSize) -> Unit,
    val onFingerDraws: (Boolean) -> Unit,
)

/** Where the viewer was, kept by the caller so that it survives rotation. */
class ViewerPosition(
    val get: () -> ViewportAnchor?,
    val set: (ViewportAnchor?) -> Unit,
)
