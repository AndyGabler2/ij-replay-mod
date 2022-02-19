package com.github.andronikusgametech.ijreplaymod.action

import com.github.andronikusgametech.ijreplaymod.model.FileKeyframeSet
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframes
import com.github.andronikusgametech.ijreplaymod.model.Keyframe
import com.github.andronikusgametech.ijreplaymod.services.ReplayProjectService
import com.github.andronikusgametech.ijreplaymod.util.CodingReplayErrorDialogue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager

class KeyFrameFileAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        println("Key Frame Add Called")
        val project = event.project!!
        val service = project.service<ReplayProjectService>()
        var state = service.state
        if (state == null) {
            state = FileKeyframes()
            service.setState(state)
        }

        val textEditor = FileEditorManager.getInstance(project).selectedTextEditor
        if (textEditor == null) {
            CodingReplayErrorDialogue(project, "Must have a file open to keyframe.").show()
            return
        }

        val currentDocument = textEditor.document
        val currentVirtualFile = FileDocumentManager.getInstance().getFile(currentDocument)

        val path = currentVirtualFile!!.path // TODO ensure this works for new files
        val keyFrameText = currentDocument.text

        val fileFrameSet =
            state.keyFramesSets.firstOrNull { fileFrameSet -> fileFrameSet.fileName == path } ?:
            makeNewFrameSet(state, path)

        val nextOrder = (fileFrameSet.keyFrames.map { frame -> frame.order }.maxOfOrNull{ order -> order } ?: 0) + 1000
        fileFrameSet.keyFrames.add(Keyframe(nextOrder, keyFrameText))
    }

    private fun makeNewFrameSet(state: FileKeyframes, fileName: String): FileKeyframeSet {
        val newFileFrameSet = FileKeyframeSet(fileName, mutableListOf())
        state.keyFramesSets.add(newFileFrameSet)
        return newFileFrameSet
    }
}
