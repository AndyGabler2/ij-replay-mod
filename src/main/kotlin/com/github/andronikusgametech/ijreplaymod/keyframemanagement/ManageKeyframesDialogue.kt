package com.github.andronikusgametech.ijreplaymod.keyframemanagement

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframeSet
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NotNull
import javax.swing.JComponent

class ManageKeyframesDialogue(
    project: Project,
    private val frameSet: FileKeyframeSet,
    currentDocument: Document,
    documentToVirtualFileNameTransformer: (Document) -> @NotNull @NlsSafe String = {
        document -> FileDocumentManager.getInstance().getFile(document)!!.name
    }
): DialogWrapper(project) {

    private val wrappers: List<KeyframeManagementWrapper>
    private val fileName: String = documentToVirtualFileNameTransformer.invoke(currentDocument)

    init {
        title = CodingReplayBundle.getProperty("cr.ui.keyframeManagement.title", fileName)
        wrappers = initWrapper(frameSet)
        init()
    }

    public override fun createCenterPanel(): JComponent {
        return ManageKeyframesPanel(wrappers, fileName)
    }

    fun updateFrames() {
        wrappers.forEach { wrapper ->
            if (wrapper.isMarkedForDelete) {
                frameSet.keyFrames.removeIf { frame -> frame.id == wrapper.underlyingFrame.id }
                return
            }

            wrapper.underlyingFrame.apply {
                order = wrapper.order
                label = wrapper.label
                text = wrapper.text
            }
        }
    }

    companion object {
        fun initWrapper(frameSet: FileKeyframeSet): List<KeyframeManagementWrapper> = frameSet.keyFrames.map {
            frame -> KeyframeManagementWrapper(frame.order, frame.text, frame.label, false, frame)
        }
    }
}
