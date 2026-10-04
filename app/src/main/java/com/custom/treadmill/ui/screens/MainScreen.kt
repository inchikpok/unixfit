package com.custom.treadmill.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.custom.treadmill.ui.viewmodels.MainViewModel

@Composable fun MainScreen(vm: MainViewModel) {
    val devices by vm.devices.collectAsState(); val treadmill by vm.treadmillConnected.collectAsState(); val hrConnected by vm.heartRateConnected.collectAsState(); val pulse by vm.heartRate.collectAsState(); val speed by vm.speed.collectAsState(); val logs by vm.logs.collectAsState()
    val ready by vm.ble.controlReady.collectAsState()
    val busy by vm.ble.commandBusy.collectAsState()
    val status by vm.ble.message.collectAsState()
    val seconds by vm.ble.elapsedSec.collectAsState()
    val distance by vm.ble.distanceKm.collectAsState()
    val calories by vm.ble.calories.collectAsState()
    val inclination by vm.ble.inclination.collectAsState()
    val inclineRange by vm.ble.inclinationRange.collectAsState()
    val inclineSupported by vm.ble.inclinationSupported.collectAsState()
    val inclineTarget by vm.ble.inclinationTarget.collectAsState()
    val inclineNote by vm.ble.inclinationNote.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmStart by remember { mutableStateOf(false) }
    if (confirmStart) AlertDialog(
        onDismissRequest = { confirmStart = false },
        title = { Text("Запустить дорожку?") },
        text = { Text("Полотно начнёт движение. Убедитесь, что ключ безопасности закреплён и вокруг полотна нет препятствий.") },
        confirmButton = { TextButton({ confirmStart = false; vm.start() }) { Text("Запустить") } },
        dismissButton = { TextButton({ confirmStart = false }) { Text("Отмена") } }
    )
    var tab by remember { mutableIntStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.scan() }
    Column(Modifier.padding(16.dp)) {
        Text("UnixFit Local", style = MaterialTheme.typography.headlineMedium); Text("Без интернета • BLE / FTMS")
        TabRow(tab) { listOf("Данные", "Устройства", "Debug").forEachIndexed { i, s -> Tab(tab == i, { tab = i }, text = { Text(s) }) } }
        Text(status, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> androidx.compose.foundation.lazy.LazyColumn {
                item {
                    Text("Дорожка: ${if (treadmill) "подключена" else "не подключена"}")
                    Text("Пульсометр: ${if (hrConnected) "подключен" else "не подключен"}")
                    Spacer(Modifier.height(12.dp))
                    Text("Скорость: %.1f км/ч".format(speed))
                    Text("Время: %02d:%02d".format(seconds / 60, seconds % 60))
                    Text("Дистанция: %.3f км".format(distance))
                    Text("Калории: $calories ккал")
                    Text("Наклон: ${inclination?.let { "%.1f %%".format(it) } ?: "нет данных"}")
                    inclineRange?.let { r -> Text("Диапазон BLE: ${r.minimum}–${r.maximum} %, шаг ${r.step} %", style = MaterialTheme.typography.bodySmall) }
                    inclineTarget?.let { Text("Задано: $it % — ожидаем показание") }
                    val inclineEnabled = ready && !busy && inclineSupported && inclination != null && inclineTarget == null && inclineRange != null
                    Row {
                        Button({ vm.ble.changeInclination(-1) }, enabled = inclineEnabled && inclination?.let { inclineRange?.next(it, -1) } != null) { Text("Наклон −") }
                        Spacer(Modifier.width(8.dp))
                        Button({ vm.ble.changeInclination(1) }, enabled = inclineEnabled && inclination?.let { inclineRange?.next(it, 1) } != null) { Text("Наклон +") }
                    }
                    Text(inclineNote, style = MaterialTheme.typography.bodySmall)
                    Text("Панель: уровни 0–15. Соответствие процентам пока не проверено.", style = MaterialTheme.typography.bodySmall)
                    Text("Пульс: ${pulse ?: "--"} уд/мин")
                    Row {
                        Button({ vm.slower() }, enabled = ready && !busy) { Text("−") }
                        Spacer(Modifier.width(8.dp))
                        Button({ vm.faster() }, enabled = ready && !busy) { Text("+") }
                        Spacer(Modifier.width(8.dp))
                        Button({ confirmStart = true }, enabled = ready && !busy) { Text("Старт") }
                    }
                    Button({ vm.stop() }, enabled = treadmill, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFB3261E))) { Text("СТОП") }
                    Text("При опасности используйте физический ключ безопасности.", style = MaterialTheme.typography.bodySmall)
                }
            }
            1 -> Column { Button({ permissions.launch(if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }) { Text("Сканировать") }; LazyColumn { itemsIndexed(devices) { i, d -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(d.name ?: d.address, Modifier.weight(1f)); Button({ if ((d.name ?: "").startsWith("FS-", true) || (d.name ?: "").startsWith("FitShow", true)) vm.connectTreadmill(i) else vm.connectHeartRate(i) }) { Text("Подключить") } } } } }
            else -> Column {
                Button({
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("BLE Debug", logs.reversed().joinToString("\n")))
                    android.widget.Toast.makeText(context, "Журнал скопирован", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Копировать журнал") }
                LazyColumn { items(logs) { Text(it, style = MaterialTheme.typography.bodySmall); HorizontalDivider() } }
            }
        }
    }
}
