package com.github.andronikusgametech.ijreplaymod.keyframemanagement

import com.github.andronikusgametech.ijreplaymod.actions.AbstractKeyframeAccessingAction
import com.github.andronikusgametech.ijreplaymod.actions.CodingReplayErrorDialogue
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframes
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

class ManageKeyframesAction: AbstractKeyframeAccessingAction("manage file key frames") {

    override fun performAction(project: Project, state: FileKeyframes, currentDocument: Document) {
        val currentVirtualFile = FileDocumentManager.getInstance().getFile(currentDocument)
        val path = currentVirtualFile!!.path // TODO ensure this works for new files
        val fileFrameSet = state.keyFramesSets.firstOrNull { fileFrameSet -> fileFrameSet.fileName == path }

        if (fileFrameSet == null || fileFrameSet.keyFrames.isEmpty()) {
            CodingReplayErrorDialogue(project, "No key frame set for current file.").show()
            return
        }

        val dialogue = ManageKeyframesDialogue(project, fileFrameSet, currentDocument)

        if (dialogue.showAndGet()) {
            dialogue.updateFrames()
        }
    }
}