package com.yohaq.todomvi.presentation.taskList

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.jakewharton.rxrelay2.PublishRelay
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import com.yohaq.todomvi.R
import com.yohaq.todomvi.TodoMviApplication
import com.yohaq.todomvi.depdendencyInjection.UiComponent
import com.yohaq.todomvi.extensions.*
import com.yohaq.todomvi.presentation.taskList.adapterItems.TaskItem
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.switchOnNext
import kotlinx.android.synthetic.main.controller_task_list.view.*

class TaskListController : Controller() {

    init {

        val transientIntentRelay = PublishRelay.create<Observable<TaskListIntent>>()
        val intentStream: Observable<TaskListIntent> =
                postAttachEvents()
                        .map(ControllerLifecycleEvent.PostAttach::view)
                        .switchMap { view -> view.getIntentStream(preDetachEvents().map { Unit }) }

        val dialogSingle: Single<TaskListDialog> =
                postContextAvailableEvents()
                        .firstOrError()
                        .map(ControllerLifecycleEvent.PostContextAvailable::controller)
                        .map { controller -> controller.activity ?: throw RuntimeException("Unable to build component") }
                        .map(Activity::getApplication)
                        .cast(TodoMviApplication::class.java)
                        .map(TodoMviApplication::uiComponent)
                        .map(UiComponent::taskListComponent)
                        .map(TaskListComponent::dialog)


        val viewStateStream: Observable<TaskListViewState> = dialogSingle.flatMapObservable { dialog ->
            Observable.merge(intentStream, transientIntentRelay.switchOnNext()).compose(dialog)
        }

        val updateViewRequestStream: Observable<UpdateViewRequest> =
                Observable.combineLatest(
                        lifecycleStream(),
                        viewStateStream,
                        BiFunction(::UpdateViewRequest)
                )

        updateViewRequestStream
                .takeUntil(preDestroyEvents())
                .observeOn(AndroidSchedulers.mainThread())
//                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            handleUpdateViewRequest(
                                    viewUpdateRequest = it,
                                    bindNewIntents = { intent: Observable<TaskListIntent> -> transientIntentRelay.accept(intent) }
                            )
                        },
                        this::handleError
                )

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View =
            inflater.inflate(R.layout.controller_task_list, container, false)

    private fun handleUpdateViewRequest(viewUpdateRequest: UpdateViewRequest, bindNewIntents: (Observable<TaskListIntent>) -> Unit) {
        viewUpdateRequest.also { (event, viewState) ->
            when (event) {
                is ControllerLifecycleEvent.PostAttach -> event.view.update(viewState).also { bindNewIntents(it) }
            }
        }
    }

    private fun handleError(error: Throwable) {
        Log.e(TAG, "error in update request stream", error)
        throw error
    }

    private fun View.getIntentStream(detachStream: Observable<Unit>): Observable<TaskListIntent> {

        val draftsStream: Observable<TaskListIntent.UpdateTaskDraft> =
                et_task_name.textChanges().map { it.toString() }.map { TaskListIntent.UpdateTaskDraft(it) }.subscribeOn(AndroidSchedulers.mainThread()).share()

        val retryStream: Observable<TaskListIntent.RetryLoadingTasks> =
                btn_retry.clicks().map { TaskListIntent.RetryLoadingTasks }.subscribeOn(AndroidSchedulers.mainThread()).share()

        val addTaskIntents: Observable<TaskListIntent.AddTaskRequest> =
                tv_add_note.clicks().withLatestFrom(
                                draftsStream,
                                BiFunction<Unit, TaskListIntent.UpdateTaskDraft, TaskListIntent.UpdateTaskDraft> {_, intent-> intent }
                        ).map { TaskListIntent.AddTaskRequest(it.content) }.subscribeOn(AndroidSchedulers.mainThread())
        val allIntents = Observable.merge(addTaskIntents, draftsStream, retryStream)


        return allIntents.takeUntil(detachStream)
    }

    private fun View.update(viewState: TaskListViewState): Observable<TaskListIntent> {
        val TAG = "TaskListController"

        val transientIntentRelay = PublishRelay.create<TaskListIntent>()

        task_list_progress_bar.visibility = if(viewState.isProgressBarVisible) View.VISIBLE else View.GONE
        rv_task_list.isVisible = viewState.isTaskListVisible
        ll_compose_area.isVisible = viewState.isComposeAreaVisible
        tv_add_note.isVisible = viewState.isAddButtonVisible
        tv_task_list_error_message.isVisible = viewState.isErrorMessageVisible
        btn_retry.isVisible = viewState.isRefreshVisible


        val adapter: GroupAdapter<*> = rv_task_list.adapter as? GroupAdapter ?: GroupAdapter<ViewHolder>().also { rv_task_list.adapter = it }
        adapter.apply {
            clear()
            viewState.tasks.map {
                TaskItem(
                        taskItemViewState = it,
                        onChecked = { it.markTaskCompleteIntent?.let { transientIntentRelay.accept(it) } },
                        onUnchecked = { it.markTaskIncompleteIntent?.let { transientIntentRelay.accept(it) } },
                        onRetryClicked = { it.retryIntent?.let { transientIntentRelay.accept(it) } },
                        onClearClicked = { it.clearIntent?.let { transientIntentRelay.accept(it) } }
                )
            }.also { addAll(it) }
        }




        Log.d(TAG, "taskListViewState:\n$viewState")
        return transientIntentRelay.toFlowable(BackpressureStrategy.BUFFER).toObservable().subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread())
    }


    data class UpdateViewRequest(val event: ControllerLifecycleEvent, val viewState: TaskListViewState)


    companion object {
        private val TAG = TaskListController::class.java.simpleName!!
    }
}