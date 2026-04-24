package kz.kkm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kz.kkm.data.local.DatabasePassphraseProvider
import javax.inject.Inject

@HiltAndroidApp
class KkmApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var passphraseProvider: DatabasePassphraseProvider

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // SQLCipher loaded via reflection so debug build works without the dep
        try {
            val cls = Class.forName("net.sqlcipher.database.SQLiteDatabase")
            cls.getMethod("loadLibs", android.content.Context::class.java).invoke(null, this)
        } catch (e: ClassNotFoundException) { /* debug: plain sqlite */ }
    }
}