package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF00295C),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF003548),
    tertiary = DarkIncomeGreen,
    background = DarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = DarkExpenseRed,
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue50,
    onPrimaryContainer = Navy900,
    secondary = Navy800,
    onSecondary = Color.White,
    tertiary = IncomeGreen,
    background = BackgroundLight,
    onBackground = NeutralDark,
    surface = SurfaceLight,
    onSurface = NeutralDark,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = NeutralMedium,
    error = ExpenseRed,
    onError = Color.White
)

@Composable
fun MoneyMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default for brand consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
