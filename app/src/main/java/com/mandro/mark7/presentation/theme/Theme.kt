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
 * 느낌의 차분한 그래파이트 + 진한 파랑 액센트 하나, 상태색(온도/전류 경고)은 의미 기반.
 * (구체 UI 방향은 화면 작업 때 확정 — 여기서는 토큰만.)
 */
object Mark7Palette {
    val Accent = Color(0xFF2B5CA8)      // 진한 파랑 (앱 유일 강조색)
    val AccentDim = Color(0xFF1D437E)
    val AccentSoft = Color(0xFFE4ECF7)

    val Ink = Color(0xFF11161B)
    val InkMuted = Color(0xFF5B6670)
    val Line = Color(0xFFD5DBDF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF3F5F6)
    val Bg = Color(0xFFEDEFF0)

    val Warn = Color(0xFFE8973C)        // 온도 주의
    val Danger = Color(0xFFDB4B4B)      // 과열 / 과전류
    val Ok = Color(0xFF2FA36B)

    // 동작 제어 (쥐기 / 펴기) 전용 고대비 의미 색상
    val Grasp = Color(0xFF1E56A0)         // 쥐기 (선명한 코발트 블루)
    val GraspSoft = Color(0xFFE8F1FC)     // 쥐기 연한 배경
    val GraspBorder = Color(0xFF3B82F6)   // 쥐기 활성 강조 테두리
    val Release = Color(0xFF0D824D)       // 펴기 (산뜻한 에메랄드 그린)
    val ReleaseSoft = Color(0xFFE6F5EC)   // 펴기 연한 배경
    val ReleaseBorder = Color(0xFF10B981) // 펴기 활성 강조 테두리

    // 모터 6개 트레이스 색 — 구분은 뚜렷하되 형광·청록 없는 팔레트.
    val motorColors = listOf(
        Color(0xFF3060A8), Color(0xFFDE8F05), Color(0xFF6E8B3D),
        Color(0xFFD55E00), Color(0xFFA25CA0), Color(0xFF8C6D4F),
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
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Mark7Palette.SurfaceAlt,
    surfaceContainer = Mark7Palette.Surface,
    surfaceContainerHigh = Mark7Palette.Surface,
    surfaceContainerHighest = Mark7Palette.SurfaceAlt,
    surfaceBright = Color.White,
    surfaceDim = Mark7Palette.SurfaceAlt,
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
