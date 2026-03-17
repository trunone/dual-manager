package com.jason.dualmanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import coil.compose.rememberAsyncImagePainter
import com.jason.dualmanager.data.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val mainApps by viewModel.mainApps.collectAsState()
    val dualApps by viewModel.dualApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    val selectedApp by viewModel.selectedAppForPermissions.collectAsState()
    val specialPermissions by viewModel.specialPermissions.collectAsState()
    val isPermissionLoading by viewModel.isPermissionLoading.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Main Apps", "Dual Messenger")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual Manager") },
                actions = {
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
            val isShizukuPermissionGranted by viewModel.isShizukuPermissionGranted.collectAsState()

            if (!isShizukuAvailable || !isShizukuPermissionGranted) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (!isShizukuAvailable) "Shizuku is not running. Please start Shizuku." else "Shizuku permission not granted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            val searchQuery by viewModel.searchQuery.collectAsState()
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text("Search apps...") },
                singleLine = true,
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val currentList = if (selectedTabIndex == 0) mainApps else dualApps
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(currentList) { app ->
                        AppItem(
                            app = app,
                            isDualApp = selectedTabIndex == 1,
                            onClone = { viewModel.cloneToDualMessenger(app.packageName) },
                            onUninstall = { viewModel.uninstallFromDualMessenger(app.packageName) },
                            onClick = { if (selectedTabIndex == 1) viewModel.loadSpecialPermissions(app) }
                        )
                        HorizontalDivider()
                    }
                }
            }

            selectedApp?.let { app ->
                SpecialPermissionDialog(
                    app = app,
                    permissions = specialPermissions,
                    isLoading = isPermissionLoading,
                    onDismiss = { viewModel.dismissPermissionDialog() },
                    onToggle = { permission, allow -> viewModel.toggleSpecialPermission(app.packageName, permission, allow) }
                )
            }

            errorMessage?.let {
                // simple error display
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("Error") },
                    text = { Text(it) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SpecialPermissionDialog(
    app: AppInfo,
    permissions: List<com.jason.dualmanager.data.SpecialPermission>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onToggle: (com.jason.dualmanager.data.SpecialPermission, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Special Permissions: ${app.name}") },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (permissions.isEmpty()) {
                Text("No special permissions requested by this app.")
            } else {
                Column {
                    permissions.forEach { permission ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(permission.label, style = MaterialTheme.typography.bodyLarge)
                                Text(permission.op, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = permission.isAllowed,
                                onCheckedChange = { onToggle(permission, it) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun AppItem(
    app: AppInfo,
    isDualApp: Boolean,
    onClone: () -> Unit,
    onUninstall: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        app.icon?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = app.name,
                modifier = Modifier.size(48.dp)
            )
        } ?: Box(modifier = Modifier.size(48.dp))
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.name, style = MaterialTheme.typography.titleMedium)
            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
        }

        if (isDualApp) {
            Button(onClick = onUninstall) {
                Text("Uninstall")
            }
        } else {
            OutlinedButton(onClick = onClone) {
                Text("Clone")
            }
        }
    }
}
