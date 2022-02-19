package com.github.andronikusgametech.ijreplaymod.model

import com.intellij.util.xmlb.annotations.Tag

data class Keyframe(
    @Tag
    public var order: Int = 0,
    @Tag
    public var text: String = ""
)
