// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.ui

import pm.asaf.inkpad.core.geometry.ViewportAnchor
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
)

/** Where the viewer was, kept by the caller so that it survives rotation. */
class ViewerPosition(
    val get: () -> ViewportAnchor?,
    val set: (ViewportAnchor?) -> Unit,
)
