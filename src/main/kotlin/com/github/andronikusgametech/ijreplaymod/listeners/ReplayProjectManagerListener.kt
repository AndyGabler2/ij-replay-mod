package com.github.andronikusgametech.ijreplaymod.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.github.andronikusgametech.ijreplaymod.services.ReplayProjectService

internal class ReplayProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        project.service<ReplayProjectService>()
    }
}
