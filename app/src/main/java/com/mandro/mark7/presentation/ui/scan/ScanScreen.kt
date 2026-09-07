package com.mandro.mark7.presentation.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
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

    var hasPermission by remember {
        mutableStateOf(
            BLE_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasPermission = results.values.all { it }
        if (hasPermission) viewModel.rescan()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(BLE_PERMISSIONS)
    }

    // 이 화면에서 '새로' 연결됐을 때만 Monitor 로 자동 전환한다. Connecting 단계를 거친 경우만
    // 사용자가 여기서 연결을 시작한 것 → 자동 진입. 이미 연결된 채로 들어온 경우(Monitor 에서
    // 뒤로가기)는 Connecting 을 안 거치므로 이 화면에 머무르고, 아래 '계속' 버튼으로 진입한다.
    var sawConnecting by remember { mutableStateOf(false) }
    LaunchedEffect(ui.bleState) {
        if (ui.bleState is BleState.Connecting) sawConnecting = true
    }
    LaunchedEffect(ui.connected, sawConnecting) {
        if (ui.connected && sawConnecting) onConnected()
    }

    ScanContent(
        ui = ui,
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
        onConnect = viewModel::connect,
        onContinue = onConnected,
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
    onContinue: () -> Unit,
) {
    val state = ui.bleState
    val isConnecting = state is BleState.Connecting
    val connectingAddress = (state as? BleState.Connecting)?.device?.address
    val errorMessage = (state as? BleState.Error)?.message

    // 연결 전에는 주기적으로 재스캔 → 새 기기/신호세기가 계속 갱신돼 "찾고 있는" 느낌.
    LaunchedEffect(hasPermission, ui.connected) {
        if (!hasPermission || ui.connected) return@LaunchedEffect
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
        // ── 타이틀 + 상태 문구 ──
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Mark7Palette.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    when {
                        ui.connected -> R.string.scan_sub_connected
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
                    isConnected = ui.connected,
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

        // ── 연결된 상태로 들어온 경우: 앱으로 계속 진입 ──
        if (ui.connected) {
            item {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mark7Palette.Accent),
                ) {
                    Text(
                        text = stringResource(R.string.scan_continue),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
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
        } else if (hasPermission && errorMessage == null && !ui.connected) {
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
            val retryEnabled = hasPermission && !ui.connected
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
                    Text(stringResource(R.string.scan_connect), style = MaterialTheme.typography.labelLarge)
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
            onContinue = {},
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
            onContinue = {},
        )
    }
}
