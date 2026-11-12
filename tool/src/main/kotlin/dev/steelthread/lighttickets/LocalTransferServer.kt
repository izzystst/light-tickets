package dev.steelthread.lighttickets

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One foreground upload, paired with a fresh secret. No saved-ticket download routes. */
class LocalTransferServer(
    bind: InetAddress, private val directory: File, private val assets: Map<String, ByteArray>,
    private val scriptHash: String, private val listener: (Pending) -> Unit,
    lifetimeMillis: Long = 180_000, port: Int = 54874
) : AutoCloseable {
    data class Pending(val file: File, val name: String, val bytes: Long)
    private val random = SecureRandom()
    private val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    val pairingCode: String = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000))
    private val socket = ServerSocket()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val daemon = ThreadFactory { task -> Thread(task, "ticket-transfer").apply { isDaemon = true } }
    private val clients = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(2), daemon, ThreadPoolExecutor.AbortPolicy())
    private val expiry = Executors.newSingleThreadScheduledExecutor(daemon)
    private val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lifetimeMillis)
    private val host: String
    @Volatile private var closed = false
    @Volatile private var stage = "waiting"
    private var pending: Pending? = null
    private var pairFailures = 0
    val address: String get() = "http://$host/"
    val pairingUrl: String get() = "$address#key=$token"
    val state: String get() = if (closed) "closed" else stage
    val secondsLeft: Long get() = TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime()).coerceAtLeast(0)

    init {
        require(lifetimeMillis > 0)
        try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not prepare receiving folder.")
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(bind, port), 3)
            host = "${bind.hostAddress}:${socket.localPort}"
            expiry.schedule({ close() }, lifetimeMillis, TimeUnit.MILLISECONDS)
            daemon.newThread { acceptLoop() }.start()
        } catch (error: Exception) {
            socket.close(); clients.shutdownNow(); expiry.shutdownNow(); throw error
        }
    }

    @Synchronized fun saving() { if (!closed && stage == "pending") stage = "saving" }
    @Synchronized fun finish(accepted: Boolean) {
        pending?.file?.delete(); pending = null
        if (!closed) stage = if (accepted) "saved" else "rejected"
    }

    private fun acceptLoop() {
        while (!closed) {
            try {
                val client = socket.accept().apply { soTimeout = 10_000 }
                sockets.add(client)
                try { clients.execute { handle(client) } }
                catch (_: RejectedExecutionException) { sockets.remove(client); client.close() }
            } catch (_: IOException) { if (!closed) close() }
        }
    }

    private data class Request(val method: String, val path: String, val headers: Map<String, String>)
    private fun handle(client: Socket) {
        try {
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val request = readRequest(input)
            if (request.headers["host"] != host) { respond(output, 403, "text/plain", "Wrong receiver address."); return }
            val origin = request.headers["origin"]
            if ((origin != null && origin != "http://$host") || request.headers["sec-fetch-site"] == "cross-site") {
                respond(output, 403, "text/plain", "Open the receiver page directly."); return
            }
            if (request.method == "GET" && request.path in setOf("/", "/style.css")) {
                val data = assets[request.path]
                if (data == null) respond(output, 404, "text/plain", "Not found.")
                else respond(output, 200, if (request.path == "/") "text/html" else "text/css", data)
                return
            }
            if (request.method == "POST" && request.path == "/pair") {
                val supplied = readBytes(input, contentLength(request, 16).toInt()).toString(Charsets.US_ASCII)
                val matches = synchronized(this) {
                    (!closed && pairFailures < 5 && equal(supplied, pairingCode)).also { if (!it) pairFailures++ }
                }
                if (matches) respond(output, 200, "application/json", "{\"key\":\"$token\"}")
                else {
                    respond(output, 403, "text/plain", if (pairFailures >= 5)
                        "Pairing closed. Start a new session on the Light Phone." else "Incorrect pairing code.")
                    if (pairFailures >= 5) close()
                }
                return
            }
            if (!equal(request.headers["authorization"].orEmpty(), "Bearer $token")) {
                respond(output, 403, "text/plain", "Pair with the Light Phone first."); return
            }
            when {
                request.method == "GET" && request.path == "/status" -> respond(output, 200, "application/json", "{\"status\":\"$state\"}")
                request.method == "POST" && request.path == "/upload" -> receive(request, input, output)
                else -> respond(output, 404, "text/plain", "Not found.")
            }
        } catch (_: ProtocolException) {
            try { respond(client.getOutputStream(), 400, "text/plain", "Invalid transfer request.") } catch (_: IOException) { }
        } catch (_: IOException) {
            // Never log request data, ticket names, pairing codes, or contents.
        } finally { sockets.remove(client); try { client.close() } catch (_: IOException) { } }
    }

    private fun receive(request: Request, input: InputStream, output: OutputStream) {
        val length = contentLength(request, ImportLimits.MAX_BYTES)
        if (length < 1) throw ProtocolException("empty upload")
        val encoded = request.headers["x-ticket-name"] ?: throw ProtocolException("missing name")
        if (encoded.length > 1200) throw ProtocolException("name too long")
        val name = try { ImportLimits.cleanName(URLDecoder.decode(encoded, "UTF-8")) }
            catch (_: IllegalArgumentException) { throw ProtocolException("bad name") }
        synchronized(this) {
            if (closed || stage != "waiting") { respond(output, 409, "text/plain", "Start another receiving session on the Light Phone."); return }
            stage = "receiving"
        }
        var temporary: File? = null
        var handedOff = false
        try {
            temporary = File.createTempFile("incoming-", ".partial", directory)
            temporary.outputStream().use { file ->
                val buffer = ByteArray(16384)
                var remaining = length
                while (remaining > 0) {
                    if (closed || System.nanoTime() >= deadline) throw IOException("session ended")
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw EOFException()
                    file.write(buffer, 0, count); remaining -= count
                }
            }
            val received = Pending(temporary, name, length)
            synchronized(this) {
                if (closed) throw IOException("session ended")
                pending = received; stage = "pending"; handedOff = true
            }
            listener(received)
            respond(output, 202, "application/json", "{\"status\":\"pending\"}")
        } finally {
            if (!handedOff) {
                temporary?.delete()
                synchronized(this) { if (!closed) stage = "waiting" }
            }
        }
    }

    private fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    private fun readRequest(input: InputStream): Request {
        val budget = intArrayOf(8192)
        val line = readLine(input, budget).split(' ')
        if (line.size != 3 || line[2] != "HTTP/1.1" || line[0] !in setOf("GET", "POST") || !line[1].startsWith('/'))
            throw ProtocolException("request line")
        val headers = mutableMapOf<String, String>()
        while (true) {
            val header = readLine(input, budget)
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            if (colon <= 0) throw ProtocolException("header")
            val name = header.substring(0, colon).lowercase(Locale.ROOT)
            if (!name.matches(Regex("[a-z0-9-]+")) || headers.containsKey(name)) throw ProtocolException("ambiguous header")
            headers[name] = header.substring(colon + 1).trim()
        }
        if (headers.containsKey("transfer-encoding") || headers["content-encoding"]?.let { it != "identity" } == true)
            throw ProtocolException("encoding")
        return Request(line[0], line[1], headers)
    }
    private fun readLine(input: InputStream, budget: IntArray): String {
        val bytes = ByteArrayOutputStream()
        while (budget[0]-- > 0) {
            val character = input.read()
            if (character < 0) throw EOFException()
            if (character == 13) {
                if (--budget[0] < 0 || input.read() != 10) throw ProtocolException("line ending")
                return bytes.toString("US-ASCII")
            }
            if (character !in 32..126) throw ProtocolException("header character")
            bytes.write(character)
        }
        throw ProtocolException("headers too large")
    }
    private fun contentLength(request: Request, max: Long): Long {
        val value = request.headers["content-length"].orEmpty()
        if (!value.matches(Regex("[0-9]{1,10}"))) throw ProtocolException("length")
        return value.toLong().also { if (it > max) throw ProtocolException("file too large") }
    }
    private fun readBytes(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) { val count = input.read(bytes, offset, length - offset); if (count < 0) throw EOFException(); offset += count }
        return bytes
    }
    private fun respond(output: OutputStream, status: Int, type: String, text: String) = respond(output, status, type, text.toByteArray(Charsets.UTF_8))
    private fun respond(output: OutputStream, status: Int, type: String, bytes: ByteArray) {
        val headers = "HTTP/1.1 $status Response\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Connection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'sha256-$scriptHash'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII)); output.write(bytes); output.flush()
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        try { socket.close() } catch (_: IOException) { }
        sockets.forEach { try { it.close() } catch (_: IOException) { } }
        clients.shutdownNow(); expiry.shutdownNow()
        pending?.file?.delete(); pending = null
    }
}
