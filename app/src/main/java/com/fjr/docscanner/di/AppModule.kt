package com.fjr.docscanner.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan(value = "com.fjr.docscanner.data")
class DataModule {
    @Single
    fun provideCoroutine() = CoroutineScope(Dispatchers.IO)
}

@Module
@ComponentScan(value = "com.fjr.docscanner.domain")
class DomainModule

@Module
@ComponentScan(value = "com.fjr.docscanner.presentation")
class PresentationModule

@Module(includes = [DataModule::class, DomainModule::class, PresentationModule::class])
class AppModule