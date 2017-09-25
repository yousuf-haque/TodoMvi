package com.yohaq.todomvi.presentation.taskList.misc

import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

/**
 * Created by yousufhaque on 9/24/17.
 */


class MyPresenter @Inject constructor(
        private val fooStream: Observable<List<Foo>>,
        private val barStream: Observable<List<Bar>>,
        private val bazStream: Observable<List<Baz>>,
        private val onUserClickedThing: Observable<Unit>,
        private val onUserTypedThing: Observable<String>
) {

    private val compositeDisposable: CompositeDisposable by lazy { CompositeDisposable() }

    fun onAttach(view: TaskListView) {

        compositeDisposable +=
                combineStreams(fooStream, barStream, bazStream, onUserClickedThing, onUserTypedThing)
                        .subscribe { view.update(it) }

    }




    private fun combineStreams(
            fooStream: Observable<List<Foo>>,
            barStream: Observable<List<Bar>>,
            bazStream: Observable<List<Baz>>,
            onUserClickedThing: Observable<Unit>,
            onUserTypedThing: Observable<String>
    ): Observable<MyViewState> {
        // do some rx magic here
        TODO("not implemented")
    }

    fun onDetach() {
        compositeDisposable.clear()
    }
}



