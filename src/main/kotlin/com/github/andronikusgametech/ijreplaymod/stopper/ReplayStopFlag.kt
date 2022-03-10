package com.github.andronikusgametech.ijreplaymod.stopper

open class ReplayStopFlag {

    private var stopped = false

    fun stop() {
        stopped = true
    }

    fun isStopped() = stopped
}