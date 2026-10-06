package fr.departures.data.stops

/**
 * Official Bulgarian romanisation (Transliteration Act 2009, "Streamlined System"):
 * щ→sht, ъ→a, ь→y, ю→yu, я→ya, ж→zh, ц→ts, ч→ch, ш→sh, х→h, й→y; word-final "ия" → "ia".
 * Output is lower-case; non-Cyrillic characters pass through (lower-cased).
 */
fun toLatin(cyrillic: String): String {
    val s = cyrillic.lowercase()
    val out = StringBuilder(s.length + 8)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == 'и' && i + 1 < s.length && s[i + 1] == 'я' && (i + 2 == s.length || !s[i + 2].isLetter())) {
            out.append("ia"); i += 2; continue
        }
        out.append(MAP[c] ?: c.toString())
        i++
    }
    return out.toString()
}

/** Search form: Latin, lower-case, punctuation → space, single spaces. Works for Cyrillic or Latin input. */
fun normalize(s: String): String =
    toLatin(s).map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ")

private val MAP: Map<Char, String> = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z",
    'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
    'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
    'ш' to "sh", 'щ' to "sht", 'ъ' to "a", 'ь' to "y", 'ю' to "yu", 'я' to "ya",
)
