package com.github.andronikusgametech.ijreplaymod.services

import com.intellij.openapi.project.Project
import com.github.andronikusgametech.ijreplaymod.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
