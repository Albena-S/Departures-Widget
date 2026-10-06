package fr.departures.widget

/** How much of a departure row fits in the widget's real width. */
data class RowFit(val laterCount: Int, val showLabel: Boolean)

const val LABEL_MIN_DP = 44f
private const val LABEL_GAP_DP = 8f
private const val SAFETY_DP = 4f

/**
 * Decides, from estimated text widths, how many later departures and whether the station
 * label fit next to the badge and first departure. Priority: first departure > station label
 * (when the group has several stations) > 2nd departure > 3rd departure.
 * Widths are in dp at font scale 1; pass [fontScale] to scale text.
 */
fun fitRow(
    availableDp: Float,
    badgeDp: Float,
    first: TimeCell,
    later: List<TimeCell>,
    label: String?,
    compact: Boolean,
    fontScale: Float = 1f,
): RowFit {
    val room = availableDp - SAFETY_DP
    val badge = badgeDp + if (compact) 8f else 10f
    val firstW = firstCellWidth(first, compact) * fontScale
    val laterW = later.map { (textWidth(laterText(it), 14f) * fontScale) + if (compact) 10f else 12f }
    val labelW = LABEL_MIN_DP + LABEL_GAP_DP

    fun used(n: Int, withLabel: Boolean) = badge + firstW + laterW.take(n).sum() + if (withLabel) labelW else 0f

    var n = later.size
    while (n > 0 && used(n, false) > room) n--
    if (label.isNullOrBlank()) return RowFit(n, false)
    var withLabel = n
    while (withLabel > 0 && used(withLabel, true) > room) withLabel--
    return if (used(withLabel, true) <= room) RowFit(withLabel, true) else RowFit(n, false)
}

/** What a 2nd/3rd departure shows: the number without "min", or a clock time. */
fun laterText(c: TimeCell): String = c.text.substringBefore(' ')

fun firstCellWidth(c: TimeCell, compact: Boolean): Float {
    val num = c.text.substringBefore(' ')
    val unit = c.text.substringAfter(' ', "")
    val numW = textWidth(num, if (compact) 17f else 22f) * 1.05f // bold
    val unitW = if (unit.isEmpty()) 0f else textWidth(" $unit", if (compact) 11f else 12f)
    return numW + unitW + platformWidth(c.platform, compact)
}

/** Outlined platform box after the first departure: text + inner padding + gap. */
fun platformWidth(platform: String?, compact: Boolean): Float =
    if (platform.isNullOrEmpty()) 0f else textWidth(platform, if (compact) 10f else 11f) * 1.05f + 10f + 6f

/** Rough Roboto advance widths, in em. Good enough to decide what fits. */
fun textWidth(text: String, sp: Float): Float = text.sumOf { ch ->
    when {
        ch.isDigit() -> 0.56
        ch == ':' -> 0.28
        ch == ' ' -> 0.27
        ch == '—' -> 0.9
        ch.isUpperCase() -> 0.64
        else -> 0.52
    }
}.toFloat() * sp
