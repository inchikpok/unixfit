package com.custom.treadmill

import android.app.Service
import android.content.Intent
import android.os.IBinder

class WorkoutForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
