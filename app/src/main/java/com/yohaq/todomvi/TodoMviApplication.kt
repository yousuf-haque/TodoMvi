package com.yohaq.todomvi

import android.app.Application
import com.yohaq.todomvi.depdendencyInjection.*

/**
 * Created by yousufhaque on 9/17/17.
 */
class TodoMviApplication : Application() {

    private val appComponent: ApplicationComponent by lazy {
        DaggerApplicationComponent.builder().appModule(AppModule(applicationContext)).build()
    }

    private val dataComponent: DataComponent by lazy {
        DaggerDataComponent.builder().applicationComponent(appComponent).build()
    }

    val uiComponent: UiComponent by lazy {
        dataComponent.getUiComponent()
    }
}