package com.github.andronikusgametech.ijreplaymod.model

import com.intellij.util.xmlb.annotations.XCollection

data class FileKeyframes(
    @XCollection
    public var keyFramesSets: MutableList<FileKeyframeSet> = mutableListOf()
)
