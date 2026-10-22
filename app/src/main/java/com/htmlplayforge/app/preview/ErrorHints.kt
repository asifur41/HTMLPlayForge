package com.htmlplayforge.app.preview

/** Plain-English help for common errors. First match wins. */
object ErrorHints {
    private val HINTS: List<Pair<String, String>> = listOf(
        "Cannot read properties of null" to
            "The element was not found. Check the id, or move your script to the end of <body>.",
        "Cannot read property" to
            "Something is empty (null or undefined) when you use it. Check the name and the order of your code.",
        "is not a function" to
            "You called something that is not a function. Check the spelling and what the name really holds.",
        "has already been declared" to
            "This name is used twice with let or const. Rename one of them or remove the second one.",
        "is not defined" to
            "The name is misspelled, or the script runs before the thing exists. Check the spelling and the order.",
        "Unexpected end of input" to
            "A bracket, quote or parenthesis is missing above this line. Check that every { ( [ has its pair.",
        "Unexpected token" to
            "A bracket, quote or parenthesis is probably missing above this line. Look for a missing comma or closing mark.",
        "Failed to load resource" to
            "The file name, letters (big or small) or folder is wrong. There is no internet, so only your project files load.",
        "Unexpected identifier" to
            "Something is missing just before this word, like a comma or a semicolon.",
        "Invalid or unexpected token" to
            "There is a strange character or an unclosed quote on this line."
    )

    fun hintFor(message: String): String? {
        for ((key, hint) in HINTS) if (message.contains(key, ignoreCase = true)) return hint
        return null
    }
}
