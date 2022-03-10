package com.github.andronikusgametech.ijreplaymod.stopper

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ReplaysStoppedDialogue(
    project: Project
): DialogWrapper(project) {

    init {
        title = CodingReplayBundle.getProperty("cr.ui.stopper.confirmation.title")
        init()
    }

    override fun createCenterPanel(): JComponent? {
        val dialogPanel = JPanel(BorderLayout())

        val label = JLabel(CodingReplayBundle.getProperty("cr.ui.stopper.confirmation.body"))
        dialogPanel.add(label, BorderLayout.CENTER)

        return dialogPanel
    }
}