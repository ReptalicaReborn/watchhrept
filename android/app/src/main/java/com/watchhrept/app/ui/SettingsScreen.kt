package com.watchhrept.app.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.watchhrept.app.data.ApiClient
import com.watchhrept.app.data.Appearance
import com.watchhrept.app.data.ServerSettings
import com.watchhrept.app.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: ServerSettings,
    appearance: Appearance,
    profileName: String?,
    profileId: String?,
    loggedIn: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSave: (ServerSettings) -> Unit,
    onAppearance: (Appearance) -> Unit,
    onRestartServer: () -> Unit,
    onLogout: () -> Unit,
) {
    var onDevice by rememberSaveable { mutableStateOf(current.onDevice) }
    var serverUrl by rememberSaveable { mutableStateOf(current.serverUrl) }
    var token by rememberSaveable { mutableStateOf(current.token) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var pickTheme by rememberSaveable { mutableStateOf(false) }
    val edited = ServerSettings(onDevice, serverUrl.trim(), token.trim())
    val changed = edited != current
    val urlValid = onDevice || ApiClient.normalizeServerUrl(serverUrl) != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (loggedIn) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(profileName.orEmpty(), "", key = profileId ?: "me", isGroup = false, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(profileName ?: "Zalo account", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Logged in to Zalo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }

            SectionHeader("Appearance")
            ListItem(
                modifier = Modifier.clickable { pickTheme = true },
                leadingContent = { Icon(Icons.Outlined.DarkMode, null) },
                headlineContent = { Text("Theme") },
                supportingContent = { Text(appearance.themeMode.label) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    modifier = Modifier.clickable { onAppearance(appearance.copy(dynamicColor = !appearance.dynamicColor)) },
                    leadingContent = { Icon(Icons.Outlined.Palette, null) },
                    headlineContent = { Text("Wallpaper colors") },
                    supportingContent = { Text("Use colors from your wallpaper instead of blue") },
                    trailingContent = {
                        Switch(
                            checked = appearance.dynamicColor,
                            onCheckedChange = { onAppearance(appearance.copy(dynamicColor = it)) },
                        )
                    },
                )
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionHeader("Zalo runs on")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SegmentedButton(
                    selected = onDevice,
                    onClick = { onDevice = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { SegmentedButtonDefaults.Icon(onDevice) { Icon(Icons.Outlined.PhoneAndroid, null) } },
                ) { Text("This phone") }
                SegmentedButton(
                    selected = !onDevice,
                    onClick = { onDevice = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { SegmentedButtonDefaults.Icon(!onDevice) { Icon(Icons.Outlined.Cloud, null) } },
                ) { Text("A server") }
            }
            Text(
                if (onDevice) {
                    "The Zalo connection runs inside this app and stays connected in the background."
                } else {
                    "Connect to a watchhrept server you run yourself (see the README)."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (!onDevice) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server address") },
                        placeholder = { Text("https://zalo.example.com") },
                        singleLine = true,
                        isError = serverUrl.isNotBlank() && !urlValid,
                        supportingText = if (serverUrl.isNotBlank() && !urlValid) ({ Text("Not a valid address") }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Access token") },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showToken) "Hide token" else "Show token",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (changed) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { onSave(edited) }, enabled = urlValid) { Text("Save changes") }
                }
            }

            if (current.onDevice || loggedIn) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                SectionHeader("Account")
            }
            if (current.onDevice) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onRestartServer),
                    leadingContent = { Icon(Icons.Outlined.RestartAlt, null) },
                    headlineContent = { Text("Restart Zalo service") },
                    supportingContent = { Text("If messages stop arriving") },
                )
            }
            if (loggedIn) {
                ListItem(
                    modifier = Modifier.clickable { confirmLogout = true },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("Log out of Zalo", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Removes the session and message history from this device") },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickTheme) {
        AlertDialog(
            onDismissRequest = { pickTheme = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = appearance.themeMode == mode, onClick = {
                                    onAppearance(appearance.copy(themeMode = mode))
                                    pickTheme = false
                                })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = appearance.themeMode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickTheme = false }) { Text("Cancel") } },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to scan a QR code with the Zalo app to log in again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Log out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}
