package com.fjr.docscanner

import android.app.Application
import com.fjr.docscanner.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

class DocScanner: Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DocScanner)
            modules(
                AppModule().module
            )
        }
    }
}