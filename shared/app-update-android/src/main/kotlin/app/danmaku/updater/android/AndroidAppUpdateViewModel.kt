package app.danmaku.updater.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AndroidAppUpdateViewModel internal constructor(
    private val updater: AndroidAppUpdater,
) : ViewModel() {
    val state: StateFlow<AppUpdateState> = updater.state

    fun startAutomaticCheck() {
        viewModelScope.launch { updater.check(manual = false) }
    }

    fun checkNow() {
        viewModelScope.launch { updater.check(manual = true) }
    }

    fun download() {
        viewModelScope.launch { updater.download() }
    }

    fun dismiss() = updater.dismiss()
}

class AndroidAppUpdateViewModelFactory(
    context: Context,
    configuration: AppUpdateConfiguration,
) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext
    private val configuration = configuration

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AndroidAppUpdateViewModel::class.java)) {
            "Unsupported ViewModel ${modelClass.name}"
        }
        return AndroidAppUpdateViewModel(
            AndroidAppUpdater(applicationContext, configuration),
        ) as T
    }
}
