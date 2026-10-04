package com.custom.treadmill.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.ArrayDeque
import java.util.UUID

private fun id(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    val devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val events = MutableStateFlow<List<String>>(emptyList())
    val treadmillConnected = MutableStateFlow(false)
    val heartRateConnected = MutableStateFlow(false)
    val controlReady = MutableStateFlow(false)
    val commandBusy = MutableStateFlow(false)
    val message = MutableStateFlow("Подключите дорожку")
    val speed = MutableStateFlow(0.0)
    val inclination = MutableStateFlow<Double?>(null)
    val inclinationRange = MutableStateFlow<InclinationRange?>(null)
    val inclinationSupported = MutableStateFlow(false)
    val inclinationTarget = MutableStateFlow<Double?>(null)
    val inclinationNote = MutableStateFlow("Ожидание данных наклона")
    private var inclinationStamp = 0L
    private var inclinationExpiry: Runnable? = null
    private var inclinationMotionTimeout: Runnable? = null
    val elapsedSec = MutableStateFlow(0)
    val distanceKm = MutableStateFlow(0.0)
    val calories = MutableStateFlow(0)
    val heartRate = MutableStateFlow<Int?>(null)
    private var scanner: ScanCallback? = null
    private var treadmill: Link? = null
    private var heart: Link? = null
    private var cp: BluetoothGattCharacteristic? = null
    private var pending: Int? = null
    private var commandTimeout: Runnable? = null
    private var range: Triple<Double, Double, Double>? = null
    private var pulseTimeout: Runnable? = null
    private fun log(text: String) { events.value = (listOf("${System.currentTimeMillis()} $text") + events.value).take(500) }
    private fun fail(text: String) { message.value = text; log(text) }
    private fun granted(p: String) = Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    fun canScan() = granted(if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION) && (Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT))
    fun scan() {
        if (!canScan()) { fail("Нет разрешений Bluetooth / геолокации"); return }
        if (adapter?.isEnabled != true) { fail("Включите Bluetooth"); return }
        stopScan(); devices.value = emptyList()
        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) { handler.post {
                val name = result.scanRecord?.deviceName ?: result.device.name.orEmpty()
                if (name.startsWith("FS-", true) || name.startsWith("FitShow", true) || result.scanRecord?.serviceUuids?.any { it.uuid == id("180d") } == true) {
                    devices.value = (devices.value + result.device).distinctBy { it.address }
                }
            } }
            override fun onScanFailed(errorCode: Int) { handler.post { fail("Ошибка сканирования $errorCode") } }
        }
        scanner = callback; adapter.bluetoothLeScanner?.startScan(callback)
        log("Сканирование BLE: 15 секунд")
        handler.postDelayed({ if (scanner === callback) stopScan() }, 15000)
    }
    fun stopScan() { scanner?.let { if (canScan()) adapter?.bluetoothLeScanner?.stopScan(it) }; scanner = null }
    fun connectTreadmill(device: BluetoothDevice) {
        if (!canScan()) return
        stopScan(); treadmill?.close(); resetControl(); range = null
        speed.value = 0.0; elapsedSec.value = 0; distanceKm.value = 0.0; calories.value = 0
        message.value = "Подключение к дорожке"
        treadmill = Link(true).also { it.gatt = device.connectGatt(context, false, it.callback) }
    }
    fun connectHeartRate(device: BluetoothDevice) {
        if (!canScan()) return
        stopScan(); heart?.close(); heartRate.value = null
        heart = Link(false).also { it.gatt = device.connectGatt(context, false, it.callback) }
    }
    fun disconnect() {
        stopScan(); treadmill?.close(); heart?.close(); resetControl()
        pulseTimeout?.let(handler::removeCallbacks); heartRate.value = null
    }
    private fun resetControl() {
        commandTimeout?.let(handler::removeCallbacks); pending = null; commandBusy.value = false
        cp = null; controlReady.value = false; treadmillConnected.value = false
        inclinationExpiry?.let(handler::removeCallbacks)
        inclinationMotionTimeout?.let(handler::removeCallbacks)
        inclination.value = null; inclinationRange.value = null; inclinationSupported.value = false
        inclinationTarget.value = null; inclinationStamp = 0L
        inclinationNote.value = "Ожидание данных наклона"
    }
    // Отдельная очередь для каждого соединения; новая операция начинается после callback.
    private inner class Link(val isTreadmill: Boolean) {
        var gatt: BluetoothGatt? = null
        var closed = false
        private val queue = ArrayDeque<() -> Boolean>()
        private var busy = false
        private val timeout = Runnable { fail("Тайм-аут GATT; переподключите устройство"); close() }
        private val afterDescriptors = mutableMapOf<BluetoothGattDescriptor, () -> Unit>()
        fun enqueue(action: () -> Boolean) { if (!closed) { queue.add(action); next() } }
        private fun next() {
            if (closed || busy || queue.isEmpty()) return
            busy = true
            if (!queue.removeFirst().invoke()) { fail("BLE отклонил операцию"); close(); return }
            handler.postDelayed(timeout, 8000)
        }
        fun complete(status: Int) {
            handler.removeCallbacks(timeout); busy = false
            if (status != 0) { fail("Ошибка GATT $status"); close() } else next()
        }
        fun close() {
            if (closed) return
            closed = true; queue.clear(); handler.removeCallbacks(timeout); afterDescriptors.clear()
            gatt?.disconnect(); gatt?.close()
            if (isTreadmill) resetControl() else { heartRateConnected.value = false; heartRate.value = null }
        }
        fun subscribe(c: BluetoothGattCharacteristic, indication: Boolean, after: () -> Unit = {}) {
            enqueue {
                val g = gatt; val d = c.getDescriptor(id("2902"))
                if (g == null || d == null || !g.setCharacteristicNotification(c, true)) false
                else {
                    d.value = if (indication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    afterDescriptors[d] = after
                    g.writeDescriptor(d)
                }
            }
        }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) { handler.post {
                if (closed) return@post
                log("GATT status=$status state=$state")
                if (status == 0 && state == BluetoothProfile.STATE_CONNECTED) enqueue { g.discoverServices() }
                else { fail(if (isTreadmill) "Дорожка отключена. Физическая остановка не гарантирована!" else "Пульсометр отключён"); close() }
            } }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { handler.post {
                if (closed) return@post
                complete(status); if (closed) return@post
                g.services.forEach { s -> log("Service ${s.uuid}\n${s.characteristics.joinToString("\n") { "${it.uuid} properties=0x${it.properties.toString(16)}" }}") }
                if (isTreadmill) {
                    treadmillConnected.value = true
                    val s = g.getService(id("1826"))
                    val data = s?.getCharacteristic(id("2acd"))
                    if (data != null) subscribe(data, false) else fail("Нет Treadmill Data 2ACD")
                    s?.getCharacteristic(id("2ad4"))?.let { c -> enqueue { g.readCharacteristic(c) } }
                    // Необязательные поля наклона не меняют формат команд скорости.
                    s?.getCharacteristic(id("2acc"))?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }?.let { c -> enqueue { g.readCharacteristic(c) } }
                    val inclineRange = s?.getCharacteristic(id("2ad5"))
                    if (inclineRange != null && inclineRange.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) enqueue { g.readCharacteristic(inclineRange) }
                    else inclinationNote.value = "Дорожка не предоставляет диапазон наклона 2AD5"
                    cp = s?.getCharacteristic(id("2ad9"))
                    val c = cp
                    if (c != null && c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) subscribe(c, true) { requestControl() }
                    else fail("Нет FTMS Control Point с indications")
                    s?.getCharacteristic(id("2ada"))?.let { subscribe(it, false) }
                } else {
                    val c = g.getService(id("180d"))?.getCharacteristic(id("2a37"))
                    if (c != null) subscribe(c, false) { heartRateConnected.value = true } else fail("Нет Heart Rate Measurement")
                }
            } }
            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { handler.post {
                if (closed) return@post
                val action = afterDescriptors.remove(d)
                log("CCCD ${d.characteristic.uuid} status=$status"); complete(status)
                if (!closed && status == 0) action?.invoke()
            } }
            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { handler.post {
                if (!closed) { log("WRITE ${c.uuid} status=$status"); complete(status) }
            } }
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                val b = c.value?.copyOf() ?: byteArrayOf()
                handler.post { if (!closed) {
                    if (status == 0) receive(c.uuid, b)
                    val optional = c.uuid == id("2acc") || c.uuid == id("2ad5")
                    if (status != 0 && optional) {
                        inclinationNote.value = "Не удалось прочитать параметры наклона: $status"
                        log("READ ${c.uuid} status=$status; ручная скорость остаётся доступной")
                    }
                    complete(if (optional) 0 else status)
                } }
            }
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                val b = c.value?.copyOf() ?: byteArrayOf()
                handler.post { if (!closed) receive(c.uuid, b) }
            }
        }
    }
    private fun receive(uuid: UUID, b: ByteArray) {
        log("RX $uuid: ${b.hex()}")
        when (uuid) {
            id("2acd") -> try {
                val d = FtmsData.parse(b)
                d.speed?.let { speed.value = it }; d.seconds?.let { elapsedSec.value = it }
                d.distanceKm?.let { distanceKm.value = it }; d.calories?.let { calories.value = it }
                d.inclination?.let { value ->
                    inclination.value = value; inclinationStamp = android.os.SystemClock.elapsedRealtime()
                    inclinationExpiry?.let(handler::removeCallbacks)
                    inclinationExpiry = Runnable { inclination.value = null }.also { handler.postDelayed(it, 10000) }
                    checkInclinationReached()
                }
            } catch (_: IllegalArgumentException) { log("Повреждённый пакет Treadmill Data") }
            id("2a37") -> if (b.size >= 2) {
                val wide = b[0].toInt() and 1 != 0
                if (!wide || b.size >= 3) {
                    heartRate.value = (b[1].toInt() and 255) + (if (wide) (b[2].toInt() and 255) shl 8 else 0)
                    pulseTimeout?.let(handler::removeCallbacks)
                    pulseTimeout = Runnable { heartRate.value = null }.also { handler.postDelayed(it, 10000) }
                }
            }
            id("2acc") -> {
                // Второй uint32 — возможности установки целей; бит 1 — наклон.
                inclinationSupported.value = b.size >= 8 && b[4].toInt() and 2 != 0
                if (!inclinationSupported.value) inclinationNote.value = "FTMS не сообщает поддержку управления наклоном"
            }
            id("2ad5") -> {
                inclinationRange.value = runCatching { InclinationRange.parse(b) }.getOrNull()
                inclinationNote.value = if (inclinationRange.value != null) "Наклон FTMS в процентах; уровни панели проверяются отдельно" else "Некорректный диапазон наклона"
                log("Диапазон наклона: ${inclinationRange.value}")
            }
            id("2ad4") -> if (b.size >= 6) {
                fun u(i: Int) = ((b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)) / 100.0
                range = Triple(u(0), u(2), u(4)); log("Диапазон скорости: $range")
            }
            id("2ad9") -> if (b.size >= 3 && b[0].toInt() and 255 == 128) {
                val op = b[1].toInt() and 255; val result = b[2].toInt() and 255
                if (pending == op) {
                    commandTimeout?.let(handler::removeCallbacks); pending = null; commandBusy.value = false
                    if (result == 1) {
                        if (op == 0) controlReady.value = true
                        if (op == 3) {
                            inclinationNote.value = "Команда наклона принята; ожидаем показание дорожки"
                            checkInclinationReached()
                        }
                        message.value = if (op == 0) "FTMS: управление разрешено" else "Команда подтверждена дорожкой"
                    } else {
                        if (op == 0 || result == 5) controlReady.value = false
                        if (op == 3) {
                            inclinationMotionTimeout?.let(handler::removeCallbacks)
                            inclinationTarget.value = null
                            inclinationNote.value = "Наклон отклонён: код $result"
                            if (result == 2) inclinationSupported.value = false
                        }
                        fail("FTMS отказ: opcode=$op result=$result")
                    }
                }
            }
            id("2ada") -> if (b.isNotEmpty() && b[0].toInt() and 255 == 255) { controlReady.value = false; fail("Дорожка отозвала управление") }
        }
    }
    private fun command(op: Int, bytes: ByteArray) {
        val link = treadmill; val c = cp
        if (link == null || link.closed || c == null) { fail("Нет подключения FTMS"); return }
        if (pending != null) { fail("Дождитесь ответа дорожки. При опасности используйте физический ключ!"); return }
        if (op != 0 && !controlReady.value) { fail("Нет разрешения на управление"); return }
        pending = op; commandBusy.value = true
        link.enqueue {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; c.value = bytes
            log("TX ${c.uuid}: ${bytes.hex()}")
            commandTimeout = Runnable {
                pending = null; commandBusy.value = false; controlReady.value = false
                fail("Нет ответа FTMS; переподключите дорожку. Используйте физический ключ при опасности!")
                link.close()
            }.also { handler.postDelayed(it, 10000) }
            link.gatt?.writeCharacteristic(c) == true
        }
    }
    private fun checkInclinationReached() {
        val target = inclinationTarget.value ?: return
        val actual = inclination.value ?: return
        if (pending != 3 && kotlin.math.abs(actual - target) < 0.051) {
            inclinationTarget.value = null
            inclinationMotionTimeout?.let(handler::removeCallbacks)
            inclinationNote.value = "Дорожка сообщает заданный наклон"
        }
    }
    fun changeInclination(direction: Int) {
        if (!controlReady.value || pending != null || inclinationTarget.value != null) {
            fail("Наклон: нет управления или предыдущая команда ещё выполняется"); return
        }
        val r = inclinationRange.value
        val current = inclination.value
        if (!inclinationSupported.value || r == null || current == null || android.os.SystemClock.elapsedRealtime() - inclinationStamp > 10000) {
            fail("Наклон: нужны поддержка FTMS, диапазон и свежие данные дорожки"); return
        }
        val target = r.next(current, direction)
        if (target == null) { fail("Достигнут предел наклона; тестовый лимит 0–15%, не уровни панели"); return }
        inclinationTarget.value = target
        inclinationNote.value = "Запрошен наклон $target %"
        command(3, r.command(target))
        if (pending == 3) {
            inclinationMotionTimeout = Runnable {
                inclinationSupported.value = false; inclinationTarget.value = null
                inclinationNote.value = "Нет подтверждения положения за 45 секунд. Проверьте панель и переподключитесь"
            }.also { handler.postDelayed(it, 45000) }
        } else inclinationTarget.value = null
    }
    fun requestControl() = command(0, byteArrayOf(0))
    fun start() = command(7, byteArrayOf(7))
    fun setSpeed(kmh: Double) {
        if (!kmh.isFinite() || kmh < 0 || kmh > 12) { fail("Допустимая скорость 0–12 км/ч"); return }
        val r = range
        if (kmh != 0.0 && (r == null || r.third <= 0 || kmh < r.first || kmh > r.second)) { fail("Скорость вне диапазона или диапазон не прочитан"); return }
        val target = if (r != null && kmh != 0.0) r.first + kotlin.math.round((kmh - r.first) / r.third) * r.third else kmh
        if (target > 12 || (r != null && target > r.second)) { fail("Превышен предел скорости"); return }
        val value = kotlin.math.round(target * 100).toInt()
        command(2, byteArrayOf(2, value.toByte(), (value shr 8).toByte()))
    }
    // FTMS Stop — отдельная команда, не подмена неизвестными proprietary-байтами.
    fun emergencyStop() = command(8, byteArrayOf(8, 1))
    fun setCustomUuid(value: String) { log("Proprietary UUID $value: управление пока не реализовано") }
    fun setCustomCommand(hex: String) { fail("Отправка неизвестных команд отключена в этой версии: $hex") }
}
private fun ByteArray.hex() = joinToString(" ") { "%02X".format(it.toInt() and 255) }
