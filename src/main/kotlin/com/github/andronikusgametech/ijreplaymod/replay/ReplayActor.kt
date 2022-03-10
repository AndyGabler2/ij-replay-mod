package com.github.andronikusgametech.ijreplaymod.replay

import com.github.andronikusgametech.ijreplaymod.stopper.ReplayStopFlag
import com.github.andronikusgametech.ijreplaymod.util.IDocumentMutator
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch

class ReplayActor(
    private val documentMutator: IDocumentMutator,
    private val stopper: ReplayStopFlag
) {

    fun run(textVersions: List<String>) {
        if (textVersions.size < 2) {
            throw IllegalArgumentException("Text versions must have at least two versions.")
        }

        documentMutator.setText(textVersions[0])

        val matcher = DiffMatchPatch()
        matcher.diffTimeout = 0f

        textVersions.forEachIndexed { index, version ->
            if (isStopped()) {
                return
            }
            if (index != 0) {
                val oldText = textVersions[index - 1]
                val diffs = matcher.diffMain(oldText, version)

                var positionalIndex = 0
                diffs.forEach { diff ->
                    if (isStopped()) {
                        return
                    }
                    when (diff.operation) {
                        DiffMatchPatch.Operation.DELETE -> {
                            documentMutator.deleteSegment(positionalIndex, positionalIndex + diff.text.length)
                        }
                        DiffMatchPatch.Operation.INSERT -> {
                            documentMutator.writeSegment(diff.text, positionalIndex)
                            positionalIndex += diff.text.length
                        }
                        else -> {
                            // Presumed to be be EQUAL
                            positionalIndex += diff.text.length
                        }
                    }
                }
            }
        }

        stop()
    }

    fun isStopped(): Boolean = stopper.isStopped()

    fun stop() {
        stopper.stop()
    }
}