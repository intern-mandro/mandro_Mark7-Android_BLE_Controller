package com.mandro.mark7.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.BleDevice
import com.mandro.mark7.domain.model.BleState
import com.mandro.mark7.domain.model.HandStatus
import com.mandro.mark7.domain.model.MARK7_NAME_PREFIX
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Mark7Ble"

/**
 * 시리얼 브리지 후보 프로파일. Mark7 의수의 BLE 모듈은 Chipsen 제품 — HM-10(FFE0/FFE1)이
 * 아니라 대개 FFF0(+FFF1 notify / FFF2 write) 를 쓴다. 아래 후보로 먼저 찾고, 없으면
 * [onServicesDiscovered] 가 특성 속성(NOTIFY / WRITE)으로 자동 탐지한다.
 */
private val CANDIDATE_SERVICE_UUIDS = listOf(
    UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"), // HM-10
    UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), // Chipsen / CC254x SPP
)
private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** HM-10 UART 버퍼 오버플로우 방지 및 기본 BLE MTU(23B) 안전 송신 청크 단위 */
private const val TX_CHUNK_SIZE = 12
private const val TX_CHUNK_INTERVAL_MS = 20L

/**
 * 재스캔 디바운스. 스캔 화면이 init + ON_START + 10초 루프 + 권한 콜백으로 startScan 을
 * 연달아 부르는데, Android 는 30초당 5회를 넘으면 스캔을 조용히 차단한다.
 */
private const val SCAN_DEBOUNCE_MS = 1_500L

/**
 * 의수 BLE 저수준 관리자. 스캔 / 연결 / 프레임 송수신만 한다.
 * 프로토콜 인코딩·디코딩은 [MarkSevenProtocol], 상위 조립은 HandRepositoryImpl.
 */
@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state = _state.asStateFlow()

    private val _status = MutableSharedFlow<HandStatus>(extraBufferCapacity = 32)
    val status = _status.asSharedFlow()

    private val reassembler = FrameReassembler(
        onResync = { dropped -> Log.w(TAG, "RX resync — dropped $dropped stray byte(s) to realign STATUS stream") },
    )
    private val found = linkedMapOf<String, BleDevice>()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var connectingDevice: BleDevice? = null

    /** 마지막으로 실제 스캔을 시작한 시각 (elapsedRealtime). 디바운스용. */
    private var lastScanStartMs = 0L

    /**
     * true 면 이름이 `CHIPSEN…`/`mark…` 이거나 `0xFFE0` 서비스를 광고하는 기기만 목록에 올린다.
     * (Mark7 의수의 BLE 모듈은 Chipsen 제품 — "CHIPSEN" 으로 광고)
     * 디버그로 주변 모든 BLE 기기를 보고 싶으면 false 로.
     */
    var filterHandsOnly: Boolean = true

    /** 마지막으로 보낸 SET 의 "SETok" 응답을 기다리는 쪽에 신호. */
    @Volatile
    private var pendingAck: CompletableDeferred<Unit>? = null

    // ── 권한 ──────────────────────────────────────────────────
    fun hasScan(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            granted(Manifest.permission.BLUETOOTH_SCAN)
        else granted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasConnect(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            granted(Manifest.permission.BLUETOOTH_CONNECT)
        else true

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    // ── 스캔 ──────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        Log.d(TAG, "startScan: scanner=${scanner != null} hasScan=${hasScan()} btEnabled=${adapter?.isEnabled}")
        if (scanner == null || !hasScan()) {
            _state.value = BleState.Error(context.getString(R.string.ble_err_permission_scan))
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (_state.value is BleState.Scanning && now - lastScanStartMs < SCAN_DEBOUNCE_MS) {
            Log.d(TAG, "startScan ignored — debounced (${now - lastScanStartMs}ms since last)")
            return
        }
        lastScanStartMs = now
        // 이전 스캔이 아직 등록돼 있으면 먼저 해제한다.
        // (init + ON_START + 10초 재스캔 루프가 startScan 을 연달아 호출 → SCAN_FAILED_ALREADY_STARTED=1)
        runCatching { scanner.stopScan(scanCallback) }
        found.clear()
        _state.value = BleState.Scanning
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching { scanner.startScan(null, settings, scanCallback) }
            .onFailure { _state.value = BleState.Error(context.getString(R.string.ble_err_scan_failed, it.message ?: "")) }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasScan()) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val recordName = result.scanRecord?.deviceName
            val devName = result.device.name
            val name = recordName ?: devName

            val hasHandService = result.scanRecord?.serviceUuids?.any { parcelUuid ->
                CANDIDATE_SERVICE_UUIDS.any { parcelUuid.uuid == it }
            } == true

            val isKnownPrefix = name?.lowercase()?.let { lower ->
                lower.startsWith(MARK7_NAME_PREFIX) || lower.startsWith("mark")
            } == true

            val filteredOut = filterHandsOnly && !hasHandService && !isKnownPrefix
            Log.d(
                TAG,
                "scanResult name=$name addr=${result.device.address} rssi=${result.rssi} " +
                    "handSvc=$hasHandService knownPrefix=$isKnownPrefix → ${if (filteredOut) "FILTERED" else "KEPT"}",
            )
            if (filteredOut) return

            val displayName = name ?: "Mark7 Device (${result.device.address.takeLast(5)})"
            found[result.device.address] = BleDevice(displayName, result.device.address, result.rssi)
            _state.value = BleState.DevicesFound(found.values.toList())
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "onScanFailed errorCode=$errorCode")
            _state.value = BleState.Error(context.getString(R.string.ble_err_scan_code, errorCode))
        }
    }

    // ── 연결 ──────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        if (!hasConnect()) {
            _state.value = BleState.Error(context.getString(R.string.ble_err_permission_connect))
            return
        }
        stopScan()
        disconnect()
        connectingDevice = device
        _state.value = BleState.Connecting(device)
        val btDevice = adapter?.getRemoteDevice(device.address) ?: return
        gatt = btDevice.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        writeChar = null
        notifyChar = null
        reassembler.reset()
        pendingAck?.cancel()
        pendingAck = null
        if (_state.value is BleState.Connected || _state.value is BleState.Connecting) {
            _state.value = BleState.Disconnected
        }
    }

    // ── 송신 (MTU 안전 청킹 분할 전송) ─────────────────────────────
    @SuppressLint("MissingPermission")
    suspend fun writeFrame(bytes: ByteArray): Boolean {
        val g = gatt ?: run { Log.w(TAG, "writeFrame: no gatt"); return false }
        val ch = writeChar ?: run { Log.w(TAG, "writeFrame: no writeChar"); return false }
        val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        Log.d(
            TAG,
            "TX >> ${bytes.joinToString(" ") { "%02X".format(it) }} " +
                "via ${ch.uuid} type=${if (type == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) "NO_RESP" else "DEFAULT"}",
        )

        if (bytes.size <= TX_CHUNK_SIZE) {
            return writeChunk(g, ch, bytes, type)
        }

        for (offset in bytes.indices step TX_CHUNK_SIZE) {
            val end = (offset + TX_CHUNK_SIZE).coerceAtMost(bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val ok = writeChunk(g, ch, chunk, type)
            if (!ok) return false
            delay(TX_CHUNK_INTERVAL_MS)
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun writeChunk(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        chunk: ByteArray,
        type: Int,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, chunk, type) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.value = chunk
            @Suppress("DEPRECATION")
            ch.writeType = type
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    /** SET 전송 뒤 이 deferred 를 await 하면 "SETok" 수신 시 완료된다. */
    fun awaitAck(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { pendingAck = it }

    // ── GATT 콜백 ─────────────────────────────────────────────
    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> disconnect()
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(TAG, "onCharacteristicWrite ${ch.uuid} status=$status (0=OK)")
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            d: BluetoothGattDescriptor,
            status: Int,
        ) {
            Log.d(TAG, "onDescriptorWrite ${d.uuid} status=$status (0=OK) — notify ${if (status == 0) "ENABLED" else "FAILED"}")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = BleState.Error(context.getString(R.string.ble_err_service_failed, status))
                return
            }

            // ── 발견된 GATT 전체 덤프 (모듈 프로파일이 뭐든 logcat 으로 확인) ──
            val writeMask = BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            for (svc in g.services) {
                Log.d(TAG, "service ${svc.uuid}")
                for (c in svc.characteristics) {
                    val p = c.properties
                    val tags = buildString {
                        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append(" NOTIFY")
                        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) append(" INDICATE")
                        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append(" WRITE")
                        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) append(" WRITE_NR")
                        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) append(" READ")
                    }
                    Log.d(TAG, "  char ${c.uuid} props=0x${Integer.toHexString(p)}$tags")
                }
            }

            // ── 1) 알려진 후보 서비스에서 특성 선택 ──
            var write: BluetoothGattCharacteristic? = null
            var notify: BluetoothGattCharacteristic? = null
            for (svcUuid in CANDIDATE_SERVICE_UUIDS) {
                val svc = g.getService(svcUuid) ?: continue
                for (c in svc.characteristics) {
                    val p = c.properties
                    if (notify == null && p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) notify = c
                    if (write == null && p and writeMask != 0) write = c
                }
                if (write != null && notify != null) break
            }

            // ── 2) 그래도 없으면 전체 서비스에서 속성으로 자동 탐지 ──
            if (write == null || notify == null) {
                for (svc in g.services) {
                    for (c in svc.characteristics) {
                        val p = c.properties
                        if (notify == null && p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                                BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) notify = c
                        if (write == null && p and writeMask != 0) write = c
                    }
                }
            }

            if (write == null || notify == null) {
                Log.w(TAG, "no usable characteristic — write=$write notify=$notify")
                _state.value = BleState.Error(context.getString(R.string.ble_err_char_not_found))
                return
            }
            Log.d(TAG, "selected write=${write.uuid} notify=${notify.uuid}")
            writeChar = write
            notifyChar = notify

            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD_UUID) ?: notify.descriptors.firstOrNull()
            if (cccd == null) {
                Log.w(TAG, "notify char ${notify.uuid} has no CCCD — notifications may not start")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            }
            connectingDevice?.let { _state.value = BleState.Connected(it) }
        }

        @Deprecated("API 32 이하")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleRx(ch.value ?: return)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = handleRx(value)
    }

    private fun handleRx(chunk: ByteArray) {
        Log.d(TAG, "RX << ${chunk.joinToString(" ") { "%02X".format(it) }} (${chunk.size}B)")
        for (frame in reassembler.offer(chunk)) {
            when (frame) {
                is FrameReassembler.Frame.Ack -> {
                    Log.d(TAG, "RX frame: SETok (ACK)")
                    pendingAck?.complete(Unit)
                    pendingAck = null
                }
                is FrameReassembler.Frame.Status -> {
                    val st = MarkSevenProtocol.parseStatus(frame.bytes)
                    Log.d(
                        TAG,
                        "RX frame: STATUS 36B — " +
                            if (st == null) "parse FAILED"
                            else "temp=${st.motorTemp.toList()} curAvg=${st.motorCurrentAvg.toList()} " +
                                "turn=${st.motorTurn.toList()} emg=${st.emg.toList()} chkOk=${st.checksumOk}",
                    )
                    st?.let { _status.tryEmit(it) }
                }
            }
        }
    }
}
