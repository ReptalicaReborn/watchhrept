package com.watchhrept.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.watchhrept.app.data.SessionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    status: SessionStatus,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val qr = remember(status.qrImage) { status.qrImage?.let(::decodeDataUrl) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    fun save() {
        val bitmap = qr ?: return
        val ok = QrActions.saveToGallery(context, bitmap)
        Toast.makeText(
            context,
            if (ok) "Saved to Pictures. In Zalo, open the QR scanner and pick it from your gallery." else "Couldn't save the image",
            Toast.LENGTH_LONG,
        ).show()
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save()
    }
    fun saveWithPermission() {
        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (legacy) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { showHelp = !showHelp }) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "How to log in")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    qr != null -> {
                        Spacer(Modifier.height(8.dp))
                        Text("Scan to log in", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                status.scannedBy != null -> "Scanned by ${status.scannedBy}. Tap “Log in” on the phone to finish."
                                else -> "Scan this code with the Zalo app on another phone, or save it and pick it in Zalo's QR scanner on this one."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Login QR code",
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .size(260.dp)
                                .background(Color.White, RoundedCornerShape(20.dp))
                                .padding(14.dp),
                        )
                    }
                    status.state == "logging_in" -> {
                        Spacer(Modifier.height(96.dp))
                        CircularProgressIndicator()
                        Spacer(Modifier.height(24.dp))
                        Text(
                            if (status.retrying) "Waiting for a connection…" else "Logging in…",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(48.dp))
                        Box(
                            Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.QrCode2,
                                null,
                                Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                        Text("Chat with Zalo, your way", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Log in like Zalo on a computer: get a QR code here and confirm it in the Zalo app.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                status.error?.let { error ->
                    Spacer(Modifier.height(20.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    ) {
                        Text(error, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (showHelp) {
                    Spacer(Modifier.height(20.dp))
                    LoginHelp(onClose = { showHelp = false })
                }
                Spacer(Modifier.height(16.dp))
            }

            // Actions sit at the bottom, full width, within easy reach of the thumb.
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    qr != null -> {
                        Button(onClick = { saveWithPermission() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Save image")
                        }
                        OutlinedButton(onClick = { QrActions.share(context, qr) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Share")
                        }
                    }
                    status.retrying -> {
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Retry now") }
                        OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Use a QR code instead") }
                    }
                    status.state == "logged_out" -> {
                        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Get QR code") }
                        TextButton(onClick = { showHelp = true }, modifier = Modifier.fillMaxWidth()) { Text("How does this work?") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginHelp(onClose: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("How to log in", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val steps = listOf(
                "Tap “Get QR code”.",
                "Zalo is on this phone: tap “Save image”, then in Zalo tap the QR icon next to search, " +
                    "choose the picture button and pick the saved code.",
                "Zalo is on another phone: scan the code on this screen with that phone's Zalo QR scanner.",
                "Confirm the login in Zalo. Only one web session can be active, so this logs out Zalo Web elsewhere.",
            )
            steps.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Got it") }
        }
    }
}
