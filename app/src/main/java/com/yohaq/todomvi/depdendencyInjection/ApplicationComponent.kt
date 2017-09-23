package com.yohaq.todomvi.depdendencyInjection

import android.content.Context
import dagger.Component
import dagger.Module
import dagger.Provides

/**
 * Created by yousufhaque on 9/17/17.
 */
@Component (modules = arrayOf(AppModule::class))
interface ApplicationComponent {
    val applicationContext: Context
}

@Module
class AppModule(private val context: Context){

    @Provides
    fun provideContext() = context
}