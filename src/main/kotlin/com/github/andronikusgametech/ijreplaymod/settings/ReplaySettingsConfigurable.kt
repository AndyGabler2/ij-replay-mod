package com.github.andronikusgametech.ijreplaymod.settings

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.model.CodingReplayState
import com.github.andronikusgametech.ijreplaymod.services.ReplayProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class ReplaySettingsConfigurable(project: Project): Configurable {

    private val service: ReplayProjectService = project.service()
    private lateinit var panel: ReplaySettingsPanel

    init {
        if (service.state == null) {
            service.setState(CodingReplayState())
        }
    }

    override fun createComponent(): JComponent {
        panel = ReplaySettingsPanel(service)
        return panel
    }

    override fun isModified(): Boolean {
        return panel.modified
    }

    override fun apply() {
        val delayType = panel.getDelayType()
        val delay = try {
            panel.getDelay()
        } catch (exception: Exception) {
            throw ConfigurationException(CodingReplayBundle.getProperty("cr.settings.field.feedback.delay.mustBeInt"))
        }

        if (delay <= 0) {
            throw ConfigurationException(CodingReplayBundle.getProperty("cr.settings.field.feedback.delay.mustBePositive"))
        }

        service.state!!.replayDelay = delay
        service.state!!.replayDelayType = delayType
    }

    override fun getDisplayName(): String = CodingReplayBundle.getProperty("cr.settings.displayName")
}