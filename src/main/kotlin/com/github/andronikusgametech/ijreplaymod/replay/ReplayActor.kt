package com.github.andronikusgametech.ijreplaymod.replay

import com.github.andronikusgametech.ijreplaymod.util.IDocumentMutator

class ReplayActor(
    private val documentMutator: IDocumentMutator
) {

    fun run(textVersions: List<String>) {
        documentMutator.writeSegment("/*\n * yay REMOVE ME\n I WANT GONE\n*/", 40)
    }
}