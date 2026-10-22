package com.lincx.licai

import android.app.Application
import androidx.room.Room
import com.lincx.licai.data.AppDatabase
import com.lincx.licai.data.FinanceRepository

class AppContainer(app: Application) {
    private val database = Room.databaseBuilder(app, AppDatabase::class.java, "licai.db").build()
    val repository = FinanceRepository(database)
}

class LicaiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
