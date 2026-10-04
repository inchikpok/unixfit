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

@OptIn(ExperimentalMaterial3Api::class) @Composable fun MainScreenV2(vm: MainViewModel) {
    val devices by vm.devices.collectAsState()
    val treadmill by vm.treadmillConnected.collectAsState()
    val hrConnected by vm.heartRateConnected.collectAsState()
    val pulse by vm.heartRate.collectAsState()
    val speed by vm.speed.collectAsState()
    val logs by vm.logs.collectAsState()
    val incline = vm.ble.inclination.collectAsState().value
    val inclineSupported by vm.ble.inclinationSupported.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UnixFit Local — Treadmill (Design Preview)") }
            )
        },
        content = { padding ->
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Speed", style = MaterialTheme.typography.titleMedium)
                            Text("${"%.1f".format(speed)} km/h", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(8.dp))
                            Row { Button({ vm.slower() }, modifier = Modifier.weight(1f)) { Text("-") }; Spacer(Modifier.width(8.dp)); Button({ vm.faster() }, modifier = Modifier.weight(1f)) { Text("+") } }
                        }
                    }
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Incline", style = MaterialTheme.typography.titleMedium)
                            Text("${incline?.let { "${"%.1f".format(it)}" } ?: "--"} %", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(8.dp))
                            if (inclineSupported) {
                                Row { Button({ vm.decreaseIncline() }, modifier = Modifier.weight(1f)) { Text("-") }; Spacer(Modifier.width(8.dp)); Button({ vm.increaseIncline() }, modifier = Modifier.weight(1f)) { Text("+") } }
                            } else {
                                Text("Incline control not supported", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { vm.start() }, modifier = Modifier.weight(1f)) { Text("Start") }
                    Button(onClick = { vm.stop() }, modifier = Modifier.weight(1f)) { Text("Stop") }
                }
                Spacer(Modifier.height(16.dp))
                Text("Logs", style = MaterialTheme.typography.titleMedium)
                Divider()
                LazyColumn { items(logs) { Text(it, style = MaterialTheme.typography.bodySmall); Divider() } }
            }
        }
    )
}
