package com.syncbrow.tool.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MainViewModel : ViewModel() {
    private val _navEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navEvent = _navEvent.asSharedFlow()

    fun onNewIntent(url: String) {
        _navEvent.tryEmit(url)
    }
}
