package pm.asaf.inkpad.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreFormatTest {
    @Test
    fun seesCoreModel() {
        assertEquals("core:model", CoreFormat.modelName)
    }
}
