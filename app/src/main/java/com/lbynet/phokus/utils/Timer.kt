package com.lbynet.phokus.utils

import kotlin.jvm.JvmOverloads

class Timer @JvmOverloads constructor(private val name: String = "DEFAULT TIMER") {

    private var startTime: Long = 0
    private var endTime: Long = 0
    var isBusy = false
        private set

    fun start() {
        startTime = System.currentTimeMillis()
        isBusy = true
    }

    fun stop() {
        endTime = System.currentTimeMillis()
        isBusy = false
    }

    val elaspedTimeInMs: Long
        get() {
            if (isBusy) endTime = System.currentTimeMillis()
            return endTime - startTime
        }

    fun zero() {
        endTime = System.currentTimeMillis()
        startTime = System.currentTimeMillis()
    }

    override fun toString(): String {
        if (isBusy) {
            stop()
        }
        return "[" + name + "] Time elapsed: " + elaspedTimeInMs + "ms"
    }

    init {
        startTime = System.currentTimeMillis()
        isBusy = true
    }
}