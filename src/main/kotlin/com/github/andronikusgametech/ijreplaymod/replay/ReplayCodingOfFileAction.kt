package com.github.andronikusgametech.ijreplaymod.replay

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.actions.AbstractKeyframeAccessingAction
import com.github.andronikusgametech.ijreplaymod.actions.CodingReplayErrorDialogue
import com.github.andronikusgametech.ijreplaymod.model.CodingReplayState
import com.github.andronikusgametech.ijreplaymod.util.RealtimeDocumentMutator
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.concurrency.AppExecutorUtil

class ReplayCodingOfFileAction : AbstractKeyframeAccessingAction("replay file coding") {

    override fun performAction(event: AnActionEvent, state: CodingReplayState, currentDocument: Document) {
        val project = event.project!!
        val path = FileDocumentManager.getInstance().getFile(currentDocument)!!.path // TODO ensure this works for new files
        val fileFrameSet = state.keyFramesSets.firstOrNull { fileFrameSet -> fileFrameSet.fileName == path }

        if (fileFrameSet == null || fileFrameSet.keyFrames.isEmpty()) {
            CodingReplayErrorDialogue(project, CodingReplayBundle.getProperty("cr.ui.errorDialogue.errorMessage.noKeyframes")).show()
            return
        }

        val virtualFile = event.getData(PlatformDataKeys.VIRTUAL_FILE)!!
        val document = event.getData(CommonDataKeys.EDITOR)!!.document
        val editor = event.getRequiredData(CommonDataKeys.EDITOR)
        val caretModel = editor.caretModel
        val primaryCaret = caretModel.primaryCaret

        val selectorDialogue = ReplayKeyframeSelectorDialogue(project, fileFrameSet, document)
        if (selectorDialogue.showAndGet()) {
            AppExecutorUtil.getAppExecutorService().execute {
                val mutator = RealtimeDocumentMutator(
                    document, project, primaryCaret, virtualFile,
                    editor.scrollingModel, state.replayDelayType, state.replayDelay
                )
                val actor = ReplayActor(mutator)
                actor.run(selectorDialogue.textVersions())
            }
        }
    }
}
