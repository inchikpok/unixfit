package com.custom.treadmill.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.custom.treadmill.data.database.AppDatabase
import com.custom.treadmill.data.database.Program
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgramViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.create(app).programDao()
    val programs = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(name: String, description: String, segmentsJson: String) = viewModelScope.launch { dao.insert(Program(name = name, description = description, segmentsJson = segmentsJson)) }
    fun delete(program: Program) = viewModelScope.launch { dao.delete(program) }
}
