package com.github.andronikusgametech.ijreplaymod.keyframemanagement

import com.github.andronikusgametech.ijreplaymod.model.FileKeyframeSet
import com.github.andronikusgametech.ijreplaymod.model.Keyframe
import javax.swing.JFrame

fun main() {
    val frame = JFrame("Manage Keyframe Dialogue Test")
    frame.setSize(850, 750);

    val frameSet = FileKeyframeSet()
    frameSet.keyFrames.add(
        Keyframe(1000, 1, "Version 1", "I")
    )
    frameSet.keyFrames.add(
        Keyframe(4000, 4, "Version 4", "I Can Make Replay")
    )
    frameSet.keyFrames.add(
        Keyframe(2000, 2, "Version 2", "I Can")
    )
    frameSet.keyFrames.add(
        Keyframe(3000, 3, "Version 3", "I Can Make")
    )

    val panel = ManageKeyframesPanel(
        ManageKeyframesDialogue.initWrapper(frameSet), "TestFile.txt"
    )
    frame.add(panel)
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true
}
