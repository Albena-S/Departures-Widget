package fr.departures.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import fr.departures.data.model.Mode
import fr.departures.ui.theme.BADGE_FALLBACK_BG
import fr.departures.ui.theme.BADGE_FALLBACK_FG
import kotlin.math.max

/**
 * Draws a line badge in the official colours. Shape suggests the mode (IDFM conventions):
 * metro circle, RER/train rounded square, tram plain rectangle, bus wide rounded rectangle.
 */
object BadgeRenderer {
    private val cache = LruCache<String, Bitmap>(64)

    fun render(context: Context, shortName: String, mode: Mode, bg: Int?, fg: Int?, heightDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val key = "$shortName|$mode|$bg|$fg|$heightDp|$density"
        cache.get(key)?.let { return it }

        val h = heightDp * density
        val back = bg ?: BADGE_FALLBACK_BG
        val front = if (bg == null) BADGE_FALLBACK_FG else (fg ?: BADGE_FALLBACK_FG)
        val text = shortName.ifBlank { "?" }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = front
            textAlign = Paint.Align.CENTER
        }
        // Shrink long names (e.g. "95-01") so they fit without growing the badge too much.
        paint.textSize = h * when {
            text.length <= 2 -> 0.62f
            text.length <= 3 -> 0.52f
            else -> 0.44f
        }
        val textW = paint.measureText(text)
        val pad = h * 0.28f
        val w = when (mode) {
            Mode.METRO -> if (text.length <= 2) h else textW + pad * 2
            Mode.BUS -> max(h * 1.7f, textW + pad * 2)
            Mode.TRAM -> max(h * 1.25f, textW + pad * 2)
            else -> max(h, textW + pad * 2)
        }
        val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = back }
        val r = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        when (mode) {
            Mode.METRO -> c.drawRoundRect(r, h / 2, h / 2, fill)
            Mode.RER, Mode.TRAIN -> c.drawRoundRect(r, h * 0.24f, h * 0.24f, fill)
            Mode.TRAM -> c.drawRect(r, fill)
            Mode.BUS -> c.drawRoundRect(r, h * 0.3f, h * 0.3f, fill)
            Mode.OTHER -> c.drawRoundRect(r, h * 0.15f, h * 0.15f, fill)
        }
        val baseline = r.centerY() - (paint.descent() + paint.ascent()) / 2
        c.drawText(text, r.centerX(), baseline, paint)
        cache.put(key, bmp)
        return bmp
    }

    /** Platform as an outlined box: reads as "where", never as "how long" (minutes are bare bold text). */
    fun renderPlatform(context: Context, platform: String, stroke: Int, text: Int, heightDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val key = "P|$platform|$stroke|$text|$heightDp|$density"
        cache.get(key)?.let { return it }
        val h = heightDp * density
        val sw = 1.3f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = text
            textAlign = Paint.Align.CENTER
            textSize = h * 0.62f
        }
        val w = max(h, paint.measureText(platform) + h * 0.6f)
        val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = RectF(sw / 2, sw / 2, bmp.width - sw / 2, bmp.height - sw / 2)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; color = stroke }
        c.drawRoundRect(r, h * 0.22f, h * 0.22f, outline)
        c.drawText(platform, r.centerX(), r.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
        cache.put(key, bmp)
        return bmp
    }
}
