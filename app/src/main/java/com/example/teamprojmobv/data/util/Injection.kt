package com.example.teamprojmobv.data.util

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.example.teamprojmobv.data.DataRepository
import com.example.teamprojmobv.data.db.AppRoomDatabase
import com.example.teamprojmobv.data.db.LocalCache
import com.opinyour.android.app.data.api.WebApi

/**
 * Class that handles object creation.
 * Like this, objects can be passed as parameters in the constructors and then replaced for
 * testing, where needed.
 */
object Injection {

    private fun provideCache(context: Context): LocalCache {
        val database = AppRoomDatabase.getInstance(context)
        return LocalCache(database.appDao())
    }

    fun provideDataRepository(context: Context): DataRepository {
        return DataRepository.getInstance(WebApi.create(context), provideCache(context))
    }

    fun provideViewModelFactory(context: Context): ViewModelProvider.Factory {
        return ViewModelFactory(
            provideDataRepository(
                context
            )
        )
    }
}
