package io.github.trunone.dual_manager

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
import io.github.trunone.dual_manager.data.AppRepository
import io.github.trunone.dual_manager.shizuku.ShizukuHelper
import io.github.trunone.dual_manager.shizuku.ShizukuStatus
import io.github.trunone.dual_manager.ui.MainScreen
import io.github.trunone.dual_manager.ui.MainViewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(AppRepository(applicationContext))
    }

    private val REQUEST_CODE_SHIZUKU = 100

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            val status = ShizukuHelper.getShizukuStatus(this)
            viewModel.updateShizukuStatus(status)
            if (status == ShizukuStatus.Ready) {
                viewModel.loadApps()
            } else if (grantResult != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        val status = ShizukuHelper.getShizukuStatus(this)
        viewModel.updateShizukuStatus(status)
        if (status == ShizukuStatus.Ready) {
            viewModel.loadApps()
        } else if (status == ShizukuStatus.PermissionDenied) {
            ShizukuHelper.requestShizukuPermission(REQUEST_CODE_SHIZUKU)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addRequestPermissionResultListener(shizukuListener)

        val status = ShizukuHelper.getShizukuStatus(this)
        viewModel.updateShizukuStatus(status)

        if (status == ShizukuStatus.Ready) {
            viewModel.loadApps()
        } else if (status == ShizukuStatus.PermissionDenied) {
            ShizukuHelper.requestShizukuPermission(REQUEST_CODE_SHIZUKU)
        }

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
