package com.github.andronikusgametech.ijreplaymod.replay

import com.github.andronikusgametech.ijreplaymod.actions.AbstractKeyframeAccessingAction
import com.github.andronikusgametech.ijreplaymod.model.FileKeyframes
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project

class ReplayCodingOfFileAction : AbstractKeyframeAccessingAction("replay file coding") {


    override fun performAction(project: Project, state: FileKeyframes, currentDocument: Document) {
        print("replay called")
    }
}
