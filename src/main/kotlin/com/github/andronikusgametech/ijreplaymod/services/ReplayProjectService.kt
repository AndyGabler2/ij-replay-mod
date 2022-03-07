package com.github.andronikusgametech.ijreplaymod.services

import com.intellij.openapi.project.Project
import com.github.andronikusgametech.ijreplaymod.model.CodingReplayState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.slf4j.LoggerFactory

@State(name = "codingReplayState")
@Storage("codingreplay.xml")
class ReplayProjectService(
    private val project: Project
) : PersistentStateComponent<CodingReplayState> {

    private val logger = LoggerFactory.getLogger("ReplayProjectService")
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
}
