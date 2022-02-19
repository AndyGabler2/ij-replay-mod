package com.github.andronikusgametech.ijreplaymod.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ReplayCodingOfFileAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        println("replay button hit")
    }
}
