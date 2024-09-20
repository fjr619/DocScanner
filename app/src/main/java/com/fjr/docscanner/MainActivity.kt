package com.fjr.docscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fjr.docscanner.presentation.screens.scanner.ScannerScreen
import com.fjr.docscanner.ui.theme.DocScannerTheme



class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()
        setContent {
            DocScannerTheme {
                ScannerScreen()
            }
        }
    }
}

