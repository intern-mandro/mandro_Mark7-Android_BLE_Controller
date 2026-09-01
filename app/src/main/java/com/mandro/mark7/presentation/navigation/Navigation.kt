package com.mandro.mark7.presentation.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Scan : Screen("scan")

    // 메인 바텀 탭
    data object Monitor : Screen("main/monitor")   // STATUS 프레임 모니터링
    data object Action : Screen("main/action")     // 액션(F/E/close/rest) → 패턴 매핑, 사진 선택
    data object Manual : Screen("main/manual")      // 직접 구동 (CMD 프레임)
    data object Settings : Screen("main/settings")  // SET 프레임 편집·전송

    // 상세
    data object PatternPicker : Screen("action/pattern/{action}") {
        fun createRoute(action: String) = "action/pattern/$action"
    }
}

data class BottomNavItem(val screen: Screen, val label: String, val icon: String)

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Screen.Monitor, "모니터", "monitor"),
    BottomNavItem(Screen.Action, "액션", "gesture"),
    BottomNavItem(Screen.Manual, "수동", "tune"),
    BottomNavItem(Screen.Settings, "설정", "settings"),
)

val BOTTOM_NAV_ROUTES = BOTTOM_NAV_ITEMS.map { it.screen.route }.toSet()
