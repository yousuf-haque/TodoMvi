package com.yohaq.todomvi

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.yohaq.todomvi.extensions.attachRouter
import com.yohaq.todomvi.presentation.taskList.TaskListController
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var router: Router
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        router = attachRouter(container_chfl, savedInstanceState)

        if (!router.hasRootController()) {
            router.pushController(RouterTransaction.with(TaskListController()))
        }


    }
}


