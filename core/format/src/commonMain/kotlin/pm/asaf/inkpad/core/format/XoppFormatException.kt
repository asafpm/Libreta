// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.format

/** The input is not a readable Xournal++ document. */
class XoppFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
