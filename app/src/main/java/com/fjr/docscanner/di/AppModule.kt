package com.fjr.docscanner.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan(value = "com.fjr.docscanner.data")
class DataModule

@Module
@ComponentScan(value = "com.fjr.docscanner.domain")
class DomainModule

@Module
@ComponentScan(value = "com.fjr.docscanner.presentation")
class PresentationModule

@Module(includes = [DataModule::class, DomainModule::class, PresentationModule::class])
class AppModule