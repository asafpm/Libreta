// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutosaveTest {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val saves = ArrayList<Long>()

    /** Runs autosave in the background of the test, saving taking [saving] ms. */
    private fun TestScope.start(saving: Long = 0) =
        backgroundScope
            .launch {
                Autosave(timeSource = testScheduler.timeSource).run(changes) {
                    delay(saving)
                    saves += currentTime
                }
            }.also { runCurrent() }

    private fun TestScope.change() {
        changes.tryEmit(Unit)
        runCurrent()
    }

    @Test
    fun savesAFewSecondsAfterTheLastChange() =
        runTest {
            start()
            advanceTimeBy(30_000)
            assertEquals(emptyList<Long>(), saves)
            change()
            advanceTimeBy(1_000)
            change()
            advanceTimeBy(2_000)
            change()
            advanceTimeBy(10_000)
            // 5 s after the last change, at 33 s.
            assertEquals(listOf(38_000L), saves)
        }

    @Test
    fun savesAtLeastEveryMinuteWhileChangesGoOn() =
        runTest {
            start()
            repeat(150) {
                change()
                advanceTimeBy(1_000)
            }
            advanceTimeBy(10_000)
            // A minute after the first change of each run; then 5 s after the last one, at 149 s.
            assertEquals(listOf(60_000L, 120_000L, 154_000L), saves)
        }

    @Test
    fun changesWhileSavingAreSavedNext() =
        runTest {
            start(saving = 3_000)
            change()
            advanceTimeBy(6_000)
            // Saving from 5 s to 8 s; the change at 6 s is saved from 13 s to 16 s.
            change()
            advanceTimeBy(11_000)
            assertEquals(listOf(8_000L, 16_000L), saves)
        }
}
