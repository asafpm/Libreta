// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreGeometryTest {
    @Test
    fun seesCoreModel() {
        assertEquals(0x000000ffu, CoreGeometry.modelBlack.rgba)
    }
}
