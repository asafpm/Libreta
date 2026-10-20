// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta

import android.app.Application
import pm.asaf.libreta.library.BlankPdfPages

/**
 * The app under Robolectric (robolectric.properties): PDFs are drawn as blank pages, as
 * Robolectric can't run Android's PdfRenderer (PDF backgrounds, LaTeX formulas stored as PDF).
 */
class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BlankPdfPages.install()
    }
}
