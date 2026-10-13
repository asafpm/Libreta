package pm.asaf.inkpad.android.render

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidRenderTest {
    @Test
    fun seesCoreGeometry() {
        assertEquals("core:geometry", AndroidRender.geometryName)
    }

    @Test
    fun createsBitmapUnderRobolectric() {
        val bitmap = AndroidRender.blankPage(widthPx = 4, heightPx = 3)
        assertEquals(4, bitmap.width)
        assertEquals(3, bitmap.height)
    }
}
