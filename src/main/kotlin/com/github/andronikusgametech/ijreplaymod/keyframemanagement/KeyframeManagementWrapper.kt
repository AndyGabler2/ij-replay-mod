package com.github.andronikusgametech.ijreplaymod.keyframemanagement

import com.github.andronikusgametech.ijreplaymod.model.Keyframe

class KeyframeManagementWrapper(
    var order: Int,
    var text: String,
    var label: String,
    var isMarkedForDelete: Boolean,
    val underlyingFrame: Keyframe
) {
    override fun toString(): String {
        var label = "$label (Order: $order)"
        if (isMarkedForDelete) {
            label += " [MARKED FOR DELETE]"
        }

        return label
    }
}
