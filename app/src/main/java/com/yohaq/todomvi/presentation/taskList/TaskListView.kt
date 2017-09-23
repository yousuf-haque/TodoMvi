package com.yohaq.todomvi.presentation.taskList

import android.util.Log


/**
 * Created by yousufhaque on 9/17/17.
 */
fun TaskListState.render(
        errorAddingTaskString: String,
        errorMarkingTaskCompleteString: String,
        errorMarkingTaskIncompleteString: String
): TaskListViewState =
        when (this) {
            TaskListState.LoadingTasks ->
                TaskListViewState(
                        isTaskListVisible = false,
                        isProgressBarVisible = true,
                        tasks = emptyList(),
                        isComposeAreaVisible = false,
                        isAddButtonVisible = false,
                        isRefreshVisible = false
                )
            TaskListState.ErrorLoadingTasks -> TaskListViewState(
                    isTaskListVisible = false,
                    isProgressBarVisible = false,
                    tasks = emptyList(),
                    isErrorMessageVisible = true,
                    isComposeAreaVisible = false,
                    isAddButtonVisible = false,
                    isRefreshVisible = true

            )
            is TaskListState.TasksLoaded -> TaskListViewState(
                    tasks =
                    tasks.map {
                        when(it){
                            is TaskItemState.CompletedTask -> TaskItemViewState(
                                    isComplete = it.task.isComplete,
                                    isCheckEnabled = true,
                                    markTaskIncompleteIntent = it.markIncompleteIntent,
                                    isProgressSpinnerVisible = false,
                                    isTitleEnabled = true,
                                    title = it.task.title
                            )
                            is TaskItemState.IncompleteTask -> TaskItemViewState(
                                    isComplete = it.task.isComplete,
                                    isCheckEnabled = true,
                                    markTaskCompleteIntent = it.markTaskCompleteIntent,
                                    isProgressSpinnerVisible = false,
                                    isTitleEnabled = true,
                                    title = it.task.title
                            )
                            is TaskItemState.PendingTask -> TaskItemViewState(
                                    isComplete = false,
                                    isCheckEnabled = false,
                                    isTitleEnabled = false,
                                    title = it.task.title,
                                    isProgressSpinnerVisible = true
                            )
                            is TaskItemState.AddTaskFailure -> TaskItemViewState(
                                    isComplete = false,
                                    isCheckEnabled = false,
                                    isProgressSpinnerVisible = false,
                                    isTitleEnabled = false,
                                    title = it.intent.title,
                                    showError = true,
                                    errorMessage = errorAddingTaskString,
                                    showRetryButton = true,
                                    showCancelButton = true,
                                    retryIntent = it.retryIntent,
                                    clearIntent = it.clearIntent
                            )
                            is TaskItemState.MarkTaskCompleteFailure -> TaskItemViewState(
                                    isComplete = false,
                                    isCheckEnabled = false,
                                    isProgressSpinnerVisible = false,
                                    isTitleEnabled = false,
                                    title = it.task.title,
                                    showError = true,
                                    errorMessage = errorMarkingTaskCompleteString,
                                    showRetryButton = true,
                                    showCancelButton = true,
                                    retryIntent = it.retryIntent,
                                    clearIntent = it.clearIntent
                            )
                            is TaskItemState.MarkTaskIncompleteFailure -> TaskItemViewState(
                                    isComplete = true,
                                    isCheckEnabled = false,
                                    isProgressSpinnerVisible = false,
                                    isTitleEnabled = false,
                                    title = it.task.title,
                                    showError = true,
                                    errorMessage = errorMarkingTaskIncompleteString,
                                    showRetryButton = true,
                                    showCancelButton = true,
                                    retryIntent = it.retryIntent,
                                    clearIntent = it.clearIntent
                            )
                            is TaskItemState.MarkingTaskComplete -> TaskItemViewState(
                                    isComplete = true,
                                    isCheckEnabled = false,
                                    isTitleEnabled = false,
                                    title = it.task.title,
                                    isProgressSpinnerVisible = true
                            )
                            is TaskItemState.MarkingTaskIncomplete -> TaskItemViewState(
                                    isComplete = false,
                                    isCheckEnabled = false,
                                    isTitleEnabled = false,
                                    title = it.task.title,
                                    isProgressSpinnerVisible = true
                            )
                        }
                    },
                    isProgressBarVisible = false,
                    isTaskListVisible = true,
                    isComposeAreaVisible = true,
                    isAddButtonVisible = isTaskDraftValid,
                    isRefreshVisible = true
            )
        }.also { Log.d("TaskListView", it.toString())}


data class TaskListViewState(
        val isTaskListVisible: Boolean,
        val isProgressBarVisible: Boolean,
        val isComposeAreaVisible: Boolean,
        val isAddButtonVisible: Boolean,
        val isRefreshVisible: Boolean,
        val isErrorMessageVisible: Boolean = false,
        val tasks: List<TaskItemViewState>
)

data class TaskItemViewState(
        val isComplete: Boolean,
        val isCheckEnabled: Boolean,
        val title: String,
        val isTitleEnabled: Boolean,
        val isProgressSpinnerVisible: Boolean,
        val markTaskCompleteIntent: TaskListIntent.MarkTaskComplete? = null,
        val markTaskIncompleteIntent: TaskListIntent.MarkTaskIncomplete? = null,
        val retryIntent: TaskListIntent? = null,
        val clearIntent: TaskListIntent? = null,
        val showError: Boolean = false,
        val errorMessage: String = "",
        val showRetryButton: Boolean = false,
        val showCancelButton: Boolean = false
)
