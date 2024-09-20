package com.fjr.docscanner.presentation.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.fjr.docscanner.R
import com.fjr.docscanner.presentation.util.showToast
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

@Composable
fun Scanner(
    selectedTabIndex: Int,
    activity: ComponentActivity,
    onResult: (ActivityResult) -> Unit
) {
    println("selected $selectedTabIndex")

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = onResult
    )

    var optionsBuilder = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        .setScannerMode(SCANNER_MODE_FULL)

    if (selectedTabIndex == 0)
        optionsBuilder = optionsBuilder.setPageLimit(1)


    val scanner = GmsDocumentScanning.getClient(optionsBuilder.build())

    scanner.getStartScanIntent(activity)
        .addOnSuccessListener { intentSender ->
            scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
        .addOnFailureListener {
            activity.showToast( R.string.scanner_build_error_text)
        }
}
