package fr.departures.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import fr.departures.data.model.Mode
import fr.departures.widget.BadgeRenderer

/** Same generated badge as the widget, so the app and the widget match. */
@Composable
fun LineBadge(shortName: String, mode: Mode, color: Int?, textColor: Int?, modifier: Modifier = Modifier, heightDp: Float = 24f) {
    val context = LocalContext.current
    val bmp = remember(shortName, mode, color, textColor, heightDp) {
        BadgeRenderer.render(context, shortName, mode, color, textColor, heightDp).asImageBitmap()
    }
    Image(bitmap = bmp, contentDescription = "Line $shortName", modifier = modifier)
}

fun Mode.label(): String = when (this) {
    Mode.METRO -> "Metro"
    Mode.RER -> "RER"
    Mode.TRAIN -> "Train"
    Mode.TRAM -> "Tram"
    Mode.BUS -> "Bus"
    Mode.TROLLEY -> "Trolleybus"
    Mode.OTHER -> "Other"
}
