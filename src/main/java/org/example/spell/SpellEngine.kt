package org.example.spell

import org.languagetool.JLanguageTool
import org.languagetool.Languages
import org.languagetool.rules.RuleMatch

data class SpellIssue(
    val from: Int,
    val to: Int,
    val message: String,
    val word: String,
    val replacements: List<String>,
    val spelling: Boolean
)

object SpellEngine {
    private const val MAX_CHUNK = 2000
    private val tools = mutableMapOf<DocLanguage, JLanguageTool>()

    @Synchronized
    fun tool(lang: DocLanguage): JLanguageTool {
        return tools.getOrPut(lang) {
            JLanguageTool(Languages.getLanguageForShortCode(lang.code)).also { lt ->
                runCatching {
                    lt.allRules
                        .filter { it.isDictionaryBasedSpellingRule.not() && it.id.contains("WHITESPACE") }
                        .forEach { lt.disableRule(it.id) }
                }
            }
        }
    }

    fun warmup(lang: DocLanguage) {
        Thread({
            runCatching { check("тест test.", lang) }
        }, "g134-spell-warm").apply { isDaemon = true }.start()
    }

    fun check(text: String, lang: DocLanguage): List<SpellIssue> {
        if (text.isBlank()) return emptyList()
        val slice = if (text.length > MAX_CHUNK) text.takeLast(MAX_CHUNK) else text
        val shift = text.length - slice.length
        return try {
            tool(lang).check(slice).mapNotNull { match -> toIssue(slice, match, shift) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun checkNearCaret(text: String, caret: Int, lang: DocLanguage): List<SpellIssue> {
        val end = caret.coerceIn(0, text.length)
        val start = (end - 400).coerceAtLeast(0)
        val chunk = text.substring(start, end)
        return check(chunk, lang).map {
            it.copy(from = it.from + start, to = it.to + start)
        }
    }

    fun suggestions(prefix: String, lang: DocLanguage, limit: Int = 8): List<String> {
        val stem = prefix.trim()
        if (stem.length < 2) return emptyList()
        return check("$stem ", lang)
            .firstOrNull { it.spelling || it.from == 0 }
            ?.replacements
            ?.filter { it.startsWith(stem, ignoreCase = true) }
            ?.distinct()
            ?.take(limit)
            .orEmpty()
    }

    private fun toIssue(text: String, match: RuleMatch, shift: Int): SpellIssue? {
        val from = (match.fromPos + shift).coerceAtLeast(0)
        val localFrom = match.fromPos.coerceIn(0, text.length)
        val localTo = match.toPos.coerceIn(localFrom, text.length)
        if (localFrom == localTo) return null
        val word = text.substring(localFrom, localTo).trim()
        if (word.isEmpty()) return null
        return SpellIssue(
            from = from,
            to = from + (localTo - localFrom),
            message = match.shortMessage.ifBlank { match.message },
            word = word,
            replacements = match.suggestedReplacements
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != word }
                .distinct()
                .take(8),
            spelling = match.rule.isDictionaryBasedSpellingRule
        )
    }
}