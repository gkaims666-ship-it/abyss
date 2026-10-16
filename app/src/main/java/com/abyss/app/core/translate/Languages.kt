package com.abyss.app.core.translate

/** Common languages for the translator UI (BCP-47 tag -> display name). */
object Languages {
    val names: Map<String, String> = linkedMapOf(
        "en" to "English", "ru" to "Russian", "uk" to "Ukrainian", "es" to "Spanish",
        "fr" to "French", "de" to "German", "it" to "Italian", "pt" to "Portuguese",
        "pl" to "Polish", "nl" to "Dutch", "tr" to "Turkish", "ar" to "Arabic",
        "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "hi" to "Hindi",
        "fa" to "Persian", "he" to "Hebrew", "id" to "Indonesian", "vi" to "Vietnamese",
        "th" to "Thai", "cs" to "Czech", "sv" to "Swedish", "fi" to "Finnish",
        "ro" to "Romanian", "el" to "Greek", "hu" to "Hungarian", "da" to "Danish",
        "no" to "Norwegian", "bg" to "Bulgarian",
    )

    fun name(tag: String): String = names[tag] ?: tag.uppercase()
    val list: List<Pair<String, String>> = names.entries.map { it.key to it.value }
}
