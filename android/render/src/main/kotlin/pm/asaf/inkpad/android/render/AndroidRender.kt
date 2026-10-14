// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.android.render

import android.graphics.Bitmap
import pm.asaf.inkpad.core.geometry.CoreGeometry

/** Placeholder until Phase 2 adds page rendering. */
object AndroidRender {
    const val NAME: String = "android:render"
    val geometryName: String = CoreGeometry.NAME

    fun blankPage(
        widthPx: Int,
        heightPx: Int,
    ): Bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
}
