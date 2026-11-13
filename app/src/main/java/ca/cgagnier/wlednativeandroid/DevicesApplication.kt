package ca.cgagnier.wlednativeandroid

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ca.cgagnier.wlednativeandroid.di.appModule
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DevicesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@DevicesApplication)
            modules(appModule)
        }.koin

        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    val connectionManager: DeviceConnectionManager = koin.get()
                    connectionManager.onForeground()
                }

                override fun onStop(owner: LifecycleOwner) {
                    val connectionManager: DeviceConnectionManager = koin.get()
                    connectionManager.onBackground()
                }
            })
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w("DevicesApplication", "ProcessLifecycleOwner not available: ${e.message}")
        }
    }
}
