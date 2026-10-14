// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.inkpad.core.model

/**
 * Document content Inkpad does not interpret: an XML element (or text) kept verbatim so
 * that saving writes it back unchanged.
 */
sealed interface ForeignNode

/** An uninterpreted element. [attributes] keep their original order. */
data class ForeignElement(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<ForeignNode> = emptyList(),
) : ForeignNode

/** Character data inside a [ForeignElement]. */
data class ForeignText(
    val text: String,
) : ForeignNode
