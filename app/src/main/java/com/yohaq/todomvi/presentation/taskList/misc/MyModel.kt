package com.yohaq.todomvi.presentation.taskList.misc

import io.reactivex.Observable

/**
 * Created by yousufhaque on 9/25/17.
 */



fun buildViewStateStream(
        fooStream: Observable<List<Foo>>,
        barStream: Observable<List<Bar>>,
        bazStream: Observable<List<Baz>>,
        onUserClickedThing: Observable<DoThing1Intent>,
        onUserTypedThing: Observable<DoThing2Intent>
): Observable<MyViewState> {
    // do some rx magic here
    TODO("not implemented")
}






data class MyViewState(
        val showError: Boolean,
        val showProgress: Boolean,
        val showFoos: Boolean,
        val showBars: Boolean,
        val showBazs: Boolean,
        val foos: List<Foo> = emptyList(),
        val bars: List<Bar> = emptyList(),
        val bazs: List<Baz> = emptyList()
)


interface TaskListView {
    fun update(viewState: MyViewState)
}


object DoThing1Intent
data class DoThing2Intent(val parameter: String)


data class Foo(val foo: String)
data class Bar(val bar: String)
data class Baz(val baz: String)