package com.github.andronikusgametech.ijreplaymod.actions

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.model.CodingReplayState
import com.github.andronikusgametech.ijreplaymod.services.ReplayProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager

abstract class AbstractKeyframeAccessingAction(private val actionLabel: String) : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val state = getOrCreateProjectState(event)
        val currentDocument = currentDocument(event) ?: return
        performAction(event, state, currentDocument)
    }

    private fun getOrCreateProjectState(event: AnActionEvent): CodingReplayState {
        val project = event.project!!
        val service = project.service<ReplayProjectService>()
        var state = service.state
        if (state == null) {
            state = CodingReplayState()
            service.setState(state)
        }
        return state
    }

    private fun currentDocument(event: AnActionEvent): Document? {
        val project = event.project!!
        val textEditor = FileEditorManager.getInstance(project).selectedTextEditor
        if (textEditor == null) {
            CodingReplayErrorDialogue(
                project,
                CodingReplayBundle.getProperty("cr.ui.errorDialogue.errorMessage.noFile", actionLabel)
            ).show()
            return null
        }

        return textEditor.document;
    }

    abstract fun performAction(event: AnActionEvent, state: CodingReplayState, currentDocument: Document)
}