package pm.asaf.inkpad.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreModelTest {
    @Test
    fun hasName() {
        assertEquals("core:model", CoreModel.NAME)
    }
}
