package com.github.andronikusgametech.ijreplaymod.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.*
import java.util.concurrent.TimeUnit

class RealtimeDocumentMutator(
    private val currentDocument: Document,
    private val project: Project,
    private val primaryCaret: Caret,
    private val currentVirtualFile: VirtualFile,
    private val scrollingModel: ScrollingModel
): IDocumentMutator {

    override fun deleteSegment(minimumPosition: Int, maximumPosition: Int) {

    }

    override fun writeSegment(text: String, startingPosition: Int) {
        var textIncrement = 0
        var currentPosition = startingPosition

        // Determine line and column
        val currentText = currentDocument.text
        var lineIncrement = currentText.substring(0, startingPosition).count { character -> character == '\n' }
        var columnIncrement = startingPosition
        if (lineIncrement != 0) {
            columnIncrement -= 1
            columnIncrement -= currentText.substring(0, startingPosition).lastIndexOf('\n')
        }

        var lastWriteTime = Date()
        var semaphore = 0

        while (textIncrement < text.length) {
            if (semaphore == 0 && TimeUnit.MILLISECONDS.toSeconds(Date().time - lastWriteTime.time) >= 2) {
                semaphore = 1

                if (text[textIncrement] == '\n') {
                    columnIncrement = 0
                    lineIncrement++
                }
                WriteCommandAction.runWriteCommandAction(project) {
                    currentDocument.insertString(currentPosition, "${text[textIncrement]}")
                    var additionalColumnOffset = if (text[textIncrement] != '\n') 1 else 0
                    primaryCaret.moveToLogicalPosition(
                        LogicalPosition(lineIncrement, columnIncrement + additionalColumnOffset)
                    )
                    primaryCaret.moveToVisualPosition(
                        VisualPosition(lineIncrement, columnIncrement + additionalColumnOffset)
                    )
                    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    semaphore = 0
                    lastWriteTime = Date()
                    currentVirtualFile.refresh(false, false)
                }

                if (text[textIncrement] != '\n') {
                    columnIncrement++
                }
                currentPosition++
                textIncrement++
            }
        }
    }
}