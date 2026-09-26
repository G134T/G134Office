package org.example.fanfic

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object FanficChapterGit {
    fun commit(root: File, files: FanficChapterFiles): String {
        val base = root.toPath().toAbsolutePath().normalize()
        if (!base.resolve(".git").toFile().exists()) return "git не найден"
        val paths = listOf(files.html, files.markdown).map { file ->
            val path = file.toPath().toAbsolutePath().normalize()
            if (!path.startsWith(base)) return "Глава вне репозитория"
            base.relativize(path).toString()
        }
        return try {
            val add = run(root, listOf("add", "--") + paths)
            if (add.first != 0) return "git add: ${add.second}"
            val commit = run(root, listOf("commit", "-m", "глава: ${files.html.nameWithoutExtension}", "--") + paths)
            if (commit.first == 0) "Глава закоммичена" else "git commit: ${commit.second}"
        } catch (_: IOException) {
            "git не найден"
        }
    }

    private fun run(root: File, args: List<String>): Pair<Int, String> {
        val process = ProcessBuilder(listOf("git", "-C", root.absolutePath) + args)
            .redirectErrorStream(true).start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return -1 to "превышено время ожидания"
        }
        return process.exitValue() to process.inputStream.bufferedReader().use { it.readText().take(500) }
    }
}
