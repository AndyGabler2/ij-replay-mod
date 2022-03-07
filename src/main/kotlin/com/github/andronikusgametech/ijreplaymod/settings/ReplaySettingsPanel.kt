package com.github.andronikusgametech.ijreplaymod.settings

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.services.ReplayProjectService
import com.github.andronikusgametech.ijreplaymod.util.DelayType
import java.awt.GridLayout
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class ReplaySettingsPanel(service: ReplayProjectService): JPanel(GridLayout(9, 1)) {

    var modified = false

    private val delayTypeDropdown: JComboBox<String>
    private val delayInput: JTextField

    init {
        val delayTypePanel = JPanel(GridLayout(1, 2))
        delayTypePanel.add(JLabel(CodingReplayBundle.getProperty("cr.settings.field.delayType")))
        delayTypeDropdown = JComboBox(DelayType.values().map { type -> type.typeLabel }.toTypedArray())
        delayTypeDropdown.addActionListener { modified = true }
        delayTypePanel.add(delayTypeDropdown)

        val delayPanel = JPanel(GridLayout(1, 2))
        delayPanel.add(JLabel(CodingReplayBundle.getProperty("cr.settings.field.delay")))
        delayInput = JTextField()
        delayInput.addActionListener { modified = true }
        delayPanel.add(delayInput)

        add(delayTypePanel)
        add(delayPanel)

        delayTypeDropdown.selectedItem = service.state!!.replayDelayType
        delayInput.text = "${service.state!!.replayDelay}"
    }

    fun getDelayType(): String = delayTypeDropdown.selectedItem as String

    fun getDelay(): Int = Integer.parseInt(delayInput.text)
}