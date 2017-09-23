package com.yohaq.todomvi.extensions

import android.view.View

/**
 * Created by yousufhaque on 9/20/17.
 */
var View.isVisible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }