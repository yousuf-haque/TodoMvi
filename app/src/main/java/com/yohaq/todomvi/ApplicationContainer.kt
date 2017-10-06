package com.yohaq.todomvi

import android.app.Activity
import android.content.Context
import com.yohaq.todomvi.data.Task
import com.yohaq.todomvi.depdendencyInjection.AddTaskRequestBuilder
import com.yohaq.todomvi.depdendencyInjection.MarkTaskCompleteBuilder
import com.yohaq.todomvi.depdendencyInjection.MarkTaskIncompleteBuilder
import com.yohaq.todomvi.depdendencyInjection.TaskLibrary
import com.yohaq.todomvi.presentation.taskList.dialog.TaskListIntent
import com.yohaq.todomvi.presentation.taskList.dialog.TaskListViewState
import com.yohaq.todomvi.presentation.taskList.dialog.buildTaskListStateStream
import com.yohaq.todomvi.presentation.taskList.dialog.render
import io.reactivex.Observable
import io.reactivex.Single

/**
 * Created by yousufhaque on 9/17/17.
 */

object ApplicationContainer {

    private var appComponent: ApplicationComponent? = null
    private var dataComponent: DataComponent? = null
    private var uiComponent: UiComponent? = null

    private fun Single<Activity>.applicationComponent(): Single<ApplicationComponent> =
            this
                    .map(Activity::getApplicationContext)
                    .map { appComponent ?: ApplicationComponent(it).also { appComponent = it } }

    private fun Single<ApplicationComponent>.dataComponent(): Single<DataComponent> =
            this
                    .map {
                        dataComponent ?: DataComponent(
                                TaskLibrary(),
                                it.applicationContext
                        ).also { dataComponent = it }
                    }

    private fun Single<DataComponent>.uiComponent(): Single<UiComponent> =
            this
                    .map { (taskApi, context) ->
                        uiComponent ?: UiComponent(
                                taskApi.taskListStream,
                                taskApi::addTaskRequest,
                                taskApi::markTaskCompleteRequest,
                                taskApi::markTaskIncompleteRequest,
                                context
                        ).also { uiComponent = it }
                    }

    private fun Single<UiComponent>.taskListComponent(): Single<TaskListComponent> =
            this.map(UiComponent::taskListComponent)

    fun buildTaskListComponent(activitySingle: Single<Activity>): Single<TaskListComponent> =
            activitySingle.applicationComponent().dataComponent().uiComponent().taskListComponent()

}

private data class ApplicationComponent(val applicationContext: Context)
private data class DataComponent(val taskApi: TaskLibrary, val applicationContext: Context)
data class UiComponent(
        private val taskListSingle: Single<List<Task>>,
        private val addTaskRequestBuilder: AddTaskRequestBuilder,
        private val markTaskCompleteBuilder: MarkTaskCompleteBuilder,
        private val markTaskIncompleteBuilder: MarkTaskIncompleteBuilder,
        private val context: Context
) {
    val taskListComponent: TaskListComponent =
            TaskListComponent(
                    taskListDialog = { intents: Observable<TaskListIntent> ->
                        buildTaskListStateStream(
                                intentStream = intents,
                                taskListStream = taskListSingle,
                                addRequestBuilder = addTaskRequestBuilder,
                                markTaskCompleteBuilder = markTaskCompleteBuilder,
                                markTaskIncompleteBuilder = markTaskIncompleteBuilder
                        ).map { taskListState ->
                            taskListState.render(
                                    errorAddingTaskString = context.getString(R.string.error_adding_task),
                                    errorMarkingTaskCompleteString = context.getString(R.string.error_marking_task_complete),
                                    errorMarkingTaskIncompleteString = context.getString(R.string.error_marking_task_incomplete)

                            )
                        }
                    }
            )
}

data class TaskListComponent(val taskListDialog: TaskListDialog)

typealias TaskListDialog = (Observable<TaskListIntent>) -> Observable<TaskListViewState>