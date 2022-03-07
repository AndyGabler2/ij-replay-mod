package com.github.andronikusgametech.ijreplaymod.actions

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CodingReplayErrorDialogue(
    project: Project,
    private val error: String
): DialogWrapper(project) {

    init {
        title = CodingReplayBundle.getProperty("cr.ui.errorDialogue.title")
        init()
    }

    override fun createCenterPanel(): JComponent? {
        val dialogPanel = JPanel(BorderLayout())

        val label = JLabel(error)
        dialogPanel.add(label, BorderLayout.CENTER)

        return dialogPanel
    }
}