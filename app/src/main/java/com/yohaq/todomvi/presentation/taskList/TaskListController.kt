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
import com.yohaq.todomvi.presentation.taskList.dialog.TaskListIntent
import com.yohaq.todomvi.presentation.taskList.dialog.TaskListViewState
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.switchOnNext
import kotlinx.android.synthetic.main.controller_task_list.view.*

class TaskListController : Controller() {

    init {


        /*
         * First we make a stream that will build us the dagger component
         * for task list, and then we grab the dialog from there.
         *
         * This dialog is an Observable Transformer that takes in
         * Observable<Intent> and spits out Observable<ViewState>
         *
         * This transformer encapsulates all work needed to render the UI
         */
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


        /*
         * Next we construct the stream of intents from the UI.
         *
         * On every emission of the PostAttachEvent we'll get a new view,
         * and we'll use View.getIntentStream() to construct the intent stream from that view
         */
        val intentStream: Observable<TaskListIntent> =
                postAttachEvents()
                        .map(ControllerLifecycleEvent.PostAttach::view)
                        .switchMap { view ->
                            view.getIntentStream(
                                    detachStream = preDetachEvents().map { Unit }
                            )
                        }


        /*
         *
         * After each render event, we might have new views on the screen that generate intents.
         * We need to bind those intents so we can track those events.
         *
         * To do so, I used a relay, and push new intents after updating onto the relay
         *
         */
        val transientIntentRelay = PublishRelay.create<Observable<TaskListIntent>>()


        /*
         * We take the streams of intents, both the transient ones and the non-transient ones, and merge them.
         *
         * After merged, we feed those into the dialog, which will use the intents + app data to build view states and emit those
         *
         */
        val viewStateStream: Observable<TaskListViewState> = dialogSingle.flatMapObservable { dialog ->
            Observable.merge(intentStream, transientIntentRelay.switchOnNext()).compose(dialog)
        }


        /*
         * In order to see view availability when the ViewState was emitted, we pair it with the latest lifecycleEvent.
         * This will also clear out the currently held value for the combining of the different streams,
         * in case the view were to become unavailable.
         *
         * UpdateViewRequest is a data class that has a lifecycle status, and a view state.
         * Similar to a Tuple, but I believe it's best practice to give these types
         * of relationships a name
         */
        val updateViewRequestStream: Observable<UpdateViewRequest> =
                Observable.combineLatest(
                        lifecycleStream(),
                        viewStateStream,
                        BiFunction(::UpdateViewRequest)
                )


        /*
         * Finally we have our side effect here.
         *
         * We get events of view states paired with an instance of the view to operate on.
         *
         * We send it over to the handle update function, to perform the handling of the viewState.
         * We also send over a function that handleUpdateRequests will use to
         * notify us that there are new intents to be bound after update.
         *
         * I didn't have the binding function at first, but realized that recycler views make it necessary to have something like this,
         * unless youre going to start putting observables in your viewHolders.
         * I think that's a valid option to try, but I want to look in the disposing implications
         */
        updateViewRequestStream
                .takeUntil(preDestroyEvents())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            handleUpdateViewRequest(
                                    viewUpdateRequest = it,
                                    // transientIntentRelay is captured in this lambda's closure
                                    bindNewIntents = { intent: Observable<TaskListIntent> -> transientIntentRelay.accept(intent) }
                            )
                        },
                        this::handleError
                )

    }


    /*
        Here we access the different UI widgets, grab streams from them, map them to their corresponding intents,
        and combine them into a stream of intents.

        We asl for a detach stream as a parameter so that we know when to unsubscribe from the streams

     */
    private fun View.getIntentStream(detachStream: Observable<Unit>): Observable<TaskListIntent> {

        val updateDraftIntents: Observable<TaskListIntent.UpdateTaskDraft> =
                et_task_name.textChanges()
                        .map { it.toString() }
                        .map { TaskListIntent.UpdateTaskDraft(it) }
                        .subscribeOn(AndroidSchedulers.mainThread())
                        .share()

        val retryLoadingTaskIntents: Observable<TaskListIntent.RetryLoadingTasks> =
                btn_retry.clicks()
                        .map { TaskListIntent.RetryLoadingTasks }
                        .subscribeOn(AndroidSchedulers.mainThread()).share()

        val addTaskIntents: Observable<TaskListIntent.AddTaskRequest> =
                tv_add_note.clicks().withLatestFrom(
                        updateDraftIntents,
                        BiFunction<Unit, TaskListIntent.UpdateTaskDraft, TaskListIntent.UpdateTaskDraft> { _, intent -> intent }
                )
                        .map { TaskListIntent.AddTaskRequest(it.content) }
                        .subscribeOn(AndroidSchedulers.mainThread())

        return Observable.merge(addTaskIntents, updateDraftIntents, retryLoadingTaskIntents).takeUntil(detachStream)
    }


    /*
        This will take care of deciding if we want to update the view.
        Currently its checking that the view is attached before calling
        update on it with the new view state
     */
    private fun handleUpdateViewRequest(viewUpdateRequest: UpdateViewRequest, bindNewIntents: (Observable<TaskListIntent>) -> Unit) {

        /* We can selectively choose to update the view based on what lifecycle status it's in.
         * We know the lifecycle status exactly, because it's right there on the thing that we have to render.
         */
        viewUpdateRequest.also { (event, viewState) ->
            when (event) {
            // only update when attached
                is ControllerLifecycleEvent.PostAttach -> {
                    event.view
                            .update(viewState)
                            .also { newIntents -> bindNewIntents(newIntents) }
                }
            }
        }
    }


    /*
        Our job here is to make the UI reflect the view state that was just passed in.
        We are told to return any new intents that may or may not need to be bound.

        If we didn't have any more, we could return Observable.empty()
     */
    private fun View.update(viewState: TaskListViewState): Observable<TaskListIntent> {
        val TAG = "TaskListController"

        val transientIntentRelay = PublishRelay.create<TaskListIntent>()

        task_list_progress_bar.visibility = if (viewState.isProgressBarVisible) View.VISIBLE else View.GONE
        rv_task_list.isVisible = viewState.isTaskListVisible
        ll_compose_area.isVisible = viewState.isComposeAreaVisible
        tv_add_note.isVisible = viewState.isAddButtonVisible
        tv_task_list_error_message.isVisible = viewState.isErrorMessageVisible
        btn_retry.isVisible = viewState.isRefreshVisible


        val adapter: GroupAdapter<*> =
                rv_task_list.adapter as? GroupAdapter
                        ?: GroupAdapter<ViewHolder>()
                        .also { rv_task_list.adapter = it }
        adapter.apply {
            clear()
            viewState.tasks
                    .map { task ->
                        TaskItem(
                                taskItemViewState = task,
                                onChecked = { task.markTaskCompleteIntent?.let { transientIntentRelay.accept(it) } },
                                onUnchecked = { task.markTaskIncompleteIntent?.let { transientIntentRelay.accept(it) } },
                                onRetryClicked = { task.retryIntent?.let { transientIntentRelay.accept(it) } },
                                onClearClicked = { task.clearIntent?.let { transientIntentRelay.accept(it) } }
                        )
                    }.also { addAll(it) }
        }




        Log.d(TAG, "taskListViewState:\n$viewState")
        return transientIntentRelay
                .toFlowable(BackpressureStrategy.BUFFER)
                .toObservable()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View =
            inflater.inflate(R.layout.controller_task_list, container, false)

    private fun handleError(error: Throwable) {
        Log.e(TAG, "error in update request stream", error)
        throw error
    }

    data class UpdateViewRequest(val event: ControllerLifecycleEvent, val viewState: TaskListViewState)


    companion object {
        private val TAG = TaskListController::class.java.simpleName!!
    }

}