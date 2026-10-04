package com.custom.treadmill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.custom.treadmill.ui.screens.MainScreen
import com.custom.treadmill.ui.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        setContent { MaterialTheme { Surface { MainScreen(vm) } } }
    }
}
