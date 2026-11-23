package org.mz.mzfit

import android.app.Application
import org.mz.mzfit.data.AppContainer

class MzFitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
