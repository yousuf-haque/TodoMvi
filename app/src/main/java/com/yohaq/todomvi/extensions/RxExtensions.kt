package com.yohaq.todomvi.extensions

import io.reactivex.Observable

/**
 * Created by yousufhaque on 9/10/17.
 */
fun <T> T.just() = Observable.just(this)!!
