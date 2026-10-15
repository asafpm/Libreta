// SPDX-License-Identifier: GPL-2.0-or-later

package pm.asaf.libreta.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * Fails on `java.`, `javax.` or `android.` imports in common source sets.
 *
 * While the only targets are Android and JVM, the Kotlin compiler accepts JDK APIs in
 * commonMain (the metadata compilation is skipped), so this is the only enforcement of
 * the multiplatform rules until an iOS target exists.
 */
abstract class CommonPurityCheck : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val forbidden = Regex("""^\s*import\s+(java|javax|android)\.""")
        val violations =
            sources.asFileTree
                .matching { include("**/*.kt") }
                .files
                .sorted()
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (forbidden.containsMatchIn(line)) "$file:${index + 1}: ${line.trim()}" else null
                    }
                }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Platform imports are not allowed in common code (see PLAN.md, Multiplatform rules):\n" +
                    violations.joinToString("\n"),
            )
        }
        marker.get().asFile.writeText("ok\n")
    }
}
