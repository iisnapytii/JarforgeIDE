package com.jarforge.ide

import android.app.Application

/**
 * Application entry point. Owns the manual dependency container.
 */
class JarforgeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
