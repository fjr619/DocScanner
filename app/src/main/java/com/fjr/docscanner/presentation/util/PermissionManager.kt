package com.fjr.docscanner.presentation.util

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

enum class PermissionStatus {
    GRANTED,
    DENIED,
    SHOULD_SHOW_RATIONALE
}

fun checkPermission(activity: Activity, permission: String): PermissionStatus {
    return when {
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED -> {
            PermissionStatus.GRANTED
        }
        activity.shouldShowRequestPermissionRationale(permission) -> {
            PermissionStatus.SHOULD_SHOW_RATIONALE
        }
        else -> {
            PermissionStatus.DENIED
        }
    }
}

@Composable
fun RequestStoragePermissions(
    onPermissionResult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val permissionState = remember { mutableStateOf(PermissionStatus.DENIED) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val allPermissionsGranted = permissions.entries.all {
            it.value
        }
        onPermissionResult(allPermissionsGranted)
    }

    LaunchedEffect(Unit) {
        val activity = context as Activity

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Check for MANAGE_EXTERNAL_STORAGE permission on Android 11+
            permissionState.value = if (Environment.isExternalStorageManager()) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.DENIED
            }

            if (permissionState.value != PermissionStatus.GRANTED) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            } else {
                onPermissionResult(true)
            }
        } else {
            // Request READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE for Android 10 and below
            permissionState.value = checkPermission(activity,  Manifest.permission.READ_EXTERNAL_STORAGE)

            if (permissionState.value != PermissionStatus.GRANTED) {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            } else {
                onPermissionResult(true)
            }
        }
    }
}

//class PermissionManager(private val activity: ComponentActivity) {
//
//    // State to observe permissions
//    var permissionGranted by mutableStateOf(false)
//        private set
//
//    // Register the activity result for requesting multiple permissions
//    private val requestPermissions =
//        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
//            permissionGranted = results[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true ||
//                    results[Manifest.permission.READ_MEDIA_IMAGES] == true ||
//                    results[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
//                    results[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true ||
//                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
//        }
//
//    fun requestForStoragePermissions() {
//        if (checkStoragePermissions()) {
//            permissionGranted = true
//            return
//        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            // For Android 11 (API 30) and above
//            if (!Environment.isExternalStorageManager()) {
//                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
//                intent.data = Uri.parse("package:${activity.packageName}")
//                activity.startActivity(intent)
//            } else {
//                permissionGranted = true
//            }
//        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
//            // For future versions, if any specific permissions are introduced
//            requestPermissions.launch(arrayOf(
//                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
//                Manifest.permission.READ_MEDIA_IMAGES
//            ))
//        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            // For Android 13 (API 33) and above
//            requestPermissions.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
//        } else {
//            // For below Android 10 (API 29)
//            requestPermissions.launch(arrayOf(
//                Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                Manifest.permission.READ_EXTERNAL_STORAGE
//            ))
//        }
//    }
//
//    private fun checkStoragePermissions(): Boolean {
//        return when {
//            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
//                // Check for MANAGE_EXTERNAL_STORAGE permission on Android 11 (API 30) and above
//                Environment.isExternalStorageManager()
//            }
//            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
//                // Future versions, if needed
//                val mediaVisualUserSelectedPermission = ContextCompat.checkSelfPermission(
//                    activity, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
//                ) == PermissionChecker.PERMISSION_GRANTED
//
//                val readMediaImagesPermission = ContextCompat.checkSelfPermission(
//                    activity, Manifest.permission.READ_MEDIA_IMAGES
//                ) == PermissionChecker.PERMISSION_GRANTED
//
//                mediaVisualUserSelectedPermission || readMediaImagesPermission
//            }
//            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
//                // Check for READ_MEDIA_IMAGES permission on Android 13 (API 33) and above
//                ContextCompat.checkSelfPermission(
//                    activity, Manifest.permission.READ_MEDIA_IMAGES
//                ) == PermissionChecker.PERMISSION_GRANTED
//            }
//            else -> {
//                // For below Android 10 (API 29)
//                val writePermission = ContextCompat.checkSelfPermission(
//                    activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
//                ) == PermissionChecker.PERMISSION_GRANTED
//                val readPermission = ContextCompat.checkSelfPermission(
//                    activity, Manifest.permission.READ_EXTERNAL_STORAGE
//                ) == PermissionChecker.PERMISSION_GRANTED
//
//                writePermission && readPermission
//            }
//        }
//    }
//}