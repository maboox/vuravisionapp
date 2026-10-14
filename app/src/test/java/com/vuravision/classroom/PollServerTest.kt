package com.vuravision.classroom

import com.vuravision.classroom.room.PollServer
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phones answering the class quiz: one vote per phone, changeable until the reveal, and nothing outside the token. */
// Robolectric for the real org.json (plain JVM tests get Android's stubs).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PollServerTest {
    private lateinit var server: PollServer

    @Before fun start() {
        server = PollServer()
        server.start(5000, false)
    }

    @After fun stop() {
        server.stop()
    }
    private fun call(path: String, post: Boolean = false): Pair<Int, String> {
        val c = URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection
        if (post) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.outputStream.use { }
        }
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to body
    }

    @Test fun votesAreCountedOncePerPhoneAndCloseAtTheReveal() {
        val base = "/${server.token}"
        assertEquals(200, call(base).first)
        assertTrue("the page is the student view", call(base).second.contains("Waiting for the question"))
        assertEquals(-1, JSONObject(call("$base/state?v=phoneaaaa1").second).getInt("id"))
        server.ask(0, "۲ + ۲ ؟", listOf("۳", "۴", "۵"))
        assertEquals("۲ + ۲ ؟", JSONObject(call("$base/state?v=phoneaaaa1").second).getString("text"))
        assertEquals(200, call("$base/vote?v=phoneaaaa1&q=0&c=1", true).first)
        assertEquals(200, call("$base/vote?v=phonebbbb2&q=0&c=0", true).first)
        assertEquals("a phone may change its answer", 200, call("$base/vote?v=phonebbbb2&q=0&c=1", true).first)
        assertArrayEquals(intArrayOf(0, 2, 0), server.tally())
        assertEquals("an option that does not exist", 400, call("$base/vote?v=phonecccc3&q=0&c=3", true).first)
        assertEquals("an old question", 400, call("$base/vote?v=phonecccc3&q=7&c=1", true).first)
        server.revealed = 1
        assertEquals("closed after the reveal", 400, call("$base/vote?v=phonecccc3&q=0&c=1", true).first)
        assertEquals(2, server.people)
        server.ask(1, "next", listOf("a", "b"))
        assertArrayEquals("a new question starts from zero", intArrayOf(0, 0), server.tally())
        assertEquals("wrong token", 404, call("/0000/state").first)
    }
}
