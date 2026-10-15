// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.viewer

import android.view.MotionEvent

/** Something one pointer does from when it goes down until it lifts: erasing, selecting, drawing a shape. */
internal interface PointerInput {
    /** Whether a pointer is doing it. */
    val isActive: Boolean

    /** Handles [event] while active; returns false once the pointer is done. */
    fun onTouchEvent(event: MotionEvent): Boolean
}
