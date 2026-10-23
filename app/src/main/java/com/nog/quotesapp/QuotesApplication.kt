package com.nog.quotesapp

import android.app.Application

class QuotesApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        container.start()
    }
}
