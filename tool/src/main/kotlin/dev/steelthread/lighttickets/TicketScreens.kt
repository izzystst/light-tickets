package dev.steelthread.lighttickets

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@Composable
internal fun Action(text: String, onClick: () -> Unit) {
    LightText(text = text, variant = LightTextVariant.Copy,
        modifier = Modifier.fillMaxWidth().lightClickable(onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
internal fun Copy(text: String) {
    LightText(text = text, variant = LightTextVariant.Detail, modifier = Modifier.padding(16.dp))
}

@Composable
internal fun Frame(back: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    LightTheme {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Action(back, onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}

@InitialScreen
class HomeScreen(activity: SealedLightActivity) : SimpleLightScreen<Unit>(activity) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val store by lazy { TicketStore(lightContext.filesDir) }
    private var tickets by mutableStateOf<List<Ticket>>(emptyList())
    private var message by mutableStateOf("")
    private var busy by mutableStateOf(false)
    override fun willShow() { scope.launch { tickets = withContext(Dispatchers.IO) { store.list() } } }
    override fun onScreenDestroy() { scope.cancel() }

    @Composable override fun Content() {
        Frame("‹ Close Light Tickets", { goBack() }) {
            LightText(text = "Light Tickets", variant = LightTextVariant.Heading, modifier = Modifier.padding(16.dp))
            Action("Add a ticket · Receive wirelessly") { navigateTo({ ReceiveScreen(it) }) }
            if (message.isNotEmpty()) Copy(message)
            if (tickets.isEmpty()) Copy("Your tickets stay here for offline use. Send a PDF or ticket image from your phone or laptop.")
            tickets.forEach { ticket -> Action(ticket.title) { navigateTo({ TicketScreen(it, ticket) }) } }
            Action(if (busy) "Opening practice ticket…" else "Open practice ticket") {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            val demo = withContext(Dispatchers.IO) {
                                store.list().firstOrNull { it.demo } ?: store.importTicket(
                                    ByteArrayInputStream(lightContext.readAsset("demo-event.png")), "Practice ticket", true)
                            }
                            navigateTo({ TicketScreen(it, demo) })
                        } catch (_: Exception) { message = "Could not open the practice ticket. Please try again." }
                        finally { busy = false }
                    }
                }
            }
            Copy("For PDFs and static ticket images. Tickets that change their barcode require the provider’s own app. This preview uses wireless import; the prototype’s USB inbox is not yet carried over.")
        }
    }
}

class TicketScreen(activity: SealedLightActivity, private val ticket: Ticket) : SimpleLightScreen<Unit>(activity) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val store by lazy { TicketStore(lightContext.filesDir) }
    private var page by mutableIntStateOf(0)
    private var bitmap by mutableStateOf<Bitmap?>(null)
    private var notice by mutableStateOf("")
    private var visible by mutableStateOf(false)
    private var confirmRemove by mutableStateOf(false)
    private var fullScreen by mutableStateOf(false)
    private var optionsOpen by mutableStateOf(false)
    private var generation = 0

    override fun willShow() { visible = true; loadPage() }
    override fun willHide() { visible = false }
    override fun onAppPause() { visible = false }
    override fun onScreenDestroy() { generation++; scope.cancel(); bitmap = null }
    override fun goBack(result: Unit?) {
        when {
            confirmRemove -> confirmRemove = false
            fullScreen -> fullScreen = false
            optionsOpen -> optionsOpen = false
            else -> super.goBack(result)
        }
    }
    private fun loadPage() {
        val request = ++generation
        bitmap = null
        notice = "Opening ticket…"
        scope.launch {
            try {
                val rendered = withContext(Dispatchers.IO) { TicketRenderer.render(store.file(ticket), ticket.kind, page) }
                if (request == generation) { bitmap = rendered; notice = "" }
            } catch (_: Exception) { if (request == generation) notice = "Could not display this page. Your saved file has not been changed." }
        }
    }

    @Composable override fun Content() {
        if (!visible) return
        // Supported Compose window protection, without accessing the host Activity.
        Dialog(onDismissRequest = { goBack() }, properties = DialogProperties(
            securePolicy = if (ticket.demo) SecureFlagPolicy.SecureOff else SecureFlagPolicy.SecureOn,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            LightTheme {
                Column(Modifier.fillMaxSize().background(Color.Black).keepScreenOn()) {
                    if (fullScreen) Action("‹ Back to ticket") { fullScreen = false }
                    else if (optionsOpen || confirmRemove) Action("‹ Back to ticket") {
                        optionsOpen = false; confirmRemove = false
                    }
                    else Row {
                        Box(Modifier.weight(0.65f)) { Action("‹ Back to tickets") { goBack() } }
                        Box(Modifier.weight(0.35f)) { Action(if (optionsOpen) "Done" else "Options") { optionsOpen = !optionsOpen } }
                    }
                    if (confirmRemove) {
                        Copy("Remove this saved copy? Your original ticket is unaffected.")
                        Action("Remove ticket") {
                            scope.launch {
                                if (withContext(Dispatchers.IO) { store.remove(ticket) }) super.goBack(null)
                                else { confirmRemove = false; notice = "Could not remove ticket. Please try again." }
                            }
                        }
                        Action("Keep ticket") { confirmRemove = false }
                    } else if (optionsOpen) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Copy(ticket.title)
                            Copy("Page ${page + 1} of ${ticket.pages}. Double-tap the code to enlarge it, or pinch and drag. Full screen gives the ticket more room without stretching it.")
                            Action("Full screen") { optionsOpen = false; fullScreen = true }
                            Action("Remove saved copy") { confirmRemove = true }
                            Action("Back to ticket") { optionsOpen = false }
                        }
                    } else {
                        val current = bitmap
                        if (current == null) Box(Modifier.weight(1f)) { Copy(notice) }
                        else {
                            var scale by remember(current, fullScreen) { mutableFloatStateOf(1f) }
                            var offset by remember(current, fullScreen) { mutableStateOf(Offset.Zero) }
                            Box(Modifier.weight(1f).fillMaxWidth().background(Color.White).clipToBounds()
                                .pointerInput(current, fullScreen) {
                                    detectTapGestures(onDoubleTap = { tapped ->
                                        if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                                        else {
                                            scale = 3f
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            val shifted = (center - tapped) * scale
                                            offset = Offset(shifted.x.coerceIn(-size.width.toFloat(), size.width.toFloat()),
                                                shifted.y.coerceIn(-size.height.toFloat(), size.height.toFloat()))
                                        }
                                    })
                                }.pointerInput(current, fullScreen) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val previousScale = scale
                                        scale = (previousScale * zoom).coerceIn(1f, 6f)
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val focus = centroid - center
                                        val shifted = (offset - focus) * (scale / previousScale) + focus + pan
                                        val boundX = size.width * (scale - 1f) / 2
                                        val boundY = size.height * (scale - 1f) / 2
                                        offset = if (scale == 1f) Offset.Zero else Offset(
                                            shifted.x.coerceIn(-boundX, boundX),
                                            shifted.y.coerceIn(-boundY, boundY))
                                    }
                                }) {
                                Image(current.asImageBitmap(), "Ticket page ${page + 1}",
                                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y; clip = true
                                    })
                            }
                            if (!fullScreen) Row {
                                Box(Modifier.weight(0.65f)) { Action("Full screen") { fullScreen = true } }
                                Box(Modifier.weight(0.35f)) { Action("Fit") { scale = 1f; offset = Offset.Zero } }
                            }
                        }
                        if (ticket.pages > 1 && !fullScreen) Row {
                            Box(Modifier.weight(1f)) { Action("‹ Previous") { if (page > 0) { page--; loadPage() } } }
                            Box(Modifier.weight(1f)) { Action("Next ›") { if (page + 1 < ticket.pages) { page++; loadPage() } } }
                        }
                    }
                }
            }
        }
    }
}
