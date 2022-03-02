package com.github.andronikusgametech.ijreplaymod.replay

import com.github.andronikusgametech.ijreplaymod.model.FileKeyframeSet
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel

class ReplayKeyframeSelectorPanel(
    frameSet: FileKeyframeSet
): JPanel() {

    private val keyframeCheckBoxes = mutableListOf<JCheckBox>()
    private val addBlankFrameCheckBox: JCheckBox
    private val useLatestVersionAsLastKeyframeCheckBox: JCheckBox

    init {
        val rootPanel = JPanel(BorderLayout())
        val keyFrameRootPanel = JPanel(BorderLayout())
        val optionsRootPanel = JPanel(BorderLayout())

        keyFrameRootPanel.add(JLabel("Keyframes"), BorderLayout.PAGE_START)
        optionsRootPanel.add(JLabel("Options"), BorderLayout.PAGE_START)

        val keyFramesContentPanel = JPanel(GridLayout(frameSet.keyFrames.size, 1))
        frameSet.keyFrames.sortedBy { frame -> frame.order }.forEach { frame ->
            val checkBox = JCheckBox(frame.label, true)
            keyframeCheckBoxes.add(checkBox)
            keyFramesContentPanel.add(checkBox)
        }
        keyFrameRootPanel.add(keyFramesContentPanel, BorderLayout.CENTER)

        val optionsContentPanel = JPanel(GridLayout(2, 1))
        addBlankFrameCheckBox = JCheckBox("Add Blank Keyframe Before First", false)
        useLatestVersionAsLastKeyframeCheckBox = JCheckBox("Add Current Version as Last Keyframe", false)
        optionsContentPanel.add(addBlankFrameCheckBox)
        optionsContentPanel.add(useLatestVersionAsLastKeyframeCheckBox)
        optionsRootPanel.add(optionsContentPanel, BorderLayout.CENTER)

        rootPanel.add(keyFrameRootPanel, BorderLayout.CENTER)
        rootPanel.add(optionsRootPanel, BorderLayout.PAGE_END)
        add(rootPanel)
    }

    fun isAtLeastOneKeyframeSelected(): Boolean = keyframeCheckBoxes.any { checkBox -> checkBox.model.isSelected }

    fun areSufficientOptionsSelected(): Boolean {
        return keyframeCheckBoxes.count { checkBox -> checkBox.model.isSelected } >= 2 ||
            (isAtLeastOneKeyframeSelected() &&
            (isAddingBlankKeyframe() || isUsingLatestVersionAsLastKeyFrame()))
    }

    fun isAddingBlankKeyframe(): Boolean = addBlankFrameCheckBox.model.isSelected

    fun isUsingLatestVersionAsLastKeyFrame(): Boolean = useLatestVersionAsLastKeyframeCheckBox.model.isSelected

    fun getKeyframeSelectionVector(): List<Boolean> = keyframeCheckBoxes.map { checkbox -> checkbox.model.isSelected }
}
