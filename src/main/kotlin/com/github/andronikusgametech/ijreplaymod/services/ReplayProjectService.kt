package com.github.andronikusgametech.ijreplaymod.services

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.intellij.openapi.project.Project
import com.github.andronikusgametech.ijreplaymod.model.CodingReplayState
import com.github.andronikusgametech.ijreplaymod.replay.ReplayActor
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.slf4j.LoggerFactory

@State(name = "codingReplayState")
@Storage("codingreplay.xml")
class ReplayProjectService(
    project: Project
) : PersistentStateComponent<CodingReplayState> {

    private val logger = LoggerFactory.getLogger("ReplayProjectService")
    private val actors = mutableListOf<ReplayActor>()
    private var state: CodingReplayState? = null
    private val projectName: String = project.name

    init {
        logger.info("Initialized project service for Project \"$projectName\".")
    }

    override fun getState(): CodingReplayState? = state

    override fun loadState(state: CodingReplayState) {
        this.state = state
        logger.info("State loaded for project \"$projectName\".")
    }

    fun setState(state: CodingReplayState) {
        this.state = state
        logger.info("State set for project \"$projectName\".")
    }

    fun registerReplayActor(actor: ReplayActor) {
        actors.add(actor)
    }

    fun stopReplays() {
        if (!actors.any { actor -> !actor.isStopped() }) {
            throw IllegalStateException(CodingReplayBundle.getProperty("cr.ui.stopper.failure"))
        }

        actors.filter { actor -> !actor.isStopped() }.forEach { actor -> actor.stop() }
    }
}
