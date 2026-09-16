package com.webunime.mobile.data

object SeasonLabels {

    fun from(title: String?, slug: String?, explicit: String? = null): String {
        explicit?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val hay = listOfNotNull(title, slug).joinToString(" ")
        Regex("""(?i)(\d+)(?:st|nd|rd|th)\s*season""")
            .find(hay)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return "Season $it" }
        Regex("""(?i)(?:season|s)\s*[-_]?\s*(\d+)""")
            .find(hay)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return "Season $it" }
        Regex("""(?i)cour\s*[-_]?\s*(\d+)""")
            .find(hay)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return "Cour $it" }
        Regex("""(?i)part\s*[-_]?\s*(\d+)""")
            .find(hay)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return "Part $it" }
        return "Episode"
    }

    fun sortKey(label: String): Int {
        Regex("""(\d+)""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return if (label.equals("Episode", ignoreCase = true)) 0 else 999
    }
}
