package com.github.andronikusgametech.ijreplaymod.util

interface IDocumentMutator {

    fun setText(completeText: String)

    fun deleteSegment(minimumPosition: Int, maximumPosition: Int)

    fun writeSegment(text: String, startingPosition: Int)
}