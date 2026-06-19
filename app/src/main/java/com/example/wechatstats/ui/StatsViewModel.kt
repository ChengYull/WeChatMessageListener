package com.example.wechatstats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wechatstats.data.AppDatabase
import com.example.wechatstats.data.StatsRepository
import com.example.wechatstats.data.StatsRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.app.Application

class StatsViewModel(app: Application) : ViewModel() {

    private val repository = StatsRepository(AppDatabase.getDatabase(app).messageDao())

    val stats: StateFlow<List<StatsRow>> = repository.statsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatsViewModel(app) as T
    }
}
