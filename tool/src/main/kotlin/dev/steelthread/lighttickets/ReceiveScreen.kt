package dev.steelthread.lighttickets

import android.graphics.Bitmap
import android.graphics.Color as PixelColor
import android.util.AtomicFile
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

internal object BrowserAssets {
    fun scriptHash(html: ByteArray): String {
        val document = html.toString(Charsets.UTF_8)
        val start = document.indexOf("<script>")
        val end = document.indexOf("</script>", start)
        require(start >= 0 && end > start && document.indexOf("<script>", start + 8) == -1)
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
            .digest(document.substring(start + 8, end).toByteArray(Charsets.UTF_8)))
    }
}

/** Local interface enumeration identifies the Wi-Fi address without unsealing Android Context. */
internal fun privateWifiAddress(wifiActive: Boolean): InetAddress {
    if (wifiActive) {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().filter {
            it.isUp && !it.isLoopback && (it.name.startsWith("wlan") || it.name.startsWith("wifi"))
        }.forEach { network ->
            network.inetAddresses.toList().firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.let { return it }
        }
    }
    throw IOException("Connect this phone to your home Wi-Fi first. Receiving needs a private Wi-Fi address.")
}

class ReceiveScreen(activity: SealedLightActivity) : SimpleLightScreen<Unit>(activity) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bookmark by lazy { AtomicFile(File(lightContext.filesDir, "transfer-bookmark.txt")) }
    private var receiver: LocalTransferServer? = null
    private var generation = 0
    private var visible by mutableStateOf(false)
    private var starting by mutableStateOf(false)
    private var address by mutableStateOf("")
    private var code by mutableStateOf("")
    private var qr by mutableStateOf<Bitmap?>(null)
    private var notice by mutableStateOf("")
    private var status by mutableStateOf("")
    private var bookmarkNotice by mutableStateOf("")
    private var pending by mutableStateOf<LocalTransferServer.Pending?>(null)
    private var saving by mutableStateOf(false)

    override fun willShow() { visible = true }
    override fun willHide() { visible = false; stop() }
    override fun onAppPause() { visible = false; stop(); notice = "Receiving stopped when you left the app. Start again when ready." }
    override fun onScreenDestroy() { stop(); scope.cancel() }
    private fun stop() {
        generation++
        receiver?.close(); receiver = null
        address = ""; code = ""; qr = null; pending = null; saving = false; starting = false
    }
    private fun wifiAddress() = privateWifiAddress(lightContext.connectivity.currentStatus.isWifi)
    private fun start() {
        stop()
        val request = generation
        starting = true; notice = ""
        scope.launch {
            var created: LocalTransferServer? = null
            try {
                val prepared = withContext(Dispatchers.IO) {
                    val html = lightContext.readAsset("transfer/index.html")
                    val assets = mapOf("/" to html, "/style.css" to lightContext.readAsset("transfer/style.css"))
                    val session = LocalTransferServer(wifiAddress(), File(lightContext.filesDir, "wireless-incoming"),
                        assets, BrowserAssets.scriptHash(html), { received ->
                            scope.launch { if (request == generation && visible) pending = received }
                        })
                    created = session
                    val matrix = QRCodeWriter().encode(session.pairingUrl, BarcodeFormat.QR_CODE, 720, 720,
                        mapOf(EncodeHintType.MARGIN to 4))
                    val pixels = IntArray(720 * 720) { if (matrix[it % 720, it / 720]) PixelColor.BLACK else PixelColor.WHITE }
                    val image = Bitmap.createBitmap(pixels, 720, 720, Bitmap.Config.RGB_565)
                    val previous = try { bookmark.openRead().bufferedReader().use { it.readText() } } catch (_: IOException) { null }
                    Triple(session, image, previous)
                }
                if (request != generation || !visible) { prepared.first.close(); return@launch }
                receiver = prepared.first; qr = prepared.second
                address = prepared.first.address; code = prepared.first.pairingCode
                bookmarkNotice = if (prepared.third == address) "" else if (prepared.third == null)
                    "Save the upload address below as a bookmark." else "Address changed—update your bookmark to the address below."
                starting = false
                var ticks = 0
                while (isActive && request == generation) {
                    val session = receiver ?: break
                    if (session.state == "closed") { stop(); notice = "Session ended. Start again to receive another ticket."; break }
                    if (ticks++ % 6 == 0) {
                        val current = withContext(Dispatchers.IO) { "http://${wifiAddress().hostAddress}:54874/" }
                        if (current != session.address) { stop(); notice = "Wi-Fi address changed. Start receiving again for the updated link."; break }
                    }
                    val description = when (session.state) {
                        "receiving" -> "Receiving file…"
                        "pending" -> "Waiting for your approval"
                        "saving" -> "Saving…"
                        "saved" -> "Ticket saved for offline use"
                        "rejected" -> "File declined"
                        else -> "Ready to receive"
                    }
                    status = "$description\nReceiving closes in ${session.secondsLeft} seconds"
                    delay(500)
                }
            } catch (error: Exception) {
                created?.close()
                if (request == generation) {
                    stop()
                    notice = if (error is BindException) "The upload address is busy. Stop any other receiving session, then try again."
                        else if (error is IOException) "Connect to home Wi-Fi, then try again. If another ticket app is receiving, stop it first."
                        else "Could not start receiving. Please try again."
                }
            }
        }
    }

    private fun acknowledgeBookmark() {
        try {
            val output = bookmark.startWrite()
            try { output.write(address.toByteArray(Charsets.UTF_8)); bookmark.finishWrite(output) }
            catch (error: Exception) { bookmark.failWrite(output); throw error }
            bookmarkNotice = ""
        } catch (_: IOException) { notice = "Could not remember this address. Please try again." }
    }

    private fun save(incoming: LocalTransferServer.Pending) {
        val session = receiver ?: return
        if (saving || session.state != "pending") return
        saving = true; session.saving()
        val request = generation
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    incoming.file.inputStream().use { TicketStore(lightContext.filesDir).importTicket(it, incoming.name) }
                    true
                } catch (_: Exception) { false }
            }
            session.finish(result)
            if (request == generation) {
                pending = null; saving = false
                notice = if (result) "Ticket saved for offline use." else
                    "Could not save this file. Use an unprotected PDF or PNG/JPEG/WebP image up to 20 MB."
            }
        }
    }

    @Composable override fun Content() {
        if (!visible) return
        Dialog(onDismissRequest = { stop(); goBack() }, properties = DialogProperties(
            securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(if (address.isNotEmpty()) Modifier.fillMaxSize().keepScreenOn() else Modifier.fillMaxSize()) {
                Frame("‹ Back to tickets", { stop(); goBack() }) {
                    if (notice.isNotEmpty()) Copy(notice)
                    val incoming = pending
                    when {
                        incoming != null -> {
                            Copy("Save this ticket?\n${incoming.name}\n${String.format(Locale.ROOT, "%.1f MB", incoming.bytes / 1048576.0)} received from your paired device.")
                            Action(if (saving) "Saving…" else "Save") { save(incoming) }
                            if (!saving) Action("Decline") { receiver?.finish(false); pending = null; notice = "File declined." }
                        }
                        address.isEmpty() -> {
                            Copy("Receive a ticket\nSend from your iPhone or laptop browser. Put both devices on the same Wi-Fi.")
                            Copy("You can bookmark the upload address and use a fresh pairing code each time. Use trusted home Wi-Fi: this local transfer is not encrypted.")
                            Action(if (starting) "Starting…" else "Start receiving") { if (!starting) start() }
                        }
                        else -> {
                            if (bookmarkNotice.isNotEmpty()) {
                                Copy(bookmarkNotice)
                                Action("I've updated my bookmark") { acknowledgeBookmark() }
                            }
                            Copy("iPhone: scan this code with Camera.")
                            qr?.let { Image(it.asImageBitmap(), "Scan to open the ticket uploader", modifier = Modifier.fillMaxWidth().size(190.dp)) }
                            Copy("Bookmark this address:\n$address\nPairing code: $code")
                            Copy("Same address on this Wi-Fi; a new code each session. A changed Wi-Fi address needs a new bookmark.")
                            Copy(status)
                            Action("Stop receiving") { stop(); notice = "Receiving stopped." }
                        }
                    }
                }
            }
        }
    }
}
