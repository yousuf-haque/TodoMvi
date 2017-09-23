package com.yohaq.todomvi.presentation.taskList

import com.yohaq.todomvi.data.Task
import java.util.*

/**
 * Created by yousufhaque on 9/17/17.
 */
sealed class TaskListIntent {

    object RetryLoadingTasks : TaskListIntent()

    data class UpdateTaskDraft(val content: String) : TaskListIntent()

    data class AddTaskRequest(val title: String, val requestId: UUID = UUID.randomUUID()) : TaskListIntent()
    data class MarkTaskComplete(val requestId: UUID) : TaskListIntent()
    data class MarkTaskIncomplete(val requestId: UUID) : TaskListIntent()

    data class RetryAddTaskRequest(val task: Task) : TaskListIntent()
    data class RetryMarkTaskComplete(val task: Task) : TaskListIntent()
    data class RetryMarkTaskIncomplete(val task: Task) : TaskListIntent()


    data class ClearFailedMarkIncompleteRequest(val requestId: UUID) : TaskListIntent()
    data class ClearFailedMarkCompleteRequest(val requestId: UUID) : TaskListIntent()
    data class ClearFailedAddTaskRequest(val requestId: UUID) : TaskListIntent()

}