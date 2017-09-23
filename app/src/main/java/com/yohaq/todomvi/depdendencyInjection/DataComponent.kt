package com.yohaq.todomvi.depdendencyInjection

import com.jakewharton.rxrelay2.BehaviorRelay
import com.yohaq.todomvi.data.Task
import dagger.Component
import dagger.Module
import dagger.Provides
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by yousufhaque on 9/17/17.
 */
@Component(
        modules = arrayOf(TasksModule::class),
        dependencies = arrayOf(ApplicationComponent::class)
)
interface DataComponent {
    val taskApi: TasksModule.TaskApi
    fun getUiComponent(): UiComponent
}


@Module
class TasksModule {

    data class TaskApi(
            val addTaskRequestBuilder: AddTaskRequestBuilder,
            val taskListStream: Single<List<Task>>,
            val markTaskCompleteBuilder: MarkTaskCompleteBuilder,
            val markTaskIncompleteBuilder: MarkTaskIncompleteBuilder
    )

    @Provides
    fun provideApi(): TaskApi =
            TaskApi(addTaskRequestBuilder = addTaskRequest,
                    taskListStream = taskRelay.delay (2, TimeUnit.SECONDS).map { if(randomBoolean()) throw RuntimeException("unable to get tasks") else it }
                            .firstOrError(),
                    markTaskCompleteBuilder = markTaskCompleteRequest,
                    markTaskIncompleteBuilder = markTaskIncompleteRequest

            )

    private val taskRelay: BehaviorRelay<List<Task>> by lazy {
        BehaviorRelay.createDefault(taskList)
    }

    private val addTaskRequest = { task: Task ->
        if (randomBoolean()) {
            Unit.toSingle().delay(2, TimeUnit.SECONDS).map { throw RuntimeException("pick a better task") }.toCompletable()
        } else {
            Completable.complete().delay(2, TimeUnit.SECONDS).doOnComplete { taskRelay.accept(taskRelay.value + task) }
        }
    }

    private val markTaskCompleteRequest = { taskId: UUID ->
        if (randomBoolean()) {
            Unit.toSingle().delay(2, TimeUnit.SECONDS).map { throw RuntimeException("Can't complete this task") }.toCompletable()
        } else {
            Completable.complete().delay(2, TimeUnit.SECONDS).doOnComplete {
                taskRelay.value.firstOrNull { it.id == taskId }
                        ?.let { (taskRelay.value - it) + it.copy(isComplete = true) }
                        ?.also { taskRelay.accept(it) }
            }
        }
    }

    private val markTaskIncompleteRequest = { taskId: UUID ->
        if (randomBoolean()) {
            Unit.toSingle().delay(2, TimeUnit.SECONDS).map { throw RuntimeException("Can't mark this task incomplete") }.toCompletable()
        } else {
            Completable.complete().delay(2, TimeUnit.SECONDS).doOnComplete {
                taskRelay.value.firstOrNull { it.id == taskId }?.let {
                    (taskRelay.value - it) + it.copy(isComplete = false)
                }.also { taskRelay.accept(it) }
            }
        }
    }

    private val taskList = listOf(
            Task(UUID.randomUUID(), "do talk", false),
            Task(UUID.randomUUID(), "do talk", true),
            Task(UUID.randomUUID(), "do talk", true),
            Task(UUID.randomUUID(), "do talk", false),
            Task(UUID.randomUUID(), "do talk", true),
            Task(UUID.randomUUID(), "do talk", false)
    )

    private fun randomBoolean(): Boolean =
            Random().nextInt(10) <= 3

}
typealias AddTaskRequestBuilder = (Task) -> Completable
typealias MarkTaskCompleteBuilder = (UUID) -> Completable
typealias MarkTaskIncompleteBuilder = (UUID) -> Completable




