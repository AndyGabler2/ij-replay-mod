package com.github.andronikusgametech.ijreplaymod.keyframing

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.actions.AbstractKeyframeAccessingAction
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframeSet
import com.github.andronikusgametech.ijreplaymod.model.CodingReplayState
import com.github.andronikusgametech.ijreplaymod.model.Keyframe
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager

class KeyFrameFileAction : AbstractKeyframeAccessingAction(
    CodingReplayBundle.getProperty("cr.ui.keyframing.actionLabel")
) {

    override fun performAction(event: AnActionEvent, state: CodingReplayState, currentDocument: Document) {
        val currentVirtualFile = FileDocumentManager.getInstance().getFile(currentDocument)

        val path = currentVirtualFile!!.path // TODO ensure this works for new files
        val keyFrameText = currentDocument.text

        val fileFrameSet =
            state.keyFramesSets.firstOrNull { fileFrameSet -> fileFrameSet.fileName == path } ?:
            makeNewFrameSet(state, path)

        val nextOrder = (fileFrameSet.keyFrames.map { frame -> frame.order }.maxOfOrNull{ order -> order } ?: 0) + 1000
        val nextId = (fileFrameSet.keyFrames.map { frame -> frame.id }.maxOfOrNull{ order -> order } ?: 0) + 1

        fileFrameSet.keyFrames.add(Keyframe(nextOrder, nextId, CodingReplayBundle.getProperty("cr.ui.keyframing.defaultKeyframeLabel", nextId), keyFrameText))
    }

    private fun makeNewFrameSet(state: CodingReplayState, fileName: String): FileKeyframeSet {
        val newFileFrameSet = FileKeyframeSet(fileName, mutableListOf())
        state.keyFramesSets.add(newFileFrameSet)
        return newFileFrameSet
    }
}
