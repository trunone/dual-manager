package com.jason.dualmanager

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jason.dualmanager.data.AppRepository
import com.jason.dualmanager.shizuku.ShizukuHelper
import com.jason.dualmanager.ui.MainScreen
import com.jason.dualmanager.ui.MainViewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(AppRepository(applicationContext))
    }

    private val REQUEST_CODE_SHIZUKU = 100

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU && grantResult == PackageManager.PERMISSION_GRANTED) {
            viewModel.updateShizukuStatus(available = true, permissionGranted = true)
            viewModel.loadApps()
        } else {
            viewModel.updateShizukuStatus(available = true, permissionGranted = false)
            Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        val granted = ShizukuHelper.isShizukuPermissionGranted()
        viewModel.updateShizukuStatus(available = true, permissionGranted = granted)
        if (granted) {
            viewModel.loadApps()
        } else {
            ShizukuHelper.requestShizukuPermission(REQUEST_CODE_SHIZUKU)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        val available = ShizukuHelper.isShizukuAvailable()
        val granted = ShizukuHelper.isShizukuPermissionGranted()
        viewModel.updateShizukuStatus(available, granted)

        if (available) {
            if (!granted) {
                ShizukuHelper.requestShizukuPermission(REQUEST_CODE_SHIZUKU)
            } else {
                viewModel.loadApps()
            }
        }
        // If not available, we wait for the listener to trigger. 
        // We removed the Toast here to avoid false alarms during initialization.

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }
}
