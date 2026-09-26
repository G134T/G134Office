package org.example.spell

enum class DocLanguage(val title: String, val code: String) {
    RU("Русский", "ru"),
    UK("Українська", "uk"),
    BE("Беларуская", "be"),
    EN("English", "en-US");

    companion object {
        fun detect(text: String): DocLanguage {
            val sample = text.take(800)
            val cyr = sample.count { it in 'А'..'я' || it == 'ё' || it == 'Ё' }
            val lat = sample.count { it in 'A'..'z' }
            if (lat > cyr * 2 && lat > 12) return EN
            val lower = sample.lowercase()
            val ukHits = listOf("і", "ї", "є", "ґ").sumOf { ch -> lower.count { it.toString() == ch } }
            val beHits = listOf("ў", "і").sumOf { ch -> lower.count { it.toString() == ch } }
            return when {
                ukHits >= 3 -> UK
                beHits >= 3 && ukHits < 3 -> BE
                else -> RU
            }
        }
    }
}