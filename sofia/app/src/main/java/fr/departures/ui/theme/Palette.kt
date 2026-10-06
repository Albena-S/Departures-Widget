package fr.departures.ui.theme

/** Design tokens shared by the widget and the app (see docs/widget-design.md). ARGB ints. */
data class Palette(
    val bg: Int,
    val surface: Int,
    val ink: Int,
    val ink2: Int,
    val ink3: Int,
    val rule: Int,
    val now: Int,
    val late: Int,
    val muted: Int,
    val warn: Int,
    val accent: Int,
)

val LightPalette = Palette(
    bg = 0xFFFBFAF7.toInt(),
    surface = 0xFFFFFFFF.toInt(),
    ink = 0xFF15171B.toInt(),
    ink2 = 0xFF5E636B.toInt(),
    ink3 = 0xFF8C9198.toInt(),
    rule = 0xFFECEAE4.toInt(),
    now = 0xFF0B8A5A.toInt(),
    late = 0xFFD9480F.toInt(),
    muted = 0xFFB4B8BE.toInt(),
    warn = 0xFFC2410C.toInt(),
    accent = 0xFF1F4FD1.toInt(),
)

val DarkPalette = Palette(
    bg = 0xFF16181C.toInt(),
    surface = 0xFF1F2227.toInt(),
    ink = 0xFFF3F2EF.toInt(),
    ink2 = 0xFFA2A7AF.toInt(),
    ink3 = 0xFF6E737B.toInt(),
    rule = 0xFF272A30.toInt(),
    now = 0xFF3FD99A.toInt(),
    late = 0xFFFF8A47.toInt(),
    muted = 0xFF4E535B.toInt(),
    warn = 0xFFFF9F66.toInt(),
    accent = 0xFF8FB0FF.toInt(),
)

const val BADGE_FALLBACK_BG = 0xFF8A8F98.toInt()
const val BADGE_FALLBACK_FG = 0xFFFFFFFF.toInt()

fun palette(dark: Boolean) = if (dark) DarkPalette else LightPalette
