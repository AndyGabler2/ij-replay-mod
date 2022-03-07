package com.github.andronikusgametech.ijreplaymod.replay

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframeSet
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NotNull
import javax.swing.JComponent

class ReplayKeyframeSelectorDialogue(
    project: Project,
    private val frameSet: FileKeyframeSet,
    private val currentDocument: Document,
    documentToVirtualFileNameTransformer: (Document) -> @NotNull @NlsSafe String = {
        document -> FileDocumentManager.getInstance().getFile(document)!!.name
    }
): DialogWrapper(project)  {

    private val fileName: String = documentToVirtualFileNameTransformer.invoke(currentDocument)
    private lateinit var panel: ReplayKeyframeSelectorPanel
    private var textVersions = mutableListOf<String>()

    init {
        title = CodingReplayBundle.getProperty("cr.ui.replay.frameSelector.title", fileName)
        init()
    }

    override fun createCenterPanel(): JComponent {
        panel = ReplayKeyframeSelectorPanel(frameSet)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        // TODO perhaps just virtual frame empty to current version is okay
        if (!panel.isAtLeastOneKeyframeSelected()) {
            return ValidationInfo(CodingReplayBundle.getProperty("cr.ui.replay.frameSelector.noFrames"))
        }

        if (!panel.areSufficientOptionsSelected()) {
            return ValidationInfo(CodingReplayBundle.getProperty("cr.ui.replay.frameSelector.insufficientOptions"))
        }

        makeTextVersionsFromPanel()
        return null
    }

    private fun makeTextVersionsFromPanel() {
        val orderedFrameSet = frameSet.keyFrames.sortedBy { frame -> frame.order }
        val selectionVector = panel.getKeyframeSelectionVector()

        val newTextVersions = mutableListOf<String>()
        if (panel.isAddingBlankKeyframe()) {
            newTextVersions.add("")
        }

        orderedFrameSet.forEachIndexed { index, keyframe ->
            if (selectionVector[index]) {
                newTextVersions.add(keyframe.text)
            }
        }

        if (panel.isUsingLatestVersionAsLastKeyFrame()) {
            newTextVersions.add(currentDocument.text)
        }

        textVersions = newTextVersions
    }

    fun textVersions(): List<String> = textVersions
}