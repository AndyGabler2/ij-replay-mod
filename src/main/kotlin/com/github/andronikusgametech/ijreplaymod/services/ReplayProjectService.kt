package com.github.andronikusgametech.ijreplaymod.services

import com.intellij.openapi.project.Project
import com.github.andronikusgametech.ijreplaymod.MyBundle
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframes
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "keyframes")
@Storage("codingreplay.xml")
class ReplayProjectService(project: Project) : PersistentStateComponent<FileKeyframes> {

    private var state: FileKeyframes? = null

    init {
        println(MyBundle.message("projectService", project.name))
    }

    override fun getState(): FileKeyframes? = state

    override fun loadState(state: FileKeyframes) {
        this.state = state
    }

    fun setState(state: FileKeyframes) {
        this.state = state
    }
}
