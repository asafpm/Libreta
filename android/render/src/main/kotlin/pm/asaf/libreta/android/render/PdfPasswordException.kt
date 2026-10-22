// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.android.render

import java.io.IOException

/** A PDF that can't be read without its password. */
class PdfPasswordException(
    cause: Throwable,
) : IOException("The PDF needs a password", cause)
