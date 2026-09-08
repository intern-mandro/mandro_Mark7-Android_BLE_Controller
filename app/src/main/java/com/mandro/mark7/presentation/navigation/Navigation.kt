package com.mandro.mark7.presentation.navigation

import androidx.annotation.StringRes
import com.mandro.mark7.R

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object DofSetup : Screen("dof_setup")     // 첫 실행: 로봇 의수 버전(자유도 5/6/7) 선택
    data object UserSetup : Screen("dof_setup")   // 레거시 호환 라우트
    data object Scan : Screen("scan")

    // 메인 바텀 탭 4개를 담는 HorizontalPager 호스트 (옆으로 밀어서 탭 전환)
    data object Main : Screen("main")

    // 아래 4개는 BOTTOM_NAV_ITEMS 의 라벨/아이콘/순서 정의용 (더 이상 NavHost route 아님)
    data object Monitor : Screen("main/monitor")   // STATUS 프레임 모니터링
    data object Action : Screen("main/action")     // 상태 전이 다이어그램 편집 (모드 플로우)
    data object Manual : Screen("main/manual")      // 직접 구동 (CMD 프레임)
    data object Settings : Screen("main/settings")  // SET 프레임 편집·전송

    // 상세
    data object GesturePicker : Screen("action/gesture/{state}") {
        fun createRoute(stateId: Int) = "action/gesture/$stateId"
    }
}

data class BottomNavItem(val screen: Screen, @StringRes val labelRes: Int, val icon: String)

val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(Screen.Monitor, R.string.tab_monitor, "monitor"),
    BottomNavItem(Screen.Action, R.string.tab_mode, "gesture"),
    BottomNavItem(Screen.Manual, R.string.tab_manual, "tune"),
    BottomNavItem(Screen.Settings, R.string.tab_settings, "settings"),
)

/** 하단 탭이자 [Screen.Main] 페이저의 페이지 인덱스 순서. */
val MAIN_TAB_COUNT = BOTTOM_NAV_ITEMS.size
const val PAGE_MONITOR = 0
const val PAGE_MODE = 1
const val PAGE_MANUAL = 2
const val PAGE_SETTINGS = 3
