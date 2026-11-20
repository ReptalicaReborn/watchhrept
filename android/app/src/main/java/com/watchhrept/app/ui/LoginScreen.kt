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
import androidx.compose.material3.FilledTonalButton
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
                title = { Text("Log in to Zalo") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                qr != null -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = qr.asImageBitmap(),
                                contentDescription = "Login QR code",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(Color.White, RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                status.scannedBy?.let { "Scanned by $it" } ?: "Scan with the Zalo app",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (status.scannedBy != null) {
                                    "Tap “Log in” on the phone to finish."
                                } else {
                                    "On another phone, scan this code. On this phone, save it and pick it in Zalo's QR scanner."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { saveWithPermission() }) {
                                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text("Save image")
                                }
                                OutlinedButton(onClick = { qr.let { QrActions.share(context, it) } }) {
                                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
                status.state == "logging_in" -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (status.retrying) "Waiting for a connection…" else "Logging in…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                else -> {
                    Spacer(Modifier.height(32.dp))
                    Icon(
                        Icons.Outlined.QrCode2,
                        null,
                        Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Log in with a QR code", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Like Zalo on a computer: you confirm the login in the Zalo app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onLogin) { Text("Get QR code") }
                }
            }

            status.error?.let { error ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                ) {
                    Text(error, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // A saved session is waiting for the network: let the user retry now or start over.
            if (status.retrying) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLogin) { Text("Use a QR code") }
                    Button(onClick = onRetry) { Text("Retry now") }
                }
            }

            if (showHelp) {
                Spacer(Modifier.height(24.dp))
                LoginHelp(onClose = { showHelp = false })
            }
        }
    }
}

@Composable
private fun LoginHelp(onClose: () -> Unit) {
    Card(modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
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
                Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
            }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Got it") }
        }
    }
}
