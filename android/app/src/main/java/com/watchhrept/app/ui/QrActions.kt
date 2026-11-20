package com.watchhrept.app.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * The login QR code has to be scanned by the Zalo app, which usually lives on this same phone.
 * Zalo's QR scanner can read a code from a picture in the gallery, so offer to save or share it.
 */
object QrActions {
    /** Saves [qr] to Pictures/Zalo Lite. Returns false when it couldn't. */
    fun saveToGallery(context: Context, qr: Bitmap): Boolean = try {
        save(context, withQuietZone(qr), "zalo-login-${System.currentTimeMillis()}.png")
    } catch (e: Exception) {
        false
    }

    private fun save(context: Context, image: Bitmap, name: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Zalo Lite")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            val out = context.contentResolver.openOutputStream(uri) ?: return false
            out.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Zalo Lite")
            dir.mkdirs()
            val file = File(dir, name)
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            @Suppress("DEPRECATION")
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, android.net.Uri.fromFile(file)))
        }
        return true
    }

    /** Opens the share sheet with the QR image, e.g. to send it to a computer or another phone. */
    fun share(context: Context, qr: Bitmap) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "zalo-login.png")
        file.outputStream().use { withQuietZone(qr).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share login QR code").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Scanners need a white border around the code; the image from Zalo is tight-cropped. */
    private fun withQuietZone(qr: Bitmap): Bitmap {
        val scale = maxOf(1, 600 / maxOf(qr.width, 1))
        val scaled = Bitmap.createScaledBitmap(qr, qr.width * scale, qr.height * scale, false)
        val pad = scaled.width / 8
        val out = Bitmap.createBitmap(scaled.width + 2 * pad, scaled.height + 2 * pad, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(scaled, pad.toFloat(), pad.toFloat(), null)
        return out
    }
}
