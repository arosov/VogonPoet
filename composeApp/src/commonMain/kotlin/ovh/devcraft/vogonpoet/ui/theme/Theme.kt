package ovh.devcraft.vogonpoet.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Gruvbox Dark Palette
val GruvboxBg0 = Color(0xFF282828)
val GruvboxBg1 = Color(0xFF3C3836)
val GruvboxFg0 = Color(0xFFFBF1C7)
val GruvboxFg1 = Color(0xFFEBDBB2)
val GruvboxFg2 = Color(0xFFD5C4A1)
val GruvboxRedDark = Color(0xFFCC241D)
val GruvboxRedLight = Color(0xFFFB4934)
val GruvboxGreenDark = Color(0xFF98971A)
val GruvboxGreen = Color(0xFF98971A)
val GruvboxGreenLight = Color(0xFFB8BB26)
val GruvboxBlueDark = Color(0xFF458588)
val GruvboxBlueLight = Color(0xFF83A598)
val GruvboxPurple = Color(0xFFD3869B)
val GruvboxYellowDark = Color(0xFFD79921)
val GruvboxYellowLight = Color(0xFFFABD2F)
val GruvboxGray = Color(0xFF928374)

val GruvboxDarkColorScheme =
    darkColorScheme(
        primary = GruvboxGreenLight,
        onPrimary = GruvboxBg0,
        secondary = GruvboxBlueLight,
        onSecondary = GruvboxBg0,
        background = GruvboxBg0,
        onBackground = GruvboxFg1,
        surface = GruvboxBg0,
        onSurface = GruvboxFg1,
        error = GruvboxRedLight,
        onError = GruvboxBg0,
    )
