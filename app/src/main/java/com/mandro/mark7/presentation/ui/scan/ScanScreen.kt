package com.mandro.mark7.presentation.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.BuildConfig
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.connection.BleDevice
import com.mandro.mark7.domain.model.connection.BleState
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme

private val BLE_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

@Composable
fun ScanScreen(
    onConnected: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    var hasBlePermission by remember {
        mutableStateOf(
            BLE_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    // mock 모드는 BLE 를 쓰지 않으므로 권한 없이도 탐색·연결 UI 를 연다.
    val hasPermission = hasBlePermission || ui.mockMode

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasBlePermission = results.values.all { it }
        if (hasBlePermission) viewModel.rescan()
    }

    // 화면으로 되돌아오면 즉시 기존 연결을 끊고 다시 BLE 탐색을 시작한다 (ON_START 시점에 즉각 발동).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.disconnectAndRescan()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 사용자가 기기의 '연결(Connect)' 버튼을 누르면 연결 완료 시 즉시 다음 화면(Monitor)으로 자동 전환한다.
    var connectRequested by remember { mutableStateOf(false) }
    var sawConnecting by remember { mutableStateOf(false) }

    LaunchedEffect(ui.bleState) {
        when (val s = ui.bleState) {
            is BleState.Connecting -> sawConnecting = true
            is BleState.Error, BleState.Disconnected -> {
                // 연결 시도(요청했거나 Connecting 을 봤음)가 실패로 끝났으면 잠깐 안내한다.
                if ((connectRequested || sawConnecting) && !ui.connected) {
                    val msg = (s as? BleState.Error)?.message
                        ?: context.getString(R.string.scan_connect_failed)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                sawConnecting = false
                connectRequested = false
            }
            else -> Unit
        }
    }

    // 연결되면 STATUS 를 기다리지 않고 바로 넘어간다. STATUS 가 오기 전(프로토콜 불일치로 안 오는 경우 포함)에는
    // 모니터 화면이 "의수 상태값을 기다리는 중" 로딩을 띄우고, STATUS 가 오면 값 표시로 바뀐다.
    // DOF 불일치 안내는 메인 화면(MainActivity)이 맡는다.
    LaunchedEffect(ui.connected, connectRequested, sawConnecting) {
        if (ui.connected && (connectRequested || sawConnecting)) {
            connectRequested = false
            sawConnecting = false
            onConnected()
        }
    }

    // 명시적으로 연결을 요청했을 때만 연결 완료 표식을 띄우고, 되돌아왔을 때는 1초의 잔상도 없이 즉시 탐색 UI가 뜨도록 함
    val isEffectivelyConnected = ui.connected && (connectRequested || sawConnecting)

    ScanContent(
        ui = ui,
        isConnected = isEffectivelyConnected,
        hasPermission = hasPermission,
        onRequestPermission = { launcher.launch(BLE_PERMISSIONS) },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
        },
        onRescan = viewModel::rescan,
        onConnect = { device ->
            connectRequested = true
            viewModel.connect(device)
        },
        showMockToggle = BuildConfig.DEBUG,
        onMockModeChange = viewModel::setMockMode,
    )
}

@Composable
private fun ScanContent(
    ui: ScanUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    isConnected: Boolean = ui.connected,
    showMockToggle: Boolean = false,
    onMockModeChange: (Boolean) -> Unit = {},
) {
    val state = ui.bleState
    val isConnecting = state is BleState.Connecting
    val connectingAddress = (state as? BleState.Connecting)?.device?.address
    val errorMessage = (state as? BleState.Error)?.message

    // 연결 전에는 주기적으로 재스캔 → 새 기기/신호세기가 계속 갱신돼 "찾고 있는" 느낌.
    LaunchedEffect(hasPermission, isConnected) {
        if (!hasPermission || isConnected) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000)
            onRescan()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Mark7Palette.Bg)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── 타이틀 + 개발 모드 전환 + 상태 문구 ──
        item {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.scan_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
                if (showMockToggle) {
                    MockModeToggle(enabled = ui.mockMode, onChange = onMockModeChange)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    when {
                        isConnected -> R.string.scan_sub_connected
                        isConnecting -> R.string.scan_sub_connecting
                        ui.devices.isNotEmpty() -> R.string.scan_sub_found
                        else -> R.string.scan_sub_scanning
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Mark7Palette.InkMuted,
            )
        }

        // ── 펄스 애니메이션 ──
        item {
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center,
            ) {
                PulsingCircle(
                    isConnecting = isConnecting,
                    isConnected = isConnected,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── 권한 안내 ──
        if (!hasPermission) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Mark7Palette.Surface,
                    border = BorderStroke(1.dp, Mark7Palette.Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.perm_ble_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Mark7Palette.Ink,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.perm_ble_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = Mark7Palette.InkMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRequestPermission, shape = RoundedCornerShape(10.dp)) {
                                Text(stringResource(R.string.perm_ble_grant))
                            }
                            OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(10.dp)) {
                                Text(stringResource(R.string.perm_ble_settings))
                            }
                        }
                    }
                }
            }
        }

        // ── 오류 ──
        if (errorMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Mark7Palette.Danger.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = errorMessage,
                        color = Mark7Palette.Danger,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        // ── 기기 목록 ──
        if (ui.devices.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.scan_found_nearby),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                )
                Spacer(Modifier.height(4.dp))
            }
            items(ui.devices, key = { it.address }) { device ->
                DeviceCard(
                    device = device,
                    isConnecting = connectingAddress == device.address,
                    onConnect = { onConnect(device) },
                )
            }
        } else if (hasPermission && errorMessage == null && !isConnected) {
            item {
                Text(
                    text = stringResource(R.string.scan_no_devices_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mark7Palette.InkMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }

        // ── 다시 찾기 ──
        item {
            Spacer(Modifier.height(8.dp))
            val retryEnabled = hasPermission && !isConnected
            TextButton(
                onClick = onRescan,
                enabled = retryEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.scan_retry) + "  ↺",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (retryEnabled) Mark7Palette.Accent else Mark7Palette.InkMuted,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 중앙 펄스. 연결되기 전에는 계속 "찾고 있는" 느낌이 나도록:
 *  - 파동(3겹)이 쉼 없이 퍼져나가고
 *  - 작은 위성 점 하나가 중앙 원 주위를 계속 돈다.
 * 연결되면 초록 정지 상태. mandro-dynamic-gesture BleScreen 참고.
 */
@Composable
private fun PulsingCircle(
    isConnecting: Boolean,
    isConnected: Boolean,
) {
    val searching = !isConnected
    val transition = rememberInfiniteTransition(label = "pulse")
    val delays = listOf(0, 320, 640)

    val scales = delays.map { d ->
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, delayMillis = d, easing = EaseOut),
                repeatMode = RepeatMode.Restart,
            ),
            label = "scale$d",
        )
    }
    val alphas = delays.map { d ->
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, delayMillis = d, easing = EaseOut),
                repeatMode = RepeatMode.Restart,
            ),
            label = "alpha$d",
        )
    }

    Box(contentAlignment = Alignment.Center) {
        if (searching) {
            scales.forEachIndexed { i, s ->
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(s.value)
                        .background(Mark7Palette.Accent.copy(alpha = alphas[i].value), CircleShape),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    color = if (isConnected) Mark7Palette.Ok.copy(alpha = 0.14f) else Mark7Palette.AccentSoft,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    when {
                        isConnected -> R.string.scan_pulse_connected
                        isConnecting -> R.string.scan_connecting
                        else -> R.string.scan_pulse_scanning
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isConnected) Mark7Palette.Ok else Mark7Palette.Accent,
            )
        }
    }
}

/** 제목 오른쪽의 개발자 모드 진입 버튼. 사용자 모드 복귀에는 인증을 요구하지 않는다. */
@Composable
private fun MockModeToggle(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    var showAuthDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var invalidPin by remember { mutableStateOf(false) }

    TextButton(
        onClick = {
            if (enabled) {
                onChange(false)
            } else {
                pin = ""
                invalidPin = false
                showAuthDialog = true
            }
        },
        modifier = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(
            text = stringResource(if (enabled) R.string.scan_user_mode else R.string.scan_developer_mode),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Mark7Palette.AccentDim else Mark7Palette.InkMuted,
        )
    }

    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            title = { Text(stringResource(R.string.scan_developer_auth_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.scan_developer_auth_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value ->
                            pin = value.filter { it.isDigit() }.take(2)
                            invalidPin = false
                        },
                        label = { Text(stringResource(R.string.scan_developer_auth_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = invalidPin,
                        supportingText = if (invalidPin) {
                            { Text(stringResource(R.string.scan_developer_auth_error)) }
                        } else null,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length == 2,
                    onClick = {
                        if (pin == DEVELOPER_MODE_PIN) {
                            showAuthDialog = false
                            onChange(true)
                        } else {
                            invalidPin = true
                            pin = ""
                        }
                    },
                ) {
                    Text(stringResource(R.string.scan_developer_auth_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private const val DEVELOPER_MODE_PIN = "77"

@Composable
private fun DeviceCard(
    device: BleDevice,
    isConnecting: Boolean,
    onConnect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(1.dp, Mark7Palette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Mark7Palette.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.address,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Mark7Palette.InkMuted,
                )
            }
            Button(
                onClick = onConnect,
                enabled = !isConnecting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mark7Palette.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = Mark7Palette.Line,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.scan_connect),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ScanPreviewScanning() {
    Mark7Theme {
        ScanContent(
            ui = ScanUiState(bleState = BleState.Scanning),
            hasPermission = true,
            onRequestPermission = {},
            onOpenSettings = {},
            onRescan = {},
            onConnect = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ScanPreviewDevices() {
    val devices = listOf(
        BleDevice("Mark7-Left", "3C:A5:51:99:BB:5F", -48),
        BleDevice("Mark7-Right", "3C:A5:51:99:BB:60", -78),
    )
    Mark7Theme {
        ScanContent(
            ui = ScanUiState(bleState = BleState.DevicesFound(devices), devices = devices),
            hasPermission = true,
            onRequestPermission = {},
            onOpenSettings = {},
            onRescan = {},
            onConnect = {},
        )
    }
}
