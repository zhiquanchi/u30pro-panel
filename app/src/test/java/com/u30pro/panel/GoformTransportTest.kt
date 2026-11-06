package com.u30pro.panel

import com.u30pro.panel.data.goform.GoformException
import com.u30pro.panel.data.goform.GoformTransport
import com.u30pro.panel.data.goform.MemoryCookieJar
import com.u30pro.panel.data.goform.string
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoformTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: GoformTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .cookieJar(MemoryCookieJar())
            .build()
        transport = GoformTransport(client)
        transport.setOrigin("127.0.0.1", server.port)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends mandatory headers and form body in order`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"LD":"salt-1"}"""))
        server.enqueue(MockResponse().setBody("""{"result":0}"""))

        val ld = transport.get(
            linkedMapOf("cmd" to "LD", "multi_data" to "1", "isTest" to "false"),
        )
        assertEquals("salt-1", ld.string("LD"))

        val login = transport.post(
            linkedMapOf(
                "isTest" to "false",
                "goformId" to "LOGIN",
                "user" to "admin",
                "password" to "ABC",
            ),
        )
        assertEquals("0", login.string("result"))

        val getRequest = server.takeRequest()
        assertEquals("XMLHttpRequest", getRequest.getHeader("X-Requested-With"))
        assertEquals("http://127.0.0.1:${server.port}/", getRequest.getHeader("Referer"))
        assertEquals("/goform/goform_get_cmd_process?cmd=LD&multi_data=1&isTest=false", getRequest.path)

        val postRequest = server.takeRequest()
        assertEquals("XMLHttpRequest", postRequest.getHeader("X-Requested-With"))
        assertEquals(
            "isTest=false&goformId=LOGIN&user=admin&password=ABC",
            postRequest.body.readUtf8(),
        )
    }

    @Test
    fun `maps none secure connection to SECURE_HEADER`() {
        server.enqueue(MockResponse().setBody("""{"Error":"none secure connection"}"""))

        val error = assertThrows(GoformException::class.java) {
            runBlocking {
                transport.get(mapOf("cmd" to "network_type", "multi_data" to "1"))
            }
        }
        assertEquals(GoformException.Kind.SECURE_HEADER, error.kind)
    }

    @Test
    fun `html response means the session was lost`() {
        server.enqueue(MockResponse().setBody("<html>login</html>"))

        val error = assertThrows(GoformException::class.java) {
            runBlocking { transport.get(mapOf("cmd" to "RD")) }
        }
        assertEquals(GoformException.Kind.NOT_LOGGED_IN, error.kind)
    }

    @Test
    fun `cookie from login is replayed on later reads`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "JSESSIONID=abc123; Path=/goform; HttpOnly")
                .setBody("""{"result":0}"""),
        )
        server.enqueue(MockResponse().setBody("""{"RD":"rd-1"}"""))

        transport.post(linkedMapOf("goformId" to "LOGIN", "isTest" to "false"))
        transport.get(mapOf("cmd" to "RD"))

        server.takeRequest()
        val readRequest = server.takeRequest()
        assertTrue(readRequest.getHeader("Cookie")!!.contains("JSESSIONID=abc123"))
    }
}