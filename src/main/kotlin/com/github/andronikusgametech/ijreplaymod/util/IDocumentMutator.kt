package com.github.andronikusgametech.ijreplaymod.util

interface IDocumentMutator {

    fun deleteSegment(minimumPosition: Int, maximumPosition: Int)

    fun writeSegment(text: String, startingPosition: Int)
}