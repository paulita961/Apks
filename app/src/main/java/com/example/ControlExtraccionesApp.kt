package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.prefs.PreferencesDataStore
import com.example.data.repository.LedgerRepository
import com.example.data.sync.SupabaseSyncService

class AppContainer(application: Application) {
    private val database: AppDatabase by lazy { AppDatabase.build(application) }
    private val preferences: PreferencesDataStore by lazy { PreferencesDataStore(application) }
    private val syncService: SupabaseSyncService by lazy { SupabaseSyncService(application) }
    val repository: LedgerRepository by lazy {
        LedgerRepository(application, database, preferences, syncService)
    }
}

class ControlExtraccionesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
