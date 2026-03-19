package com.jason.dualmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import coil.compose.rememberAsyncImagePainter
import com.jason.dualmanager.data.AppInfo
import com.jason.dualmanager.data.AppPermission
import com.jason.dualmanager.shizuku.ShizukuHelper
import com.jason.dualmanager.shizuku.ShizukuStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val mainApps by viewModel.mainApps.collectAsState()
    val dualApps by viewModel.dualApps.collectAsState()
    val historyApps by viewModel.historyApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    val selectedApp by viewModel.selectedAppForPermissions.collectAsState()
    val appPermissions by viewModel.appPermissions.collectAsState()
    val isPermissionLoading by viewModel.isPermissionLoading.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Main Apps", "Dual Messenger", "History")

    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual Manager") },
                actions = {
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Recover Cloned Apps") },
                            onClick = {
                                showMenu = false
                                viewModel.recoverClonedApps()
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            val shizukuStatus by viewModel.shizukuStatus.collectAsState()

            if (shizukuStatus != ShizukuStatus.Ready) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val (message, actionText, action) = when (shizukuStatus) {
                            ShizukuStatus.NotInstalled -> Triple(
                                "Shizuku is not installed. Please install Shizuku app.",
                                "Install Shizuku",
                                { openPlayStore(context, "moe.shizuku.privileged.api") }
                            )
                            ShizukuStatus.NotRunning -> Triple(
                                "Shizuku service is not running. Please start Shizuku.",
                                "Open Shizuku",
                                { openApp(context, "moe.shizuku.privileged.api") }
                            )
                            ShizukuStatus.PermissionDenied -> Triple(
                                "Shizuku permission not granted. Please allow Dual Manager in Shizuku.",
                                "Request Permission",
                                { ShizukuHelper.requestShizukuPermission(100) }
                            )
                            else -> Triple("Shizuku is not ready.", null, null)
                        }

                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        if (actionText != null && action != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = action,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(actionText)
                            }
                        }
                    }
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
                val currentList = when (selectedTabIndex) {
                    0 -> mainApps
                    1 -> dualApps
                    else -> historyApps
                }
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(currentList) { app ->
                        val isCurrentlyCloned = dualApps.any { it.packageName == app.packageName }
                        AppItem(
                            app = app,
                            isDualApp = selectedTabIndex == 1,
                            isHistoryTab = selectedTabIndex == 2,
                            isCurrentlyCloned = isCurrentlyCloned,
                            onClone = { viewModel.cloneToDualMessenger(app.packageName) },
                            onUninstall = { viewModel.uninstallFromDualMessenger(app.packageName) },
                            onClick = { if (selectedTabIndex == 1) viewModel.loadAppPermissions(app) }
                        )
                        HorizontalDivider()
                    }
                }
            }

            selectedApp?.let { app ->
                AppPermissionDialog(
                    app = app,
                    permissions = appPermissions,
                    isLoading = isPermissionLoading,
                    onDismiss = { viewModel.dismissPermissionDialog() },
                    onToggle = { permission, allow -> viewModel.toggleAppPermission(app.packageName, permission, allow) }
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

private fun openPlayStore(context: Context, packageName: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun openApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
fun AppPermissionDialog(
    app: AppInfo,
    permissions: List<AppPermission>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onToggle: (AppPermission, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions: ${app.name}") },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (permissions.isEmpty()) {
                Text("No permissions requested by this app.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(permissions) { permission ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(permission.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (permission.isAppOp) "Special Permission" else "Runtime Permission",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (permission.isAppOp) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                                )
                                Text(permission.name, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(
                                checked = permission.isAllowed,
                                onCheckedChange = { onToggle(permission, it) }
                            )
                        }
                        HorizontalDivider()
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
    isHistoryTab: Boolean = false,
    isCurrentlyCloned: Boolean = false,
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
        } else if (isHistoryTab) {
            if (isCurrentlyCloned) {
                Text(
                    "Cloned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            } else {
                OutlinedButton(onClick = onClone) {
                    Text("Restore")
                }
            }
        } else {
            OutlinedButton(onClick = onClone) {
                Text("Clone")
            }
        }
    }
}
