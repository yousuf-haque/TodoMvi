package com.yohaq.todomvi.presentation.taskList

import android.util.Log
import com.yohaq.todomvi.data.Task
import com.yohaq.todomvi.depdendencyInjection.AddTaskRequestBuilder
import com.yohaq.todomvi.depdendencyInjection.MarkTaskCompleteBuilder
import com.yohaq.todomvi.depdendencyInjection.MarkTaskIncompleteBuilder
import com.yohaq.todomvi.extensions.just
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single

/**
 * Created by yousufhaque on 9/17/17.
 */

fun taskListModel(intentStream: Observable<TaskListIntent>, taskListStream: Single<List<Task>>, addRequestBuilder: AddTaskRequestBuilder,
                  markTaskCompleteBuilder: MarkTaskCompleteBuilder, markTaskIncompleteBuilder: MarkTaskIncompleteBuilder): Observable<out
TaskListState> {
    val TAG = "TaskListModel"


    val intentStateReducers: Observable<TaskListStateReducer> = intentStream.flatMap { intent ->
        when (intent) {
            TaskListIntent.RetryLoadingTasks -> getLoadTasksReducer(taskListStream)
            is TaskListIntent.UpdateTaskDraft -> updateTaskDraftReducer(intent)
            is TaskListIntent.AddTaskRequest -> addTasksReducer(intent, addRequestBuilder)
            is TaskListIntent.MarkTaskComplete -> markTaskCompleteReducer(intent, markTaskCompleteBuilder)
            is TaskListIntent.MarkTaskIncomplete -> markTaskIncompleteReducer(intent, markTaskIncompleteBuilder)
            is TaskListIntent.RetryAddTaskRequest -> retryAddRequestReducer(intent, addRequestBuilder)
            is TaskListIntent.RetryMarkTaskComplete -> retryMarkTaskCompleteReducer(intent, markTaskCompleteBuilder)
            is TaskListIntent.RetryMarkTaskIncomplete -> retryMarkTaskIncompleteReducer(intent, markTaskIncompleteBuilder)
            is TaskListIntent.ClearFailedMarkIncompleteRequest -> clearFailedMarkIncompleteReducer(intent)
            is TaskListIntent.ClearFailedMarkCompleteRequest -> clearFailedMarkCompleteReducer(intent)
            is TaskListIntent.ClearFailedAddTaskRequest -> clearFailedAddTaskReducer(intent)
        }
    }



    return getLoadTasksReducer(taskListStream).mergeWith(intentStateReducers).scan(TaskListState.LoadingTasks as TaskListState,
            { oldState, reduce ->
                reduce(oldState).also {
                    Log.d(TAG, "oldState:\n\t$oldState")
                    Log.d(TAG, "newState:\n\t$it")
                }
            }
    )
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


private fun updateTaskDraftReducer(intent: TaskListIntent.UpdateTaskDraft): Observable<TaskListStateReducer> {
    return { state: TaskListState ->
        when (state) {
            is TaskListState.TasksLoaded -> state.copy(isTaskDraftValid = intent.content.isNotBlank())
            else -> state
        }
    }.just()
}

private fun addTasksReducer(intent: TaskListIntent.AddTaskRequest, addRequestBuilder: (Task) -> Completable): Observable<TaskListStateReducer> {

    val task = Task(intent.requestId, intent.title, false)
    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> state.copy(tasks = state.tasks + TaskItemState.PendingTask(task, intent))
            else -> state
        }
    }

    fun getOnSuccess(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> state.copy(tasks = (state.tasks - TaskItemState.PendingTask(task, intent)) + TaskItemState.IncompleteTask(task,
                    TaskListIntent.MarkTaskComplete(task.id)))
            else -> state
        }
    }

    fun getOnErrorReducer(error: Throwable): TaskListStateReducer = { state ->
        Log.e("TaskListView", "add task failure", error)
        when (state) {
            is TaskListState.TasksLoaded -> state.copy(tasks = (state.tasks - TaskItemState.PendingTask(task, intent)) + TaskItemState.AddTaskFailure(intent,
                    Exception(error),
                    TaskListIntent.RetryAddTaskRequest(Task(intent.requestId, intent.title, false)),
                    TaskListIntent.ClearFailedAddTaskRequest(intent.requestId)))
            else -> state
        }
    }

    return addRequestBuilder(task).toSingle { getOnSuccess() }.onErrorReturn { getOnErrorReducer(it) }.toObservable().startWith(getOnSubmitReducer())
}

private fun markTaskCompleteReducer(intent: TaskListIntent.MarkTaskComplete, markTaskCompleteBuilder: MarkTaskCompleteBuilder): Observable<TaskListStateReducer> {
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
                            state.copy(tasks = ((state.tasks - task) + TaskItemState.CompletedTask(task.task.copy(isComplete = true), TaskListIntent
                                    .MarkTaskIncomplete(task
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
                                            .markCompleteIntent, error, TaskListIntent.RetryMarkTaskComplete(task.task), TaskListIntent
                                            .ClearFailedMarkCompleteRequest(task.task.id)))
                            )
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }

    return markTaskCompleteBuilder(intent.requestId).toSingle { getOnSuccessReducer() }.onErrorReturn { getOnErrorReducer(it) }.toObservable().startWith(getOnSubmitReducer())

}

private fun markTaskIncompleteReducer(intent: TaskListIntent.MarkTaskIncomplete, markTaskIncompleteBuilder: MarkTaskIncompleteBuilder): Observable<TaskListStateReducer> {
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

private fun retryAddRequestReducer(intent: TaskListIntent.RetryAddTaskRequest, addRequestBuilder: AddTaskRequestBuilder): Observable<TaskListStateReducer> {

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
                                    tasks = (state.tasks - taskItemState) + TaskItemState.AddTaskFailure(taskItemState.intent, error, TaskListIntent
                                            .RetryAddTaskRequest(intent.task), TaskListIntent.ClearFailedAddTaskRequest(intent.task.id))
                            )
                        }
                        ?.let { it as TaskListState } ?: state
            }
            else -> state
        }
    }

    return addRequestBuilder(intent.task).toSingle { getOnSuccessReducer() }.onErrorReturn { getOnErrorReducer(it) }.toObservable().startWith(getOnSubmitReducer())
}

private fun retryMarkTaskIncompleteReducer(intent: TaskListIntent.RetryMarkTaskIncomplete, markTaskIncompleteBuilder: MarkTaskIncompleteBuilder): Observable<TaskListStateReducer> {

    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkTaskIncompleteFailure && it.task.id == intent.task.id}
                        ?.let { it as? TaskItemState.MarkTaskIncompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.MarkingTaskIncomplete(taskItemState.task, TaskListIntent
                                            .MarkTaskIncomplete(intent.task.id))
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
                        .firstOrNull { it is TaskItemState.MarkingTaskIncomplete && it.task.id == intent.task.id}
                        ?.let { it as? TaskItemState.MarkTaskIncompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.IncompleteTask(taskItemState.task.copy(isComplete = false),
                                            TaskListIntent
                                            .MarkTaskComplete
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
                        .firstOrNull { it is TaskItemState.MarkingTaskIncomplete && it.task.id == intent.task.id}
                        ?.let { it as? TaskItemState.MarkTaskIncompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.MarkTaskIncompleteFailure(taskItemState.task.copy(isComplete = false),
                                            TaskListIntent.MarkTaskIncomplete(intent.task.id), error, intent, TaskListIntent
                                            .ClearFailedMarkIncompleteRequest(intent.task.id)
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

private fun retryMarkTaskCompleteReducer(intent: TaskListIntent.RetryMarkTaskComplete, markTaskCompleteBuilder: MarkTaskCompleteBuilder): Observable<TaskListStateReducer> {

    fun getOnSubmitReducer(): TaskListStateReducer = { state ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks
                        .firstOrNull { it is TaskItemState.MarkTaskCompleteFailure && it.task.id == intent.task.id}
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
                        .firstOrNull { it is TaskItemState.MarkingTaskComplete && it.task.id == intent.task.id}
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
                        .firstOrNull { it is TaskItemState.MarkingTaskComplete && it.task.id == intent.task.id}
                        ?.let { it as? TaskItemState.MarkingTaskComplete }
                        ?.let { taskItemState ->
                            state.copy(
                                    tasks = (state.tasks - taskItemState) + TaskItemState.MarkTaskCompleteFailure(taskItemState.task.copy(isComplete = false),
                                            TaskListIntent.MarkTaskComplete(intent.task.id), error, intent, TaskListIntent
                                            .ClearFailedMarkCompleteRequest(intent.task.id)
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

private fun clearFailedMarkIncompleteReducer(intent: TaskListIntent.ClearFailedMarkIncompleteRequest): Observable<TaskListStateReducer> {
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


private fun clearFailedMarkCompleteReducer(intent: TaskListIntent.ClearFailedMarkCompleteRequest): Observable<TaskListStateReducer> {
    return { state: TaskListState ->
        when (state) {
            is TaskListState.TasksLoaded -> {
                state.tasks.firstOrNull { it is TaskItemState.MarkTaskCompleteFailure && it.request.requestId == intent.requestId }
                        ?.let { it as? TaskItemState.MarkTaskCompleteFailure }
                        ?.let { taskItemState ->
                            state.copy(tasks = (state.tasks - taskItemState) + TaskItemState.IncompleteTask(taskItemState.task.copy(isComplete = false), TaskListIntent.MarkTaskComplete(taskItemState.task.id)))
                        }?.let { it as TaskListState } ?: state

            }
            else -> state

        }
    }.just()

}

private fun clearFailedAddTaskReducer(intent: TaskListIntent.ClearFailedAddTaskRequest): Observable<TaskListStateReducer> {
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


private fun Task.toTaskItemState(): TaskItemState = TaskItemState.CompletedTask(this, TaskListIntent.MarkTaskIncomplete(this.id))


typealias TaskListStateReducer = (TaskListState) -> TaskListState
