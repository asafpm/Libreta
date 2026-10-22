// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.ui

/** A part of the app and its licence: [files] are licence texts in the app's assets, under licenses/. */
internal data class Licensed(
    val name: String,
    val terms: String,
    val files: List<String>,
)

/** What the app is made of, and under which licences (NOTICE.md says more). */
internal val LICENSED =
    listOf(
        Licensed("Libreta", "GNU GPL, version 2 or later", listOf("GPL-2.0.txt", "GPL-3.0.txt")),
        Licensed(
            "Xournal++",
            "Parts of Libreta are derived from it. © the Xournal++ contributors, GNU GPL version 2 or later",
            listOf("GPL-2.0.txt"),
        ),
        Licensed(
            "PdfBox-Android (Apache PDFBox)",
            "Apache License 2.0, with Adobe font metrics and CMaps",
            listOf("PdfBox-Android-NOTICE.txt", "PdfBox-Android-LICENSE.txt"),
        ),
        Licensed(
            "AndroidX, Jetpack Compose and Material icons",
            "© The Android Open Source Project, Apache License 2.0",
            listOf("Apache-2.0.txt"),
        ),
        Licensed(
            "Kotlin, kotlinx.coroutines, kotlinx.serialization, JetBrains annotations",
            "© JetBrains s.r.o. and Kotlin contributors, Apache License 2.0",
            listOf("Apache-2.0.txt"),
        ),
        Licensed("Okio", "© Square, Inc., Apache License 2.0", listOf("Apache-2.0.txt")),
        Licensed("xmlutil", "© Paul de Vrieze, Apache License 2.0", listOf("Apache-2.0.txt")),
        Licensed("Guava ListenableFuture", "© The Guava Authors, Apache License 2.0", listOf("Apache-2.0.txt")),
        Licensed("JSpecify", "© The JSpecify Authors, Apache License 2.0", listOf("Apache-2.0.txt")),
        Licensed(
            "DejaVu fonts",
            "© Bitstream, Inc. and Tavmjong Bah, Bitstream Vera licence; DejaVu changes are in the public domain",
            listOf("DejaVu-LICENSE.txt"),
        ),
        Licensed(
            "Liberation fonts",
            "© Google Corporation and Red Hat, Inc., SIL Open Font License 1.1",
            listOf("Liberation-LICENSE.txt"),
        ),
    )
