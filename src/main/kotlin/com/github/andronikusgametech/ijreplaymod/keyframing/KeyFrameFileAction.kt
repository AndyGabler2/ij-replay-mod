package com.github.andronikusgametech.ijreplaymod.keyframing

import com.github.andronikusgametech.ijreplaymod.actions.AbstractKeyframeAccessingAction
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframeSet
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframes
import com.github.andronikusgametech.ijreplaymod.model.Keyframe
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

class KeyFrameFileAction : AbstractKeyframeAccessingAction("add a keyframe") {

    override fun performAction(project: Project, state: FileKeyframes, currentDocument: Document) {
        println("Key Frame Add Called")
        val currentVirtualFile = FileDocumentManager.getInstance().getFile(currentDocument)

        val path = currentVirtualFile!!.path // TODO ensure this works for new files
        val keyFrameText = currentDocument.text

        val fileFrameSet =
            state.keyFramesSets.firstOrNull { fileFrameSet -> fileFrameSet.fileName == path } ?:
            makeNewFrameSet(state, path)

        val nextOrder = (fileFrameSet.keyFrames.map { frame -> frame.order }.maxOfOrNull{ order -> order } ?: 0) + 1000
        val nextId = (fileFrameSet.keyFrames.map { frame -> frame.id }.maxOfOrNull{ order -> order } ?: 0) + 1

        fileFrameSet.keyFrames.add(Keyframe(nextOrder, nextId, "Version $nextId", keyFrameText))
    }

    private fun makeNewFrameSet(state: FileKeyframes, fileName: String): FileKeyframeSet {
        val newFileFrameSet = FileKeyframeSet(fileName, mutableListOf())
        state.keyFramesSets.add(newFileFrameSet)
        return newFileFrameSet
    }
}
