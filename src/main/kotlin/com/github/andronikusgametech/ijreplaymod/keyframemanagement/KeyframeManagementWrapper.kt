package com.github.andronikusgametech.ijreplaymod.keyframemanagement

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import com.github.andronikusgametech.ijreplaymod.model.Keyframe

class KeyframeManagementWrapper(
    var order: Int,
    var text: String,
    var label: String,
    var isMarkedForDelete: Boolean,
    val underlyingFrame: Keyframe
) {
    override fun toString(): String {
        var label = CodingReplayBundle.getProperty("cr.ui.keyframeManagement.keyframeLabel", label, order)
        if (isMarkedForDelete) {
            label += CodingReplayBundle.getProperty("cr.ui.keyframeManagement.keyframeLabel.markedForDelete")
        }

        return label
    }
}
