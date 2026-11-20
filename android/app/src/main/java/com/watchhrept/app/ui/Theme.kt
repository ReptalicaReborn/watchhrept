package com.watchhrept.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.watchhrept.app.data.Appearance
import com.watchhrept.app.data.ThemeMode

// A calm messenger look: plain white (or near-black) surfaces, one strong blue for actions and your own bubbles.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2B5BD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF00174C),
    secondary = Color(0xFF585E72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF735471),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFED7F9),
    onTertiaryContainer = Color(0xFF2A122B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF5E5F67),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6FA),
    surfaceContainer = Color(0xFFF0F0F5),
    surfaceContainerHigh = Color(0xFFEAEAEF),
    surfaceContainerHighest = Color(0xFFE4E4E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF1F43AE),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFC0C6DD),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFE1BBDC),
    onTertiary = Color(0xFF412741),
    tertiaryContainer = Color(0xFF5A3D58),
    onTertiaryContainer = Color(0xFFFED7F9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121214),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFA9AAB4),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color(0xFF0D0D0F),
    surfaceContainerLow = Color(0xFF1A1A1D),
    surfaceContainer = Color(0xFF1E1E21),
    surfaceContainerHigh = Color(0xFF28282B),
    surfaceContainerHighest = Color(0xFF333336),
)

private val OutgoingBlue = Color(0xFF2C67E8)

/** Colors of the conversation screen that Material's color scheme has no slot for. */
@Immutable
data class ChatColors(
    val background: Color,
    val outgoing: Color,
    val onOutgoing: Color,
    val incoming: Color,
    val onIncoming: Color,
    val dark: Boolean,
)

val LocalChatColors = staticCompositionLocalOf {
    ChatColors(
        background = Color.White,
        outgoing = OutgoingBlue,
        onOutgoing = Color.White,
        incoming = Color(0xFFEBECEF),
        onIncoming = Color(0xFF1B1B1F),
        dark = false,
    )
}

/** Whether the app is drawn dark, from the theme setting or the system. */
@Composable
fun isAppInDarkTheme(appearance: Appearance): Boolean = when (appearance.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AppTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(appearance)
    val dynamic = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val chat = when {
        dynamic -> ChatColors(
            background = colors.background,
            outgoing = colors.primary,
            onOutgoing = colors.onPrimary,
            incoming = colors.surfaceContainerHigh,
            onIncoming = colors.onSurface,
            dark = dark,
        )
        dark -> ChatColors(
            background = DarkColors.background,
            outgoing = OutgoingBlue,
            onOutgoing = Color.White,
            incoming = Color(0xFF2B2C30),
            onIncoming = Color(0xFFE4E4E7),
            dark = true,
        )
        else -> ChatColors(
            background = LightColors.background,
            outgoing = OutgoingBlue,
            onOutgoing = Color.White,
            incoming = Color(0xFFEBECEF),
            onIncoming = Color(0xFF1B1B1F),
            dark = false,
        )
    }
    CompositionLocalProvider(LocalChatColors provides chat) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** A letter avatar's colors: a soft tint behind a strong letter of the same hue. */
@Immutable
data class AvatarColors(val container: Color, val content: Color)

private val AvatarPalette = listOf(
    AvatarColors(Color(0xFFDCE5FD), Color(0xFF1F4FD1)), // blue
    AvatarColors(Color(0xFFD6ECF6), Color(0xFF0B6A96)), // sky
    AvatarColors(Color(0xFFCFEDE6), Color(0xFF0B6F5C)), // teal
    AvatarColors(Color(0xFFD9EDD3), Color(0xFF2E7A1E)), // green
    AvatarColors(Color(0xFFECEBCB), Color(0xFF6B6512)), // olive
    AvatarColors(Color(0xFFFCEBC8), Color(0xFF8A5A00)), // amber
    AvatarColors(Color(0xFFFDDFD0), Color(0xFFB24A12)), // orange
    AvatarColors(Color(0xFFFBD9DA), Color(0xFFB3261E)), // red
    AvatarColors(Color(0xFFF9D9EA), Color(0xFFA8216E)), // pink
    AvatarColors(Color(0xFFEBDDFB), Color(0xFF6D3CC4)), // purple
    AvatarColors(Color(0xFFE0E0F8), Color(0xFF4A4AB8)), // indigo
    AvatarColors(Color(0xFFDEE1E6), Color(0xFF4B5563)), // slate
)

/** The same person or group always gets the same colors. */
fun avatarColors(key: String): AvatarColors = AvatarPalette[key.hashCode().mod(AvatarPalette.size)]

/** A group member's name color on an incoming bubble, readable in both themes. */
@Composable
fun senderNameColor(key: String): Color {
    val colors = avatarColors(key)
    return if (LocalChatColors.current.dark) colors.container else colors.content
}
