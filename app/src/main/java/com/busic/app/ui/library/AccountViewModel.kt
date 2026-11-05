package com.busic.app.ui.library

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.busic.app.BusicApp
import com.busic.app.data.BiliUser
import com.busic.app.data.FavFolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val api = BusicApp.get().api
    private val _ready = MutableStateFlow(false)
    private val _user = MutableStateFlow<BiliUser?>(null)
    private val _folders = MutableStateFlow<List<FavFolder>>(emptyList())
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val ready: StateFlow<Boolean> = _ready
    val user: StateFlow<BiliUser?> = _user
    val folders: StateFlow<List<FavFolder>> = _folders
    val loading: StateFlow<Boolean> = _loading
    val error: StateFlow<String?> = _error
    private var coverJob: Job? = null

    init {
        viewModelScope.launch {
            if (api.hasSession()) load()
            _ready.value = true
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private fun clearSession() {
        api.logout()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    fun logout() {
        coverJob?.cancel()
        clearSession()
        _user.value = null
        _folders.value = emptyList()
        _error.value = null
    }

    private suspend fun load() {
        _loading.value = true
        _error.value = null
        val result = runCatching { api.nav() }
        if (result.isFailure) {
            _error.value = result.exceptionOrNull()?.message ?: "登录状态获取失败"
            _loading.value = false
            return
        }
        val user = result.getOrNull()
        if (user == null) {
            clearSession()
            _user.value = null
            _folders.value = emptyList()
            _loading.value = false
            return
        }
        _user.value = user
        runCatching { api.folders(user.mid) }
            .onSuccess { list ->
                _folders.value = list
                loadCovers(list)
            }
            .onFailure { _error.value = it.message ?: "收藏夹加载失败" }
        _loading.value = false
    }

    private fun loadCovers(list: List<FavFolder>) {
        coverJob?.cancel()
        coverJob = viewModelScope.launch {
            val gate = Semaphore(4)
            list.filter { it.count > 0 }.map { folder ->
                async {
                    gate.withPermit {
                        val cover = runCatching { api.folderCover(folder.id) }.getOrDefault("")
                        if (cover.isBlank()) return@withPermit
                        _folders.update { current ->
                            current.map { if (it.id == folder.id) it.copy(cover = cover) else it }
                        }
                    }
                }
            }.awaitAll()
        }
    }
}
