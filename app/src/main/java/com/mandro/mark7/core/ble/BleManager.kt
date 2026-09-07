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

/** HM-10 류 시리얼 브리지. 기존 MandroProject 와 동일한 UUID — 바뀐 건 프레임뿐. */
private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
private val CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** HM-10 UART 버퍼 오버플로우 방지 및 기본 BLE MTU(23B) 안전 송신 청크 단위 */
private const val TX_CHUNK_SIZE = 12
private const val TX_CHUNK_INTERVAL_MS = 20L

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

    private val reassembler = FrameReassembler()
    private val found = linkedMapOf<String, BleDevice>()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var connectingDevice: BleDevice? = null

    /** 기본적으로 의수 관련 기기만 필터링하되, 필요 시 전체 기기를 볼 수 있도록 지원 */
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
        if (scanner == null || !hasScan()) {
            _state.value = BleState.Error(context.getString(R.string.ble_err_permission_scan))
            return
        }
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

            val hasHandService = result.scanRecord?.serviceUuids?.any {
                it.uuid.toString().equals(SERVICE_UUID.toString(), ignoreCase = true)
            } == true

            val isKnownPrefix = name?.lowercase()?.let { lower ->
                lower.startsWith(MARK7_NAME_PREFIX) || lower.startsWith("bt") || lower.startsWith("hm") || lower.startsWith("mark")
            } == true

            if (filterHandsOnly && !hasHandService && !isKnownPrefix) {
                return
            }

            val displayName = name ?: "Mark7 Device (${result.device.address.takeLast(5)})"
            found[result.device.address] = BleDevice(displayName, result.device.address, result.rssi)
            _state.value = BleState.DevicesFound(found.values.toList())
        }

        override fun onScanFailed(errorCode: Int) {
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
        val g = gatt ?: return false
        val ch = writeChar ?: return false
        Log.d(TAG, "TX >> ${bytes.joinToString(" ") { "%02X".format(it) }}")
        val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

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
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> disconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = BleState.Error(context.getString(R.string.ble_err_service_failed, status))
                return
            }
            val ch = g.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            if (ch == null) {
                _state.value = BleState.Error(context.getString(R.string.ble_err_char_not_found))
                return
            }
            writeChar = ch
            g.setCharacteristicNotification(ch, true)
            ch.getDescriptor(CCCD_UUID)?.let { d ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(d)
                    }
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
        for (frame in reassembler.offer(chunk)) {
            when (frame) {
                is FrameReassembler.Frame.Ack -> {
                    Log.d(TAG, "RX << SETok")
                    pendingAck?.complete(Unit)
                    pendingAck = null
                }
                is FrameReassembler.Frame.Status -> {
                    MarkSevenProtocol.parseStatus(frame.bytes)?.let { _status.tryEmit(it) }
                }
            }
        }
    }
}
