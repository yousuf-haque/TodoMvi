package com.yohaq.todomvi.presentation.taskList.dialog

import android.util.Log
import com.yohaq.todomvi.data.Task
import com.yohaq.todomvi.depdendencyInjection.AddTaskRequestBuilder
import com.yohaq.todomvi.depdendencyInjection.MarkTaskCompleteBuilder
import com.yohaq.todomvi.depdendencyInjection.MarkTaskIncompleteBuilder
import com.yohaq.todomvi.extensions.just
import io.reactivex.Observable
import io.reactivex.Single

/**
 * Created by yousufhaque on 9/17/17.
 */

fun buildTaskListStateStream(
        intentStream: Observable<TaskListIntent>,
        taskListStream: Single<List<Task>>,
        addRequestBuilder: AddTaskRequestBuilder,
        markTaskCompleteBuilder: MarkTaskCompleteBuilder,
        markTaskIncompleteBuilder: MarkTaskIncompleteBuilder
): Observable<out TaskListState> {

    val TAG = "TaskListModel"

    fun getStateReducer(intent: TaskListIntent) =
            when (intent) {
                is TaskListIntent.AddTaskRequest -> getAddTasksReducer(intent, addRequestBuilder)
                TaskListIntent.RetryLoadingTasks -> getLoadTasksReducer(taskListStream)
                is TaskListIntent.UpdateTaskDraft -> getUpdateTaskDraftReducer(intent)
                is TaskListIntent.MarkTaskComplete -> getMarkTaskCompleteReducer(intent, markTaskCompleteBuilder)
                is TaskListIntent.MarkTaskIncomplete -> getMarkTaskIncompleteReducer(intent, markTaskIncompleteBuilder)
                is TaskListIntent.RetryAddTaskRequest -> getRetryAddRequestReducer(intent, addRequestBuilder)
                is TaskListIntent.RetryMarkTaskComplete -> getRetryMarkTaskCompleteReducer(intent, markTaskCompleteBuilder)
                is TaskListIntent.RetryMarkTaskIncomplete -> getRetryMarkTaskIncompleteReducer(intent, markTaskIncompleteBuilder)
                is TaskListIntent.ClearFailedMarkIncompleteRequest -> getClearFailedMarkIncompleteReducer(intent)
                is TaskListIntent.ClearFailedMarkCompleteRequest -> getClearFailedMarkCompleteReducer(intent)
                is TaskListIntent.ClearFailedAddTaskRequest -> getClearFailedAddTaskReducer(intent)
            }




    val initialStateReducer: Observable<TaskListStateReducer> = getLoadTasksReducer(taskListStream)

    val intentStateReducers: Observable<TaskListStateReducer> = intentStream.flatMap { intent -> getStateReducer(intent) }

    val stateReducerStream: Observable<TaskListStateReducer> = initialStateReducer.mergeWith(intentStateReducers)


    return stateReducerStream
            .scan(
                    TaskListState.LoadingTasks as TaskListState,
                    { oldState, reducer ->
                        reducer(oldState).also {
                            Log.d(TAG, "oldState:\n\t$oldState")
                            Log.d(TAG, "newState:\n\t$it")
                        }
                    }
            )
}


/*
    A reducer is a function that takes one state and spits out another.
     This observable stream represents how to transform the model state,
    based on the status and changes in status for the execution of the intent that was passed in.

    What this ends up looking like this stream will emit 1 reducer that can transform a state to one that is loading,
    and then one more reducer that will update the state to reflect the completion or failure of a request (by returning the appropriate state)

    The previous state is available to each reducer so that it can update the existing state, and return the "next" state.

    This is analogous to using a callback that would have onBeginRequest, onSuccess, and onError functions, and pass
    in a snapshot of member variable to be operated on for that call.
    You could use those values and build the next state base don the result of the callback.
 */
private fun getAddTasksReducer(intent: TaskListIntent.AddTaskRequest, addRequestBuilder: AddTaskRequestBuilder): Observable<TaskListStateReducer> {

    val task = Task(intent.requestId, intent.title, false)

    // This tells us how to update the state to reflect that an add task request is in flight.
    // In our case that means adding a pending task to the list of existing tasks.
    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> state.copy(tasks = state.tasks + TaskItemState.PendingTask(task, intent))
            else -> state
        }
    }

    // This tells us how to update a state to reflect that the request completed successfully.
    // We do this by removing the corresponding pending task from the list and then adding an incomplete one
    //

    fun getOnSuccess(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> state.copy(tasks = (state.tasks - TaskItemState.PendingTask(task, intent)) + TaskItemState.IncompleteTask(task,
                    TaskListIntent.MarkTaskComplete(task.id)))
            else -> state
        }
    }

    // This tells us how to update the state to reflect that the request failed.
    // We do this be removing the pending task and adding an AddTaskFailure item
    fun getOnErrorReducer(error: Throwable): TaskListStateReducer = { state ->
        Log.e("TaskListView", "add task failure", error)
        when (state) {
            is TaskListState.TasksLoaded ->
                state.copy(
                        tasks = (state.tasks - TaskItemState.PendingTask(task, intent)) +
                                TaskItemState.AddTaskFailure(
                                        intent,
                                        Exception(error),
                                        TaskListIntent.RetryAddTaskRequest(Task(intent.requestId, intent.title, false)),
                                        TaskListIntent.ClearFailedAddTaskRequest(intent.requestId)
                                )
                )
            else -> state
        }
    }

    // We build the request, modify the stream to return the reducers for the corresponding events, and we return it
    return addRequestBuilder(task)
            .toSingle { getOnSuccess() }
            .onErrorReturn { getOnErrorReducer(it) }
            .toObservable()
            .startWith(getOnSubmitReducer())
}


private fun getLoadTasksReducer(taskListStream: Single<List<Task>>): Observable<TaskListStateReducer> {

    fun getOnSubmitRequestReducer(): TaskListStateReducer = { TaskListState.LoadingTasks }

    fun getOnSuccessReducer(taskList: List<Task>): TaskListStateReducer =
            { state ->

                when (state) {
                    is TaskListState.TasksLoaded -> {
                        state.copy(tasks = state.tasks)
                    }
                    else -> TaskListState.TasksLoaded(
                            tasks = taskList.map(Task::toTaskItemState),
                            isTaskDraftValid = (state as? TaskListState.TasksLoaded)?.isTaskDraftValid ?: false
                    )
                }

            }

    fun getOnErrorReducer(error: Throwable): TaskListStateReducer = {
        Log.e("getLoadTasksReducer", "error loading tasks", error)
        TaskListState.ErrorLoadingTasks
    }

    return taskListStream
            .map { getOnSuccessReducer(it) }
            .onErrorReturn { getOnErrorReducer(it) }
            .toObservable()
            .startWith(getOnSubmitRequestReducer())

}

private fun getUpdateTaskDraftReducer(intent: TaskListIntent.UpdateTaskDraft): Observable<TaskListStateReducer> {
    return { state: TaskListState ->
        when (state) {
            is TaskListState.TasksLoaded -> state.copy(isTaskDraftValid = intent.content.isNotBlank())
            else -> state
        }
    }.just()
}

private fun getMarkTaskCompleteReducer(intent: TaskListIntent.MarkTaskComplete, markTaskCompleteBuilder: MarkTaskCompleteBuilder): Observable<TaskListStateReducer> {
    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.IncompleteTask && it.task.id == intent.requestId }
                        ?.let { it as? TaskItemState.IncompleteTask }
                        ?.let { task ->
                            state.copy(tasks = ((state.tasks - task) + TaskItemState.MarkingTaskComplete(task.task.copy(isComplete = true), task
                                    .markTaskCompleteIntent)))
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }

    }

    fun getOnSuccessReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkingTaskComplete && it.task.id == intent.requestId }
                        ?.let { it as? TaskItemState.MarkingTaskComplete }
                        ?.let { task ->
                            state.copy(tasks = ((state.tasks - task) + TaskItemState.CompletedTask(task.task.copy(isComplete = true), TaskListIntent.MarkTaskIncomplete(task
                                    .task
                                    .id))))
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }

    fun getOnErrorReducer(error: Throwable): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkingTaskComplete && it.task.id == intent.requestId }
                        ?.let { it as? TaskItemState.MarkingTaskComplete }
                        ?.let { task ->
                            state.copy(
                                    tasks =
                                    ((state.tasks - task) + TaskItemState.MarkTaskCompleteFailure(task.task.copy(isComplete = false), task
                                            .markCompleteIntent, error, TaskListIntent.RetryMarkTaskComplete(task.task), TaskListIntent.ClearFailedMarkCompleteRequest(task.task.id)))
                            )
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }

    return markTaskCompleteBuilder(intent.requestId).toSingle { getOnSuccessReducer() }.onErrorReturn { getOnErrorReducer(it) }.toObservable().startWith(getOnSubmitReducer())

}

private fun getMarkTaskIncompleteReducer(intent: TaskListIntent.MarkTaskIncomplete, markTaskIncompleteBuilder: MarkTaskIncompleteBuilder): Observable<TaskListStateReducer> {
    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.CompletedTask && it.task.id == intent.requestId }
                        ?.let { it as? TaskItemState.CompletedTask }
                        ?.let { task ->
                            state.copy(tasks = ((state.tasks - task) + TaskItemState.MarkingTaskIncomplete(task.task.copy(isComplete = false), task.markIncompleteIntent)))
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }

    }

    fun getOnSuccessReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkingTaskIncomplete && it.task.id == intent.requestId }
                        ?.let { it as? TaskItemState.MarkingTaskIncomplete }
                        ?.let { task ->
                            state.copy(tasks = ((state.tasks - task) + TaskItemState.IncompleteTask(task.task.copy(isComplete = false), TaskListIntent.MarkTaskComplete(task.task.id))))
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }

    fun getOnErrorReducer(error: Throwable): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkingTaskIncomplete && it.task.id == intent.requestId }
                        ?.let { it as? TaskItemState.MarkingTaskIncomplete }
                        ?.let { task ->
                            state.copy(
                                    tasks =
                                    ((state.tasks - task) + TaskItemState.MarkTaskIncompleteFailure(task.task.copy(isComplete = true), task.markIncompleteIntent, error,
                                            TaskListIntent.RetryMarkTaskIncomplete(task.task), TaskListIntent.ClearFailedMarkIncompleteRequest(task.task.id)))
                            )
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }


    return markTaskIncompleteBuilder(intent.requestId).toSingle { getOnSuccessReducer() }.onErrorReturn { getOnErrorReducer(it) }.toObservable().startWith(getOnSubmitReducer())
}

private fun getRetryAddRequestReducer(intent: TaskListIntent.RetryAddTaskRequest, addRequestBuilder: AddTaskRequestBuilder): Observable<TaskListStateReducer> {

    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.AddTaskFailure && it.retryIntent.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.AddTaskFailure }
                        ?.let { taskItemState ->
                            val task = Task(taskItemState.intent.requestId, taskItemState.intent.title, false)

                            state.copy(tasks = (state.tasks - taskItemState) + TaskItemState.PendingTask(task, TaskListIntent.AddTaskRequest(task.title, task.id)))
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    fun getOnSuccessReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.PendingTask && it.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.PendingTask }
                        ?.let { taskItemState ->

                            state.copy(tasks = (state.tasks - taskItemState) + TaskItemState.IncompleteTask(taskItemState.task.copy(isComplete = false),
                                    TaskListIntent.MarkTaskComplete(taskItemState.task.id)))
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    fun getOnErrorReducer(error: Throwable): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.PendingTask && it.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.PendingTask }
                        ?.let { taskItemState ->

                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.AddTaskFailure(taskItemState.intent, error, TaskListIntent.RetryAddTaskRequest(intent.task), TaskListIntent.ClearFailedAddTaskRequest(intent.task.id))
                            )
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    return addRequestBuilder(intent.task).toSingle { getOnSuccessReducer() }.onErrorReturn { getOnErrorReducer(it) }.toObservable().startWith(getOnSubmitReducer())
}

private fun getRetryMarkTaskIncompleteReducer(intent: TaskListIntent.RetryMarkTaskIncomplete, markTaskIncompleteBuilder: MarkTaskIncompleteBuilder): Observable<TaskListStateReducer> {

    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkTaskIncompleteFailure && it.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.MarkTaskIncompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.MarkingTaskIncomplete(taskItemState.task, TaskListIntent.MarkTaskIncomplete(intent.task.id))
                            )
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    fun getOnSuccessReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkingTaskIncomplete && it.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.MarkTaskIncompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.IncompleteTask(taskItemState.task.copy(isComplete = false),
                                            TaskListIntent.MarkTaskComplete(intent.task.id))
                            )
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    fun getOnErrorReducer(error: Throwable): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkingTaskIncomplete && it.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.MarkTaskIncompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.MarkTaskIncompleteFailure(taskItemState.task.copy(isComplete = false),
                                            TaskListIntent.MarkTaskIncomplete(intent.task.id), error, intent, TaskListIntent.ClearFailedMarkIncompleteRequest(intent.task.id)
                                    )
                            )
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    return markTaskIncompleteBuilder(intent.task.id).toSingle { getOnSuccessReducer() }.onErrorReturn { getOnErrorReducer(it) }.toObservable().startWith(getOnSubmitReducer())
}

private fun getRetryMarkTaskCompleteReducer(intent: TaskListIntent.RetryMarkTaskComplete, markTaskCompleteBuilder: MarkTaskCompleteBuilder): Observable<TaskListStateReducer> {

    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkTaskCompleteFailure && it.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.MarkTaskCompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.MarkingTaskComplete(taskItemState.task, TaskListIntent.MarkTaskComplete(intent.task.id))
                            )
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    fun getOnSuccessReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkingTaskComplete && it.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.MarkingTaskComplete }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.CompletedTask(taskItemState.task.copy(isComplete = false),
                                            TaskListIntent.MarkTaskIncomplete
                                            (intent.task.id))
                            )
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    fun getOnErrorReducer(error: Throwable): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkingTaskComplete && it.task.id == intent.task.id }
                        ?.let { it as? TaskItemState.MarkingTaskComplete }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.MarkTaskCompleteFailure(taskItemState.task.copy(isComplete = false),
                                            TaskListIntent.MarkTaskComplete(intent.task.id), error, intent, TaskListIntent.ClearFailedMarkCompleteRequest(intent.task.id)
                                    )
                            )
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    return markTaskCompleteBuilder(intent.task.id).toSingle { getOnSuccessReducer() }.onErrorReturn { getOnErrorReducer(it) }.toObservable().startWith(getOnSubmitReducer())
}

private fun getClearFailedMarkIncompleteReducer(intent: TaskListIntent.ClearFailedMarkIncompleteRequest): Observable<TaskListStateReducer> {
    return { state: TaskListState ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks.firstOrNull { it is TaskItemState.MarkTaskIncompleteFailure && it.request.requestId == intent.requestId }
                        ?.let { it as? TaskItemState.MarkTaskIncompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(tasks = (state.tasks - taskItemState) + TaskItemState.CompletedTask(taskItemState.task.copy(isComplete = true),
                                    TaskListIntent.MarkTaskIncomplete(taskItemState.task.id)))
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }.just()
}


private fun getClearFailedMarkCompleteReducer(intent: TaskListIntent.ClearFailedMarkCompleteRequest): Observable<TaskListStateReducer> {
    return { state: TaskListState ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks.firstOrNull { it is TaskItemState.MarkTaskCompleteFailure && it.request.requestId == intent.requestId }
                        ?.let { it as? TaskItemState.MarkTaskCompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) +
                                            TaskItemState.IncompleteTask(
                                                    taskItemState.task.copy(isComplete = false),
                                                    TaskListIntent.MarkTaskComplete(taskItemState.task.id)
                                            )
                            )
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }.just()

}

private fun getClearFailedAddTaskReducer(intent: TaskListIntent.ClearFailedAddTaskRequest): Observable<TaskListStateReducer> {
    return { state: TaskListState ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks.firstOrNull { it is TaskItemState.AddTaskFailure && it.intent.requestId == intent.requestId }
                        ?.let { it as? TaskItemState.AddTaskFailure }
                        ?.let { taskItemState ->
                            state.copy(tasks = state.tasks - taskItemState)
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }.just()

}


private fun Task.toTaskItemState(): TaskItemState =
        if (isComplete) TaskItemState.CompletedTask(this, TaskListIntent.MarkTaskIncomplete(this.id))
        else TaskItemState.IncompleteTask(this, TaskListIntent.MarkTaskComplete(this.id))


typealias TaskListStateReducer = (TaskListState) -> TaskListState
