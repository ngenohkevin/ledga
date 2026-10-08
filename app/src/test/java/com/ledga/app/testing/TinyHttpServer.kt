package com.ledga.app.testing

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/** One request as [TinyHttpServer] read it: the path, and the headers with lower-case names. */
data class TinyRequest(val path: String, val headers: Map<String, String>)

/** A route's answer. [declaredLength] replaces the true Content-Length (a response that ends early). */
class TinyResponse(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
    val declaredLength: Long? = null,
)

/**
 * A minimal HTTP/1.1 server on 127.0.0.1 for the update client's tests: `com.sun.net.httpserver` isn't on an Android
 * module's test classpath. One request per connection; every answer closes it.
 */
class TinyHttpServer(private val route: (TinyRequest) -> TinyResponse) : AutoCloseable {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val base: String = "http://127.0.0.1:${socket.localPort}"
    val requests: MutableList<TinyRequest> = Collections.synchronizedList(mutableListOf())

    init {
        thread(isDaemon = true, name = "tiny-http") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                thread(isDaemon = true) { client.use(::serve) }
            }
        }
    }

    private fun serve(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return
        val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val request = TinyRequest(requestLine.split(' ')[1], headers)
        requests += request
        val response = route(request)
        val head = buildString {
            append("HTTP/1.1 ${response.status} Status\r\n")
            response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
            if (response.status != 304) append("Content-Length: ${response.declaredLength ?: response.body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        val out = client.getOutputStream()
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(response.body)
        out.flush()
    }

    override fun close() = socket.close()
}
