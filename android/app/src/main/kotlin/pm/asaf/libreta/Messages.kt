// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Something to tell the user, with an [action] to offer (e.g. Undo, Retry). */
class Message(
    val text: String,
    val action: Action? = null,
) {
    class Action(
        val label: String,
        val run: () -> Unit,
    )
}

/** What to tell the user, shown one message after another (none replaces one not yet shown). */
class Messages {
    private val queue = Channel<Message>(Channel.UNLIMITED)

    /** The messages, each once, in order. */
    val shown: Flow<Message> = queue.receiveAsFlow()

    fun say(
        text: String,
        action: Message.Action? = null,
    ) {
        queue.trySend(Message(text, action))
    }
}
