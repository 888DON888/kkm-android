package kz.kkm

import android.app.Application
import android.content.Context
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
        // Load SQLCipher native libs
        net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
    }
}
