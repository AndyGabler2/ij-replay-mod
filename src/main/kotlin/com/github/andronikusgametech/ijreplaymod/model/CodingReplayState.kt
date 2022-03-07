package com.github.andronikusgametech.ijreplaymod.model

import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

data class CodingReplayState(
    @XCollection
    public var keyFramesSets: MutableList<FileKeyframeSet> = mutableListOf(),
    @Tag
    public var replayDelayType: String = "millisecond",
    @Tag
    public var replayDelay: Int = 200
)
