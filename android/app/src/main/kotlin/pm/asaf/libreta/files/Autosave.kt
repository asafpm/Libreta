// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.files

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * When to save a note being edited: [idle] after the last change, and at least every
 * [maxDelay] while changes go on.
 */
class Autosave(
    private val idle: Duration = 5.seconds,
    private val maxDelay: Duration = 60.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /** Calls [save] after each run of [changes], until cancelled. Changes during a save start the next run. */
    suspend fun run(
        changes: Flow<*>,
        save: suspend () -> Unit,
    ) = coroutineScope {
        val signals = Channel<Unit>(Channel.CONFLATED)
        launch { changes.collect { signals.send(Unit) } }
        while (true) {
            signals.receive()
            val first = timeSource.markNow()
            do {
                val left = maxDelay - first.elapsedNow()
                val more = left.isPositive() && withTimeoutOrNull(minOf(idle, left)) { signals.receive() } != null
            } while (more)
            save()
        }
    }
}
