package com.github.andronikusgametech.ijreplaymod.stopper

import com.github.andronikusgametech.ijreplaymod.actions.CodingReplayErrorDialogue
import com.github.andronikusgametech.ijreplaymod.services.ReplayProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class StopReplayAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        try {
            event.project!!.service<ReplayProjectService>().stopReplays()
        } catch (ex: IllegalStateException) {
            CodingReplayErrorDialogue(
                event.project!!,
                ex.message!!
            ).show()
            return
        }

        ReplaysStoppedDialogue(event.project!!).show()
    }
}
