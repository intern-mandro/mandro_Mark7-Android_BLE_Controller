package com.mandro.mark7

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mandro.mark7.core.locale.AppLocale
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.presentation.navigation.BOTTOM_NAV_ITEMS
import com.mandro.mark7.presentation.navigation.MAIN_TAB_COUNT
import com.mandro.mark7.presentation.navigation.PAGE_MANUAL
import com.mandro.mark7.presentation.navigation.PAGE_MODE
import com.mandro.mark7.presentation.navigation.PAGE_MONITOR
import com.mandro.mark7.presentation.navigation.PAGE_SETTINGS
import com.mandro.mark7.presentation.navigation.Screen
import kotlinx.coroutines.launch
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme
import com.mandro.mark7.presentation.ui.action.GesturePickerScreen
import com.mandro.mark7.presentation.ui.action.ModeFlowScreen
import com.mandro.mark7.presentation.ui.manual.ManualScreen
import com.mandro.mark7.presentation.ui.monitor.MonitorScreen
import com.mandro.mark7.presentation.ui.scan.ScanScreen
import com.mandro.mark7.presentation.ui.settings.SettingsScreen
import com.mandro.mark7.presentation.ui.dof.DofSetupScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: android.content.res.Configuration?) {
        if (overrideConfiguration != null) {
            val tag = AppLocale.tag(this)
            val locale = java.util.Locale.forLanguageTag(tag)
            overrideConfiguration.setLocale(locale)
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLocale.tag(this)
        setContent {
            val currentTag by AppLocale.currentTag.collectAsStateWithLifecycle()
            val configuration = LocalConfiguration.current
            val localizedConfig = remember(currentTag, configuration) {
                android.content.res.Configuration(configuration).apply {
                    setLocale(java.util.Locale.forLanguageTag(currentTag))
                }
            }
            CompositionLocalProvider(LocalConfiguration provides localizedConfig) {
                Mark7Theme {
                    val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                val mainViewModel: MainViewModel = hiltViewModel()
                val bleState by mainViewModel.bleState.collectAsStateWithLifecycle()

                val scope = rememberCoroutineScope()
                val pagerState = rememberPagerState(pageCount = { MAIN_TAB_COUNT })
                // 각 페이지(탭)의 리셋 토큰. 같은 탭을 다시 누르면 값을 올려 그 화면을
                // 처음 상태(토글 닫힘·스크롤 최상단)로 재구성한다.
                val resetKeys = remember { mutableStateListOf(0, 0, 0, 0) }

                val isMain = currentRoute == Screen.Main.route
                val isPicker = currentRoute == Screen.GesturePicker.route
                val showChrome = isMain
                val pickerStateId = backStackEntry?.arguments?.getString("state")?.toIntOrNull() ?: 0
                val onPickerBack: () -> Unit = {
                    navController.popBackStack(Screen.Main.route, inclusive = false)
                }
                // 상단바 ← 화살표도 시스템 뒤로가기와 같은 경로로 → GesturePickerScreen 의
                // BackHandler(미저장 손 모양 확인)가 함께 가로챈다.
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

                fun goToPage(index: Int, animate: Boolean = true) {
                    scope.launch {
                        if (animate) pagerState.animateScrollToPage(index) else pagerState.scrollToPage(index)
                    }
                }

                val context = LocalContext.current
                var hasBeenConnected by remember { mutableStateOf(false) }

                LaunchedEffect(bleState) {
                    if (bleState is BleState.Connected) {
                        hasBeenConnected = true
                    }
                }

                fun handleDisconnect() {
                    if (hasBeenConnected) {
                        hasBeenConnected = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.conn_lost_warning),
                            Toast.LENGTH_LONG,
                        ).show()
                        navController.navigate(Screen.Scan.route) {
                            popUpTo(Screen.Scan.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                // 연결 해제 시: 메인 탭이나 손모양 피커에 진입해 있었다면 경고 토스트와 함께 연결(스캔) 화면으로 복귀
                LaunchedEffect(bleState, isMain, isPicker) {
                    if (hasBeenConnected && (isMain || isPicker) && bleState is BleState.Disconnected) {
                        handleDisconnect()
                    }
                }

                // 메인 페이저에서 Monitor(0페이지)가 아니면 안드로이드 뒤로가기 → Monitor 페이지로.
                BackHandler(enabled = isMain && pagerState.currentPage != PAGE_MONITOR) {
                    goToPage(PAGE_MONITOR)
                }

                // Monitor 페이지에서 뒤로가기 시: 경고 토스트 없이 즉시 연결을 끊고 스캔 탐색 상태로 복귀
                BackHandler(enabled = isMain && pagerState.currentPage == PAGE_MONITOR) {
                    hasBeenConnected = false
                    mainViewModel.disconnectAndRescan()
                    navController.popBackStack(Screen.Scan.route, inclusive = false)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        // 상단바 슬롯은 메인 탭과 손모양 피커에서 모두 유지한다. 두 헤더
                        // (ConnectionHeader ↔ PickerTopBar)의 높이가 같아 전환 시 Scaffold
                        // 인셋이 바뀌지 않으므로 "제목이 헤더 밑에 잠깐 있다 위로 튀는" 현상이 없다.
                        AnimatedVisibility(
                            visible = showChrome || isPicker,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                        ) {
                            if (isPicker) {
                                PickerTopBar(
                                    stateId = pickerStateId,
                                    onBack = { backDispatcher?.onBackPressed() ?: onPickerBack() },
                                )
                            } else {
                                ConnectionHeader(
                                    onScanClick = {
                                        hasBeenConnected = false
                                        mainViewModel.disconnectAndRescan()
                                        navController.navigate(Screen.Scan.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            }
                        }
                    },
                    bottomBar = {
                        // 피커에서도 하단바를 그대로 유지 → Action ↔ Picker 전환 시 Scaffold
                        // 하단 인셋이 안 바뀌므로 사진 그리드가 줄었다 커지는 튐이 없다.
                        AnimatedVisibility(
                            visible = showChrome || isPicker,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) {
                            Surface(
                                color = Mark7Palette.Surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding(),
                                ) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Mark7Palette.Line))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(58.dp)
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        BOTTOM_NAV_ITEMS.forEachIndexed { index, item ->
                                            // 피커는 모드(1페이지) 하위 화면이므로 그동안 "모드" 탭을 활성 표시.
                                            val selected = (isMain && pagerState.currentPage == index) ||
                                                (isPicker && index == PAGE_MODE)
                                            val tint = if (selected) Mark7Palette.Accent else Mark7Palette.InkMuted

                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        when {
                                                            // 액션 고르는 화면(피커)에서 하단 탭을 누르면 피커를 먼저 닫는다.
                                                            // → 나중에 모드 탭으로 돌아와도 피커가 아니라 액션 플로우 첫 화면이 보인다.
                                                            isPicker -> {
                                                                navController.popBackStack(Screen.Main.route, inclusive = false)
                                                                goToPage(index, animate = false)
                                                            }
                                                            // 같은 탭 재탭 → 그 페이지를 처음 상태로 리셋
                                                            isMain && pagerState.currentPage == index -> {
                                                                resetKeys[index] = resetKeys[index] + 1
                                                            }
                                                            // 다른 탭 → 옆으로 스르륵 이동
                                                            else -> goToPage(index)
                                                        }
                                                    },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Icon(
                                                    imageVector = when (item.icon) {
                                                        "monitor" -> Icons.Filled.MonitorHeart
                                                        "gesture" -> Icons.Filled.Gesture
                                                        "state" -> Icons.Filled.Visibility
                                                        "tune" -> Icons.Filled.Tune
                                                        else -> Icons.Filled.Settings
                                                    },
                                                    contentDescription = stringResource(item.labelRes),
                                                    tint = tint,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    text = stringResource(item.labelRes),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                    color = tint,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.DofSetup.route,
                        modifier = Modifier.padding(padding),
                    ) {
                        composable(Screen.DofSetup.route) {
                            DofSetupScreen(
                                onDone = {
                                    // DofSetup 을 스택에 남긴다 → 연결 화면에서 뒤로가기 시
                                    // 다시 의수 버전 선택 화면으로 돌아온다.
                                    navController.navigate(Screen.Scan.route) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(Screen.Scan.route) {
                            ScanScreen(
                                onConnected = {
                                    // Scan 을 스택에 남긴다 → Monitor 에서 뒤로가기 시 이 연결 창으로 복귀.
                                    goToPage(PAGE_MONITOR, animate = false)
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.Scan.route) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(Screen.Main.route) {
                            // 하단 탭 4개 = HorizontalPager 페이지. 옆으로 밀어서 탭 전환.
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                key = { it },
                            ) { page ->
                                when (page) {
                                    PAGE_MONITOR -> key(resetKeys[PAGE_MONITOR]) {
                                        MonitorScreen(
                                            onDisconnected = ::handleDisconnect,
                                        )
                                    }
                                    PAGE_MODE -> key(resetKeys[PAGE_MODE]) {
                                        ModeFlowScreen(
                                            onPickGesture = { stateId ->
                                                navController.navigate(Screen.GesturePicker.createRoute(stateId))
                                            },
                                        )
                                    }
                                    PAGE_MANUAL -> key(resetKeys[PAGE_MANUAL]) { ManualScreen() }
                                    else -> key(resetKeys[PAGE_SETTINGS]) { SettingsScreen() }
                                }
                            }
                        }
                        composable(
                            route = Screen.GesturePicker.route,
                            arguments = listOf(navArgument("state") { type = NavType.StringType }),
                        ) {
                            // onPickerBack: 항상 모드 페이지로만 되돌아간다. 상단 제목/뒤로가기는
                            // Scaffold 의 PickerTopBar 가 담당한다.
                            GesturePickerScreen(onDone = onPickerBack)
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * 손모양 피커 전용 상단바. ConnectionHeader 와 같은 구조/높이(흰색 Surface + 상태바 패딩 +
 * Row + 1dp 구분선 + 6dp)라서 두 화면 사이를 오갈 때 Scaffold 상단 인셋이 변하지 않는다.
 */
@Composable
private fun PickerTopBar(stateId: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Surface(
            color = Mark7Palette.Surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Mark7Palette.Ink,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onBack)
                            .padding(4.dp)
                            .size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.picker_title, stateId),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Mark7Palette.Ink,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Mark7Palette.Line))
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * 상단 바 블루투스 액션 버튼 (미니멀 클린 스타일).
 * 불필요한 상태 점이나 뱃지 없이, 정갈하고 세련된 단일 블루투스 아이콘 버튼.
 * 탭 시 기기 검색/연결 화면으로 이동할 수 있다.
 */
@Composable
private fun ConnectionIconButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Mark7Palette.SurfaceAlt)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = stringResource(R.string.conn_btn_scan),
            tint = Mark7Palette.Accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ConnectionHeader(
    onScanClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Surface(
            color = Mark7Palette.Surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Mark7Palette.Ink,
                    )

                    ConnectionIconButton(
                        onClick = onScanClick,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Mark7Palette.Line))
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
