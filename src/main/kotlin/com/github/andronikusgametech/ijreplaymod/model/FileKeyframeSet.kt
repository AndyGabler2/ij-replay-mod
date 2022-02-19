package com.github.andronikusgametech.ijreplaymod.model

import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

data class FileKeyframeSet(
    @Tag
    public var fileName: String = "",
    @XCollection
    public var keyFrames: MutableList<Keyframe> = mutableListOf()
)