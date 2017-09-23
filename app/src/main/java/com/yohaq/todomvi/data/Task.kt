package com.yohaq.todomvi.data

import java.util.*

data class Task(
        val id: UUID,
        val title: String,
        val isComplete: Boolean
)