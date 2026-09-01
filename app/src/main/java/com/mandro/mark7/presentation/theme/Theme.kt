package com.mandro.mark7.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Mark7 컨트롤러는 기존 만드로 앱들과 뚜렷이 다른 톤을 목표로 한다: 계측 장비
 * 느낌의 차분한 그래파이트 + 청록 액센트, 상태색(온도/전류 경고)은 의미 기반.
 * (구체 UI 방향은 화면 작업 때 확정 — 여기서는 토큰만.)
 */
object Mark7Palette {
    val Accent = Color(0xFF17B0A7)      // 청록
    val AccentDim = Color(0xFF0E6F6A)
    val AccentSoft = Color(0xFFDDF4F2)

    val Ink = Color(0xFF11161B)
    val InkMuted = Color(0xFF5B6670)
    val Line = Color(0xFFD5DBDF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF3F5F6)
    val Bg = Color(0xFFEDEFF0)

    val Warn = Color(0xFFE8973C)        // 온도 주의
    val Danger = Color(0xFFDB4B4B)      // 과열 / 과전류
    val Ok = Color(0xFF2FA36B)

    // 모터 6개 색 (모니터링 그래프용)
    val motorColors = listOf(
        Color(0xFFE0533B), Color(0xFFE08B2E), Color(0xFF3FA34D),
        Color(0xFF2E9BD6), Color(0xFF5A5FD0), Color(0xFFB255C8),
    )
}

private val LightScheme = lightColorScheme(
    primary = Mark7Palette.Accent,
    onPrimary = Color.White,
    primaryContainer = Mark7Palette.AccentSoft,
    onPrimaryContainer = Mark7Palette.AccentDim,
    background = Mark7Palette.Bg,
    onBackground = Mark7Palette.Ink,
    surface = Mark7Palette.Surface,
    onSurface = Mark7Palette.Ink,
    surfaceVariant = Mark7Palette.SurfaceAlt,
    onSurfaceVariant = Mark7Palette.InkMuted,
    error = Mark7Palette.Danger,
    outline = Mark7Palette.Line,
)

val Mark7Typography = Typography(
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

@Composable
fun Mark7Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Mark7Typography,
        content = content,
    )
}
