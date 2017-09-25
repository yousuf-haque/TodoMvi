package com.yohaq.todomvi.presentation.taskList

import android.content.Context
import com.yohaq.todomvi.R
import com.yohaq.todomvi.depdendencyInjection.TasksModule
import com.yohaq.todomvi.presentation.taskList.dialog.*
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import io.reactivex.Observable

/**
 * Created by yousufhaque on 9/17/17.
 */

@Subcomponent(
        modules = arrayOf(TaskListModule::class)
)
interface TaskListComponent {
    val dialog: TaskListDialog
}

@Module
class TaskListModule {

    @Provides
    fun provideTaskListDialog(taskApi: TasksModule.TaskApi, context: Context): TaskListDialog =
            { intents: Observable<TaskListIntent> ->

                val stateStream: Observable<out TaskListState> =
                        taskListModel(
                                intents,
                                taskApi.taskListStream,
                                taskApi.addTaskRequestBuilder,
                                taskApi.markTaskCompleteBuilder,
                                taskApi.markTaskIncompleteBuilder
                        )

                stateStream.map { taskListState ->
                    taskListState.render(
                            errorAddingTaskString = context.getString(R.string.error_adding_task),
                            errorMarkingTaskCompleteString = context.getString(R.string.error_marking_task_complete),
                            errorMarkingTaskIncompleteString = context.getString(R.string.error_marking_task_incomplete)

                    )
                }
            }

}


typealias TaskListDialog = (Observable<TaskListIntent>) -> Observable<TaskListViewState>


