package com.greenart7c3.nostrsigner.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.client.android.Intents
import com.greenart7c3.nostrsigner.R
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Opens the QR scanner through zxing's CaptureActivity.
 *
 * zxing asks for the camera permission itself, but when the permission is denied it
 * reports "the Android camera encountered a problem, you may need to restart the
 * device" — which blames the hardware and never mentions the permission. So ask for
 * the permission here first and, when it is refused, explain what is actually wrong:
 * offer to ask again, or a link to the app settings once Android no longer shows the
 * system prompt (permanent denial).
 */
@Composable
fun SimpleQrCodeScanner(onScan: (String?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val qrLauncher =
        rememberLauncherForActivityResult(ScanContract()) {
            onScan(it.contents)
        }

    val scanOptions =
        ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Point to the QR Code")
            setBeepEnabled(false)
            setOrientationLocked(false)
            addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                qrLauncher.launch(scanOptions)
            } else {
                val activity = context.findActivity()
                permissionPermanentlyDenied =
                    activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                showPermissionDialog = true
            }
        }

    DisposableEffect(lifecycleOwner) {
        if (context.hasCameraPermission()) {
            qrLauncher.launch(scanOptions)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose { }
    }

    if (showPermissionDialog) {
        val dismiss = {
            showPermissionDialog = false
            onScan(null)
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(text = stringResource(R.string.camera_permission_title)) },
            text = { Text(text = stringResource(R.string.camera_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        if (permissionPermanentlyDenied) {
                            context.openAppSettings()
                            onScan(null)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (permissionPermanentlyDenied) R.string.app_settings else R.string.allow,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun Context.hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
    PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun Context.openAppSettings() {
    val intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
