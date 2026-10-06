package fr.departures

import fr.departures.data.api.SofiaClient
import fr.departures.data.api.SofiaResult
import fr.departures.data.stops.StopIndex
import fr.departures.data.stops.downloadStopsCsv
import fr.departures.data.stops.parseStopsCsv
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Hits the real services. Run with LIVE_SOFIA=1 ./gradlew testDebugUnitTest. Skipped otherwise. */
class LiveSofiaTest {
    private val live = System.getenv("LIVE_SOFIA") == "1"

    @Test fun realVirtualTableWithRotatingSession() = runBlocking {
        assumeTrue(live)
        val c = SofiaClient(OkHttp.create(), AcceptAllCookiesStorage())
        for (code in listOf("0328", "18", "0328")) { // second 0328 proves the rotated token is accepted
            val r = c.virtualTable(code)
            println("LIVE $code -> $r".take(400))
            assertTrue("$code: $r", r is SofiaResult.Ok)
        }
    }

    @Test fun realStopListStreamsOnlyStopsTxt() = runBlocking {
        assumeTrue(live)
        var bytes = 0L
        val csv = downloadStopsCsv(HttpClient(OkHttp)) { bytes = it }
        val stops = parseStopsCsv(csv)
        println("LIVE stops=${stops.size} compressedBytesRead=$bytes")
        assertTrue(stops.size > 3000)
        assertTrue("read ${bytes}B, expected well under 1 MB", bytes < 1_000_000)
        val idx = StopIndex(stops)
        println("LIVE search velichkov -> " + idx.search("velichkov").take(5).map { "${it.name}·${it.code}" })
        println("LIVE search 18 -> " + idx.search("18").map { "${it.name}·${it.code} ${it.modes}" })
        println("LIVE search sofia -> " + idx.search("sofia").take(5).map { "${it.name}·${it.code}" })
        assertTrue(idx.search("0328").isNotEmpty())
    }
}
