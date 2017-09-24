package com.yohaq.todomvi.presentation.taskList.adapterItems

import android.util.Log
import android.view.View
import com.xwray.groupie.Item
import com.xwray.groupie.ViewHolder
import com.yohaq.todomvi.R
import com.yohaq.todomvi.presentation.taskList.dialog.TaskItemViewState
import kotlinx.android.synthetic.main.item_task.view.*

/**
 * Created by yousufhaque on 9/18/17.
 */
class TaskItem(
        private val taskItemViewState: TaskItemViewState,
        private val onChecked: () -> Unit,
        private val onUnchecked: () -> Unit,
        private val onRetryClicked: () -> Unit,
        private val onClearClicked: () -> Unit
): Item<ViewHolder>() {
    override fun getLayout(): Int = R.layout.item_task

    override fun bind(viewHolder: ViewHolder, position: Int) {
        viewHolder.root.apply {



            cb_completed.setOnCheckedChangeListener(null)
            cb_completed.isChecked = taskItemViewState.isComplete
            cb_completed.isEnabled = taskItemViewState.isCheckEnabled

            tv_task_title.text = taskItemViewState.title
            tv_task_title.isEnabled = taskItemViewState.isTitleEnabled

            progress_bar.visibility = if(taskItemViewState.isProgressSpinnerVisible) View.VISIBLE else View.GONE

            tv_error_message.visibility = if(taskItemViewState.showError) View.VISIBLE else View.GONE
            tv_error_message.text = taskItemViewState.errorMessage

            iv_cancel.visibility = if(taskItemViewState.showCancelButton) View.VISIBLE else View.GONE
            iv_retry.visibility = if(taskItemViewState.showRetryButton) View.VISIBLE else View.GONE

            iv_cancel.setOnClickListener {
                Log.d("TaskItem", "cancel clicked")
                onClearClicked()
            }
            iv_retry.setOnClickListener {
                Log.d("TaskItem", "retry clicked")
                onRetryClicked()
            }

            cb_completed.setOnCheckedChangeListener { _, isChecked ->
                if(isChecked){
                    onChecked()
                } else {
                    onUnchecked()
                }
            }
        }


    }
}