package com.github.andronikusgametech.ijreplaymod.keyframemanagement

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.actions.AbstractKeyframeAccessingAction
import com.github.andronikusgametech.ijreplaymod.actions.CodingReplayErrorDialogue
import com.github.andronikusgametech.ijreplaymod.model.CodingReplayState
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager

class ManageKeyframesAction: AbstractKeyframeAccessingAction(
    CodingReplayBundle.getProperty("cr.ui.keyframeManagement.actionLabel")
) {

    override fun performAction(event: AnActionEvent, state: CodingReplayState, currentDocument: Document) {
        val project = event.project!!
        val currentVirtualFile = FileDocumentManager.getInstance().getFile(currentDocument)
        val path = currentVirtualFile!!.path // TODO ensure this works for new files
        val fileFrameSet = state.keyFramesSets.firstOrNull { fileFrameSet -> fileFrameSet.fileName == path }

        if (fileFrameSet == null || fileFrameSet.keyFrames.isEmpty()) {
            CodingReplayErrorDialogue(
                project,
                CodingReplayBundle.getProperty("cr.ui.errorDialogue.errorMessage.noKeyframes")
            ).show()
            return
        }

        val dialogue = ManageKeyframesDialogue(project, fileFrameSet, currentDocument)

        if (dialogue.showAndGet()) {
            dialogue.updateFrames()
        }
    }
}