package com.github.andronikusgametech.ijreplaymod.actions

import com.github.andronikusgametech.ijreplaymod.model.FileKeyframes
import com.github.andronikusgametech.ijreplaymod.services.ReplayProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

abstract class AbstractKeyframeAccessingAction(private val actionLabel: String) : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val state = getOrCreateProjectState(event)
        val currentDocument = currentDocument(event) ?: return
        performAction(event.project!!, state, currentDocument)
    }

    private fun getOrCreateProjectState(event: AnActionEvent): FileKeyframes {
        val project = event.project!!
        val service = project.service<ReplayProjectService>()
        var state = service.state
        if (state == null) {
            state = FileKeyframes()
            service.setState(state)
        }
        return state
    }

    private fun currentDocument(event: AnActionEvent): Document? {
        val project = event.project!!
        val textEditor = FileEditorManager.getInstance(project).selectedTextEditor
        if (textEditor == null) {
            CodingReplayErrorDialogue(project, "Must have a file open to $actionLabel.").show()
            return null
        }

        return textEditor.document;
    }

    abstract fun performAction(project: Project, state: FileKeyframes, currentDocument: Document)
}