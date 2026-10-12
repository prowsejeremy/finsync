package com.jpd.hz

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Relative to the app module, where Gradle runs unit tests.
private const val SOURCES = "src/main/java/com/jpd/hz"
private const val UI_PACKAGE = "com.jpd.hz.ui"
private val IMPORT = Regex("""^import (com\.jpd\.hz\.[\w.]+)""", RegexOption.MULTILINE)
private val UI_PACKAGE_LINE = Regex("""^package com\.jpd\.hz\.ui$""", RegexOption.MULTILINE)
private const val MODIFIERS =
    "(?:(?:private|internal|public|data|sealed|abstract|open|enum|inline|const|annotation)\\s+)*"
private const val DECLARATION =
    "(?:class|object|interface|fun|val|var|typealias)\\s+(?:<[^>]*>\\s*)?(?:[\\w.<>?,\\s]+\\.)?"
// A top-level declaration's name, after any modifiers and an extension's receiver.
private val TOP_LEVEL = Regex("^$MODIFIERS$DECLARATION(\\w+)", RegexOption.MULTILINE)
private val STRING_LITERAL = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
private val PLATFORM_NAMES = listOf("jellyfin", "plex")

/** Where a source file sits in the split (adapter harness spec, H2). */
private enum class Area { SHELL, PLAYER, HARNESS, PLATFORM, SHARED }

/**
 * The adapter harness's boundaries (adapter harness spec, H2), checked from each main source
 * file's imports:
 * - the player imports nothing from the harness or a platform;
 * - the harness imports no platform, and of the player only LibraryFolderStore;
 * - the shell's screens import no platform; only Hz.kt installs them;
 * - a platform imports nothing of the player.
 * UI files share one package, so an import of `com.jpd.hz.ui.Name` is judged by the folder of the
 * file that declares Name. A same-package reference needs no import and isn't seen; Gradle modules
 * (T5) would close that gap.
 */
class ArchitectureBoundaryTest {

    private val root = File(SOURCES)
    private val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private val uiDeclarations: Map<String, Area> = files
        .filter { UI_PACKAGE_LINE.containsMatchIn(it.readText()) }
        .flatMap { file ->
            TOP_LEVEL.findAll(file.readText()).map { it.groupValues[1] to areaOf(file) }
        }
        .toMap()

    @Test
    fun `the sources are where the test looks`() {
        assertTrue("${root.absolutePath} not found", files.isNotEmpty())
    }

    @Test
    fun `each area imports only what the split allows`() {
        val problems = files.flatMap { file ->
            val from = areaOf(file)
            IMPORT.findAll(file.readText()).mapNotNull { match ->
                val imported = match.groupValues[1]
                val to = areaOfImport(imported)
                    ?: return@mapNotNull "${relative(file)}: $imported isn't in any area"
                problem(file, from, imported, to)?.let { "${relative(file)}: $it" }
            }.toList()
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the harness names no platform in its code`() {
        val problems = files.filter { areaOf(it) == Area.HARNESS }.flatMap { file ->
            val code = withoutComments(file.readText())
            STRING_LITERAL.findAll(code)
                .map { it.value }
                .filter { literal ->
                    PLATFORM_NAMES.any { literal.contains(it, ignoreCase = true) }
                }
                .map { "${relative(file)}: $it" }
                .toList()
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun problem(file: File, from: Area, imported: String, to: Area): String? = when {
        to == Area.SHARED || from == to -> null
        from == Area.PLAYER && (to == Area.HARNESS || to == Area.PLATFORM) ->
            "the player imports $imported"
        from == Area.HARNESS && to == Area.PLATFORM -> "the harness imports $imported"
        from == Area.HARNESS && to == Area.PLAYER &&
            imported != "com.jpd.hz.library.LibraryFolderStore" -> "the harness imports $imported"
        from == Area.SHELL && to == Area.PLATFORM && file.name != "Hz.kt" ->
            "a shell screen imports $imported"
        from == Area.PLATFORM && to == Area.PLAYER -> "a platform imports $imported"
        else -> null
    }

    private fun areaOf(file: File): Area {
        val path = relative(file)
        return when {
            path == "Hz.kt" -> Area.SHELL
            path.startsWith("adapter/") -> Area.HARNESS
            // CredentialStore and the sign-in it saves are Jellyfin's; they stay put (spec H8).
            path.startsWith("platform/") || path.startsWith("auth/") ||
                path == "model/ServerConfig.kt" -> Area.PLATFORM
            path.startsWith("appearance/") -> Area.SHARED
            listOf("library/", "playback/", "equaliser/", "home/", "ui/Library/", "ui/Player/",
                "ui/Equaliser/").any { path.startsWith(it) } -> Area.PLAYER
            path.startsWith("ui/") -> Area.SHELL
            else -> error("$path isn't in any area")
        }
    }

    private fun areaOfImport(imported: String): Area? {
        val rest = imported.removePrefix("com.jpd.hz.")
        return when {
            rest == "R" || rest.startsWith("databinding.") || rest.startsWith("tags.") ||
                rest.startsWith("appearance.") -> Area.SHARED
            // The shell's rescan hook (D1), the one link from an adapter to the player.
            rest == "requestLibraryRescan" -> Area.SHARED
            rest.startsWith("adapter.") -> Area.HARNESS
            rest.startsWith("platform.") || rest.startsWith("auth.") ||
                rest == "model.ServerConfig" -> Area.PLATFORM
            listOf("library.", "playback.", "equaliser.", "home.").any { rest.startsWith(it) } ->
                Area.PLAYER
            imported.startsWith("$UI_PACKAGE.") ->
                uiDeclarations[rest.removePrefix("ui.").substringBefore('.')]
            else -> null
        }
    }

    private fun relative(file: File): String = file.relativeTo(root).invariantSeparatorsPath

    // Line and block comments, so KDoc can still say "Jellyfin's folder".
    private fun withoutComments(code: String): String =
        code.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\n]*"), "")
}
