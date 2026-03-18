package com.jason.dualmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jason.dualmanager.data.AppInfo
import com.jason.dualmanager.data.AppRepository
import com.jason.dualmanager.data.SpecialPermission
import com.jason.dualmanager.shizuku.ShizukuException
import com.jason.dualmanager.shizuku.ShizukuStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: AppRepository) : ViewModel() {

    private val _mainApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _dualApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _historyApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val mainApps: StateFlow<List<AppInfo>> = combine(_mainApps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps else apps.filter { it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dualApps: StateFlow<List<AppInfo>> = combine(_dualApps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps else apps.filter { it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyApps: StateFlow<List<AppInfo>> = combine(_historyApps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps else apps.filter { it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _selectedAppForPermissions = MutableStateFlow<AppInfo?>(null)
    val selectedAppForPermissions: StateFlow<AppInfo?> = _selectedAppForPermissions

    private val _specialPermissions = MutableStateFlow<List<SpecialPermission>>(emptyList())
    val specialPermissions: StateFlow<List<SpecialPermission>> = _specialPermissions

    private val _isPermissionLoading = MutableStateFlow(false)
    val isPermissionLoading: StateFlow<Boolean> = _isPermissionLoading

    private val _shizukuStatus = MutableStateFlow<ShizukuStatus>(ShizukuStatus.Ready)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus

    fun updateShizukuStatus(status: ShizukuStatus) {
        _shizukuStatus.value = status
    }

    fun loadSpecialPermissions(app: AppInfo) {
        _selectedAppForPermissions.value = app
        viewModelScope.launch {
            _isPermissionLoading.value = true
            try {
                _specialPermissions.value = repository.getSpecialPermissions(app.packageName)
            } catch (e: ShizukuException) {
                _shizukuStatus.value = e.status
                _errorMessage.value = e.message
                _selectedAppForPermissions.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load permissions: ${e.message}"
            } finally {
                _isPermissionLoading.value = false
            }
        }
    }

    fun toggleSpecialPermission(packageName: String, permission: SpecialPermission, allow: Boolean) {
        viewModelScope.launch {
            try {
                val success = repository.setSpecialPermission(packageName, permission.op, allow)
                if (success) {
                    // Refresh permissions
                    _selectedAppForPermissions.value?.let { loadSpecialPermissions(it) }
                } else {
                    _errorMessage.value = "Failed to update permission ${permission.label}"
                }
            } catch (e: ShizukuException) {
                _shizukuStatus.value = e.status
                _errorMessage.value = e.message
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun dismissPermissionDialog() {
        _selectedAppForPermissions.value = null
        _specialPermissions.value = emptyList()
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _mainApps.value = repository.getMainApps()
                _dualApps.value = repository.getDualMessengerApps()
                _historyApps.value = repository.getClonedHistoryApps()
                _shizukuStatus.value = ShizukuStatus.Ready
            } catch (e: ShizukuException) {
                _shizukuStatus.value = e.status
                _errorMessage.value = e.message
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load apps: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cloneToDualMessenger(packageName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = repository.installToDualMessenger(packageName)
                if (success) {
                    loadApps()
                } else {
                    _errorMessage.value = "Failed to clone $packageName"
                    _isLoading.value = false
                }
            } catch (e: ShizukuException) {
                _shizukuStatus.value = e.status
                _errorMessage.value = e.message
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun uninstallFromDualMessenger(packageName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = repository.uninstallFromDualMessenger(packageName)
                if (success) {
                    loadApps()
                } else {
                    _errorMessage.value = "Failed to uninstall $packageName"
                    _isLoading.value = false
                }
            } catch (e: ShizukuException) {
                _shizukuStatus.value = e.status
                _errorMessage.value = e.message
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun recoverClonedApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val failedApps = repository.recoverClonedApps()
                if (failedApps.isNotEmpty()) {
                    _errorMessage.value = "Failed to recover: ${failedApps.joinToString(", ")}"
                }
                loadApps()
            } catch (e: ShizukuException) {
                _shizukuStatus.value = e.status
                _errorMessage.value = e.message
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
