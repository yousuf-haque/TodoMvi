package com.yohaq.todomvi.depdendencyInjection

import com.yohaq.todomvi.presentation.taskList.TaskListComponent
import dagger.Subcomponent

/**
 * Created by yousufhaque on 9/17/17.
 */
@Subcomponent
interface UiComponent{
    val taskListComponent: TaskListComponent
}