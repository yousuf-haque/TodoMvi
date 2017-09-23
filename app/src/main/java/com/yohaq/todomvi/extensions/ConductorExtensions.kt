package com.yohaq.todomvi.extensions

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.*
import com.jakewharton.rxrelay2.BehaviorRelay
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType

/**
 * Created by yousufhaque on 9/10/17.
 */

fun Activity.attachRouter(container: ViewGroup, savedInstanceState: Bundle?): Router = Conductor.attachRouter(this, container, savedInstanceState)


fun Controller.getCurrentLifecycleStatus(): ControllerLifecycleEvent =
        when {
            isBeingDestroyed || isDestroyed -> throw IllegalStateException("Cannot bind to Controller lifecycle when outside of it.")
            isAttached -> ControllerLifecycleEvent.PostAttach(this, view!!)
            view != null -> ControllerLifecycleEvent.PostCreateView(this, view!!)
            activity != null -> ControllerLifecycleEvent.PostContextAvailable(this, activity!!)
            else -> ControllerLifecycleEvent.Create
        }

fun Controller.lifecycleStream(): Observable<ControllerLifecycleEvent> =
        BehaviorRelay.createDefault(getCurrentLifecycleStatus())
                .also { relay ->

                    addLifecycleListener(object : Controller.LifecycleListener() {
                        override fun postContextUnavailable(controller: Controller) {
                            relay.accept(ControllerLifecycleEvent.PostContextUnavailable(controller))
                        }

                        override fun postCreateView(controller: Controller, view: View) {
                            relay.accept(ControllerLifecycleEvent.PostCreateView(controller, view))
                        }

                        override fun postDestroyView(controller: Controller) {
                            relay.accept(ControllerLifecycleEvent.PostDestroyView(controller))
                        }

                        override fun onRestoreInstanceState(controller: Controller, savedInstanceState: Bundle) {
                            relay.accept(ControllerLifecycleEvent.OnRestoreInstanceState(controller, savedInstanceState))
                        }

                        override fun preContextUnavailable(controller: Controller, context: Context) {
                            relay.accept(ControllerLifecycleEvent.PreContextUnavailable(controller, context))
                        }

                        override fun onRestoreViewState(controller: Controller, savedViewState: Bundle) {
                            relay.accept(ControllerLifecycleEvent.OnRestoreViewState(controller, savedViewState))
                        }

                        override fun preDetach(controller: Controller, view: View) {
                            relay.accept(ControllerLifecycleEvent.PreDetach(controller, view))
                        }

                        override fun onSaveInstanceState(controller: Controller, outState: Bundle) {
                            relay.accept(ControllerLifecycleEvent.OnSaveInstanceState(controller, outState))
                        }

                        override fun preCreateView(controller: Controller) {
                            relay.accept(ControllerLifecycleEvent.PreCreateView(controller))
                        }

                        override fun onSaveViewState(controller: Controller, outState: Bundle) {
                            relay.accept(ControllerLifecycleEvent.OnSaveViewState(controller, outState))
                        }

                        override fun onChangeEnd(controller: Controller, changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
                            relay.accept(ControllerLifecycleEvent.OnChangeEnd(controller, changeHandler, changeType))
                        }

                        override fun postDetach(controller: Controller, view: View) {
                            relay.accept(ControllerLifecycleEvent.PostDetach(controller, view))
                        }

                        override fun preAttach(controller: Controller, view: View) {
                            relay.accept(ControllerLifecycleEvent.PreAttach(controller, view))
                        }

                        override fun preContextAvailable(controller: Controller) {
                            relay.accept(ControllerLifecycleEvent.PreContextAvailable(controller))
                        }

                        override fun preDestroy(controller: Controller) {
                            relay.accept(ControllerLifecycleEvent.PreDestroy(controller))
                        }

                        override fun postContextAvailable(controller: Controller, context: Context) {
                            relay.accept(ControllerLifecycleEvent.PostContextAvailable(controller, context))
                        }

                        override fun onChangeStart(controller: Controller, changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
                            relay.accept(ControllerLifecycleEvent.OnChangeStart(controller, changeHandler, changeType))
                        }

                        override fun postAttach(controller: Controller, view: View) {
                            relay.accept(ControllerLifecycleEvent.PostAttach(controller, view))
                        }

                        override fun postDestroy(controller: Controller) {
                            relay.accept(ControllerLifecycleEvent.PostDestroy(controller))
                        }

                        override fun preDestroyView(controller: Controller, view: View) {
                            relay.accept(ControllerLifecycleEvent.PreDestroyView(controller, view))
                        }
                    })
                }.let { it.takeUntil { it is ControllerLifecycleEvent.PreDestroy } }.share()

sealed class ControllerLifecycleEvent {

    object Create : ControllerLifecycleEvent()

    data class PreContextAvailable(val controller: Controller) : ControllerLifecycleEvent()

    data class PostContextAvailable(val controller: Controller, val context: Context) : ControllerLifecycleEvent()

    data class OnRestoreInstanceState(val controller: Controller, val savedInstanceState: Bundle) : ControllerLifecycleEvent()

    data class PreCreateView(val controller: Controller) : ControllerLifecycleEvent()

    data class PostCreateView(val controller: Controller, val view: View) : ControllerLifecycleEvent()

    data class PreAttach(val controller: Controller, val view: View) : ControllerLifecycleEvent()

    data class PostAttach(val controller: Controller, val view: View) : ControllerLifecycleEvent()

    data class OnRestoreViewState(val controller: Controller, val savedViewState: Bundle) : ControllerLifecycleEvent()

    data class OnSaveViewState(val controller: Controller, val outState: Bundle) : ControllerLifecycleEvent()

    data class OnChangeStart(val controller: Controller, val changeHandler: ControllerChangeHandler, val changeType: ControllerChangeType) :
    ControllerLifecycleEvent()

    data class PreDetach(val controller: Controller, val view: View) : ControllerLifecycleEvent()

    data class PostDetach(val controller: Controller, val view: View) : ControllerLifecycleEvent()

    data class PreDestroyView(val controller: Controller, val view: View) : ControllerLifecycleEvent()

    data class PostDestroyView(val controller: Controller) : ControllerLifecycleEvent()

    data class OnSaveInstanceState(val controller: Controller, val outState: Bundle) : ControllerLifecycleEvent()

    data class PreContextUnavailable(val controller: Controller, val context: Context) : ControllerLifecycleEvent()

    data class PostContextUnavailable(val controller: Controller) : ControllerLifecycleEvent()

    data class PreDestroy(val controller: Controller) : ControllerLifecycleEvent()

    data class PostDestroy(val controller: Controller) : ControllerLifecycleEvent()

    data class OnChangeEnd(val controller: Controller, val changeHandler: ControllerChangeHandler, val changeType: ControllerChangeType) :
            ControllerLifecycleEvent()
}


fun Controller.postContextAvailableEvents() = lifecycleStream().ofType<ControllerLifecycleEvent.PostContextAvailable>()
fun Controller.postAttachEvents() = lifecycleStream().ofType<ControllerLifecycleEvent.PostAttach>()
fun Controller.preDetachEvents() = lifecycleStream().ofType<ControllerLifecycleEvent.PreDetach>()
fun Controller.preDestroyEvents() = lifecycleStream().ofType<ControllerLifecycleEvent.PreDestroy>()