package org.example.fanfic

enum class FanficDirection(val title: String) {
    GEN("Джен"),
    HET("Гет"),
    SLASH("Слэш"),
    FEMSLASH("Фемслэш"),
    OTHER("Другие виды отношений"),
    MIXED("Смешанная"),
    ARTICLE("Статья")
}

enum class FanficRating(val title: String, val color: String) {
    G("G", "#5cb85c"),
    PG13("PG-13", "#f0ad4e"),
    R("R", "#f0ad4e"),
    NC17("NC-17", "#d9534f"),
    NC21("NC-21", "#a94442")
}

enum class FanficStatus(val title: String) {
    DRAFT("Черновик"),
    IN_PROGRESS("В процессе"),
    COMPLETE("Завершён"),
    FROZEN("Заморожен")
}

data class FanficMeta(
    var title: String = "Без названия",
    var chapterTitle: String = "Глава 1",
    var author: String = "автор",
    var fandom: String = "",
    var pairing: String = "",
    var direction: FanficDirection = FanficDirection.GEN,
    var rating: FanficRating = FanficRating.G,
    var status: FanficStatus = FanficStatus.DRAFT,
    var tags: String = "",
    var description: String = "",
    var notes: String = ""
) {
    fun tagList(): List<String> = tags.split(',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun displayTitle(): String = title.ifBlank { "Без названия" }

    fun displayChapter(): String = chapterTitle.ifBlank { "Глава 1" }
}
