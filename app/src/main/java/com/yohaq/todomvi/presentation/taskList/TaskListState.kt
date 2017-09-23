package com.yohaq.todomvi.presentation.taskList

import com.yohaq.todomvi.data.Task

/**
 * Created by yousufhaque on 9/22/17.
 */


sealed class TaskListState {
    object LoadingTasks : TaskListState() {
        override fun toString() = LoadingTasks::class.java.simpleName!!
    }

    object ErrorLoadingTasks : TaskListState() {
        override fun toString() = ErrorLoadingTasks::class.java.simpleName!!
    }

    data class TasksLoaded(
            val tasks: List<TaskItemState>,
            val isTaskDraftValid: Boolean
    ) : TaskListState()

}

sealed class TaskItemState {
    data class PendingTask(val task: Task, val intent: TaskListIntent.AddTaskRequest) : TaskItemState()
    data class MarkingTaskComplete(val task: Task, val markCompleteIntent: TaskListIntent.MarkTaskComplete) : TaskItemState()
    data class MarkingTaskIncomplete(val task: Task, val markIncompleteIntent: TaskListIntent.MarkTaskIncomplete) : TaskItemState()
    data class CompletedTask(val task: Task, val markIncompleteIntent: TaskListIntent.MarkTaskIncomplete) : TaskItemState()
    data class IncompleteTask(val task: Task, val markTaskCompleteIntent: TaskListIntent.MarkTaskComplete) : TaskItemState()
    data class AddTaskFailure(
            val intent: TaskListIntent.AddTaskRequest,
            val error: Throwable,
            val retryIntent: TaskListIntent.RetryAddTaskRequest,
            val clearIntent: TaskListIntent.ClearFailedAddTaskRequest
    ) : TaskItemState()

    data class MarkTaskCompleteFailure(
            val task: Task,
            val request: TaskListIntent.MarkTaskComplete,
            val error: Throwable,
            val retryIntent: TaskListIntent.RetryMarkTaskComplete,
            val clearIntent: TaskListIntent.ClearFailedMarkCompleteRequest
    ) : TaskItemState()

    data class MarkTaskIncompleteFailure(
            val task: Task,
            val request: TaskListIntent.MarkTaskIncomplete,
            val error: Throwable,
            val retryIntent: TaskListIntent.RetryMarkTaskIncomplete,
            val clearIntent: TaskListIntent.ClearFailedMarkIncompleteRequest
    ) : TaskItemState()
}
