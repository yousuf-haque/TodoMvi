package com.yohaq.todomvi.depdendencyInjection

import com.jakewharton.rxrelay2.BehaviorRelay
import com.yohaq.todomvi.data.Task
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import java.util.*
import java.util.concurrent.TimeUnit



class TaskLibrary {
    private val taskRelay: BehaviorRelay<List<Task>> by lazy {
        BehaviorRelay.createDefault(taskList)
    }

    val taskListStream: Single<List<Task>> get() = taskRelay.delay (2, TimeUnit.SECONDS).map { if(randomBoolean()) throw RuntimeException("unable to get tasks") else it }
            .firstOrError()

    fun addTaskRequest (task: Task ) =
        if (randomBoolean()) {
            Unit.toSingle().delay(2, TimeUnit.SECONDS).map { throw RuntimeException("pick a better task") }.toCompletable()!!
        } else {
            Completable.complete().delay(2, TimeUnit.SECONDS).doOnComplete { taskRelay.accept(taskRelay.value + task) }!!
        }


    fun markTaskCompleteRequest (taskId: UUID)=
        if (randomBoolean()) {
            Unit.toSingle().delay(2, TimeUnit.SECONDS).map { throw RuntimeException("Can't complete this task") }.toCompletable()!!
        } else {
            Completable.complete().delay(2, TimeUnit.SECONDS).doOnComplete {
                taskRelay.value.firstOrNull { it.id == taskId }
                        ?.let { (taskRelay.value - it) + it.copy(isComplete = true) }
                        ?.also { taskRelay.accept(it) }
            }!!
        }


    fun markTaskIncompleteRequest(taskId: UUID) =
        if (randomBoolean()) {
            Unit.toSingle().delay(2, TimeUnit.SECONDS).map { throw RuntimeException("Can't mark this task incomplete") }.toCompletable()!!
        } else {
            Completable.complete().delay(2, TimeUnit.SECONDS).doOnComplete {
                taskRelay.value.firstOrNull { it.id == taskId }?.let {
                    (taskRelay.value - it) + it.copy(isComplete = false)
                }.also { taskRelay.accept(it) }
            }!!
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


