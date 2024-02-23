package net.mullvad.mullvadvpn.lib.theme.color

import androidx.compose.ui.graphics.Color

internal object ColorLightTokens {
    val Background = PaletteTokens.DarkBlue100
    val Error = PaletteTokens.Red600
    val ErrorContainer = PaletteTokens.Red100
    val InverseOnSurface = PaletteTokens.DarkBlue100
    val InversePrimary = PaletteTokens.Blue200
    val InverseSurface = PaletteTokens.DarkBlue800
    val OnBackground = PaletteTokens.DarkBlue900
    val OnError = PaletteTokens.Red900
    val OnErrorContainer = PaletteTokens.Red900
    val OnPrimary = PaletteTokens.Blue900
    val OnPrimaryContainer = PaletteTokens.Blue900
    val OnPrimaryFixed = PaletteTokens.Blue900
    val OnPrimaryFixedVariant = PaletteTokens.Blue700
    val OnSecondary = PaletteTokens.Green900
    val OnSecondaryContainer = PaletteTokens.Green900
    val OnSecondaryFixed = PaletteTokens.Green900
    val OnSecondaryFixedVariant = PaletteTokens.Green700
    val OnSurface = PaletteTokens.DarkBlue900
    val OnSurfaceVariant = PaletteTokens.DarkBlue700
    val OnTertiary = PaletteTokens.Yellow900
    val OnTertiaryContainer = PaletteTokens.Yellow900
    val OnTertiaryFixed = PaletteTokens.Yellow900
    val OnTertiaryFixedVariant = PaletteTokens.Yellow700
    val Outline = PaletteTokens.DarkBlue500
    val OutlineVariant = PaletteTokens.DarkBlue200
    val Primary = PaletteTokens.Blue600
    val PrimaryContainer = PaletteTokens.Blue100
    val PrimaryFixed = PaletteTokens.Blue100
    val PrimaryFixedDim = PaletteTokens.Blue200
    val Scrim = PaletteTokens.DarkBlue900
    val Secondary = PaletteTokens.Green600
    val SecondaryContainer = PaletteTokens.Green100
    val SecondaryFixed = PaletteTokens.Green100
    val SecondaryFixedDim = PaletteTokens.Green200
    val Surface = PaletteTokens.DarkBlue100
    val SurfaceBright = PaletteTokens.DarkBlue100
    val SurfaceContainer = PaletteTokens.DarkBlue900
    val SurfaceContainerHigh = PaletteTokens.DarkBlue100
    val SurfaceContainerHighest = PaletteTokens.DarkBlue100
    val SurfaceContainerLow = PaletteTokens.DarkBlue100
    val SurfaceContainerLowest = PaletteTokens.DarkBlue100
    val SurfaceDim = PaletteTokens.DarkBlue200
    val SurfaceTint = Primary
    val SurfaceVariant = PaletteTokens.DarkBlue100
    val Tertiary = PaletteTokens.Yellow600
    val TertiaryContainer = PaletteTokens.Yellow100
    val TertiaryFixed = PaletteTokens.Yellow100
    val TertiaryFixedDim = PaletteTokens.Yellow200
}

internal object ColorDarkTokens {
    val Background = PaletteTokens.DarkBlue500
    val Error = PaletteTokens.Red500
    val ErrorContainer = PaletteTokens.Red500 // Duplicate
    val InverseOnSurface = PaletteTokens.White
    val InversePrimary = PaletteTokens.Green500
    val InverseSurface = PaletteTokens.White
    val OnBackground = PaletteTokens.White
    val OnError = PaletteTokens.White
    val OnErrorContainer = Color(0xFFFFDAD6) // Replace
    val OnPrimary = PaletteTokens.White
    val OnPrimaryContainer = PaletteTokens.White
    val OnPrimaryFixed = PaletteTokens.Blue50 // Approximated
    val OnPrimaryFixedVariant = PaletteTokens.Blue50 // Approximated
    val OnSecondary = PaletteTokens.White
    val OnSecondaryContainer = Color(0xFF002204) // Replace
    val OnSecondaryFixed = PaletteTokens.Green50 // Approximated
    val OnSecondaryFixedVariant = PaletteTokens.Green50 // Approximated
    val OnSurface = PaletteTokens.White
    val OnSurfaceVariant = PaletteTokens.White
    val OnTertiary = PaletteTokens.White
    val OnTertiaryContainer =
        Color(0xffacb4bc) // MullvadWhite Alpha 60 composite over tertiary container
    val OnTertiaryFixed = PaletteTokens.Yellow50 // Approximated
    val OnTertiaryFixedVariant = PaletteTokens.Yellow50 // Approximated
    val Outline = Color(0xFF8D9199) // Replace
    val OutlineVariant = Color(0xFF43474E) // Replace
    val Primary = PaletteTokens.Blue500
    val PrimaryContainer = Color(0xFF1C344E) // Sub-container
    val PrimaryFixed = PaletteTokens.Blue100 // Approximated
    val PrimaryFixedDim = PaletteTokens.Blue100 // Approximated
    val Scrim = PaletteTokens.White
    val Secondary = PaletteTokens.Green500
    val SecondaryContainer = PaletteTokens.Green500 // Duplicate
    val SecondaryFixed = PaletteTokens.Green100 // Approximated
    val SecondaryFixedDim = PaletteTokens.Green100 // Approximated
    val Surface = PaletteTokens.DarkBlue500
    val SurfaceBright = PaletteTokens.DarkBlue700 // Approximated
    val SurfaceContainer = PaletteTokens.DarkBlue100 // Approximated
    val SurfaceContainerHigh = PaletteTokens.DarkBlue200 // Approximated
    val SurfaceContainerHighest = PaletteTokens.DarkBlue300 // Approximated
    val SurfaceContainerLow = PaletteTokens.DarkBlue50 // Approximated
    val SurfaceContainerLowest = PaletteTokens.Black // Approximated
    val SurfaceDim = PaletteTokens.Black // Approximated
    val SurfaceTint = Primary
    val SurfaceVariant = Color(0xFFA3ACB5) // Subtext
    val Tertiary = Color(0xFF99454F) // Disconnect button
    val TertiaryContainer =
        Color(0xff304358) // MullvadWhite Alpha 10 composite over MullvadDarkBlue
    val TertiaryFixed = PaletteTokens.Yellow600 // Approximated
    val TertiaryFixedDim = PaletteTokens.Yellow500 // Approximated
}
