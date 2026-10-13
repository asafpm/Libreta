package pm.asaf.inkpad.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreGeometryTest {
    @Test
    fun seesCoreModel() {
        assertEquals("core:model", CoreGeometry.modelName)
    }
}
