package dev.steelthread.lighttickets

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object ImportLimits {
    const val MAX_BYTES = 20L * 1024 * 1024
    const val MAX_PAGES = 100
    const val MAX_TICKETS = 50

    fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(16384)
        var total = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw IOException("Import interrupted. Try again.")
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BYTES) throw IOException("Ticket is larger than 20 MB.")
            output.write(buffer, 0, count)
        }
        if (total == 0L) throw IOException("This file is empty.")
        return total
    }

    fun cleanName(name: String?): String = name.orEmpty()
        .replace(Regex("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]"), " ")
        .trim().take(120).ifBlank { "Imported ticket" }
}

data class Ticket(val id: String, val title: String, val kind: String,
                  val pages: Int, val saved: Long, val demo: Boolean)

/** App-private files; metadata is committed atomically only after validation. */
class TicketStore(filesDir: File) {
    private val directory = File(filesDir, "tickets").apply {
        if (!isDirectory && !mkdirs()) throw IOException("Could not prepare ticket storage.")
    }
    // All instances in the process share one lock, including overlapping screens.
    companion object { private val lock = Any() }

    fun file(ticket: Ticket): File {
        require(ticket.id.matches(Regex("[a-f0-9-]{36}")))
        return File(directory, "${ticket.id}.ticket")
    }

    fun list(): List<Ticket> = synchronized(lock) {
        directory.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9-]{36}\\.json")) }
            .mapNotNull { metadata ->
                try {
                    val json = JSONObject(AtomicFile(metadata).openRead().bufferedReader().use { it.readText() })
                    Ticket(metadata.name.removeSuffix(".json"), json.getString("title"),
                        json.getString("kind"), json.getInt("pages"), json.getLong("saved"),
                        json.optBoolean("demo")).takeIf { file(it).isFile }
                } catch (_: Exception) { null }
            }.sortedByDescending { it.saved }
    }

    fun importTicket(input: InputStream, name: String?, demo: Boolean = false): Ticket = synchronized(lock) {
        if (list().size >= ImportLimits.MAX_TICKETS) throw IOException("Ticket shelf is full. Remove an old ticket first.")
        val id = UUID.randomUUID().toString()
        val temporary = File(directory, "$id.partial")
        val target = File(directory, "$id.ticket")
        val metadata = AtomicFile(File(directory, "$id.json"))
        try {
            temporary.outputStream().use { ImportLimits.copy(input, it) }
            val header = temporary.inputStream().use { it.readNBytes(5) }
            val kind: String
            val pages: Int
            if (header.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) {
                kind = "pdf"
                ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { pdf ->
                        pages = pdf.pageCount
                        if (pages !in 1..ImportLimits.MAX_PAGES) throw IOException("Use a PDF with 1 to 100 pages.")
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                    bounds.outMimeType !in setOf("image/png", "image/jpeg", "image/webp"))
                    throw IOException("Choose a PDF, PNG, JPEG, or WebP ticket.")
                if (bounds.outWidth > 30000 || bounds.outHeight > 30000 ||
                    bounds.outWidth.toLong() * bounds.outHeight > 100_000_000)
                    throw IOException("Image is too large. Use a smaller ticket image.")
                kind = "image"
                pages = 1
            }
            val ticket = Ticket(id, ImportLimits.cleanName(name), kind, pages, System.currentTimeMillis(), demo)
            if (!temporary.renameTo(target)) throw IOException("Could not save ticket.")
            val json = JSONObject().put("title", ticket.title).put("kind", kind).put("pages", pages)
                .put("saved", ticket.saved).put("demo", demo)
            val output = metadata.startWrite()
            try {
                output.write(json.toString().toByteArray(Charsets.UTF_8))
                metadata.finishWrite(output)
            } catch (error: Exception) { metadata.failWrite(output); throw error }
            ticket
        } catch (error: Exception) {
            temporary.delete(); target.delete(); metadata.delete()
            throw error
        }
    }

    fun remove(ticket: Ticket): Boolean = synchronized(lock) {
        // Keep the index if file deletion fails, so the user can retry.
        if (file(ticket).exists() && !file(ticket).delete()) return@synchronized false
        AtomicFile(File(directory, "${ticket.id}.json")).delete()
        true
    }
}
