package com.github.andronikusgametech.ijreplaymod.util

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern


/*
 * Functions for diff, match and patch.
 * Computes the difference between two texts to create a patch.
 * Applies the patch onto another text, allowing for errors.
 *
 * @author fraser@google.com (Neil Fraser)
 */

/*
 * Functions for diff, match and patch.
 * Computes the difference between two texts to create a patch.
 * Applies the patch onto another text, allowing for errors.
 *
 * @author fraser@google.com (Neil Fraser)
 */
/**
 * Class containing the diff, match and patch methods.
 * Also contains the behaviour settings.
 */
class DiffMatchPatch() {
    // Defaults.
    // Set these on your DiffMatchPatch instance to override the defaults.
    /**
     * Number of seconds to map a diff before giving up (0 for infinity).
     */
    var diffTimeout = 1.0f

    /**
     * Cost of an empty edit operation in terms of edit characters.
     */
    var diffEditCost: Short = 4

    /**
     * At what point is no match declared (0.0 = perfection, 1.0 = very loose).
     */
    var matchThreshold = 0.5f

    /**
     * How far to search for a match (0 = exact location, 1000+ = broad match).
     * A match this many characters away from the expected location will add
     * 1.0 to the score (0.0 is a perfect match).
     */
    var matchDistance = 1000

    /**
     * When deleting a large block of text (over ~64 characters), how close do
     * the contents have to be to match the expected contents. (0.0 = perfection,
     * 1.0 = very loose). Note that matchThreshold controls how closely the
     * end points of a delete need to match.
     */
    var patchDeleteThreshold = 0.5f

    /**
     * Chunk size for context length.
     */
    var patchMargin: Short = 4

    /**
     * Internal class for returning results from diffLinesToChars().
     * Other less paranoid languages just use a three-element array.
     */
    class LinesToCharsResult(
        var chars1: String, var chars2: String,
        var lineArray: List<String>
    )
    //  DIFF FUNCTIONS
    /**
     * The data structure representing a diff is a Linked list of Diff objects:
     * {Diff(Operation.DELETE, "Hello"), Diff(Operation.INSERT, "Goodbye"),
     * Diff(Operation.EQUAL, " world.")}
     * which means: delete "Hello", add "Goodbye" and keep " world."
     */
    enum class Operation {
        DELETE, INSERT, EQUAL
    }
    /**
     * Find the differences between two texts.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag. If false, then don't run a
     * line-level diff first to identify the changed areas.
     * If true, then run a faster slightly less optimal diff.
     * @return Linked List of Diff objects.
     */
    /**
     * Find the differences between two texts.
     * Run a faster, slightly less optimal diff.
     * This method allows the 'checklines' of diffMain() to be optional.
     * Most of the time checklines is wanted, so default to true.
     *
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @return Linked List of Diff objects.
     */
    @JvmOverloads
    fun diffMain(
        text1: String?, text2: String?,
        checklines: Boolean = true
    ): LinkedList<Diff> {
        // Set a deadline by which time the diff must be complete.
        val deadline: Long
        if (diffTimeout <= 0) {
            deadline = Long.MAX_VALUE
        } else {
            deadline = System.currentTimeMillis() + (diffTimeout * 1000).toLong()
        }
        return diffMain(text1, text2, checklines, deadline)
    }

    /**
     * Find the differences between two texts. Simplifies the problem by
     * stripping any common prefix or suffix off the texts before diffing.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag. If false, then don't run a
     * line-level diff first to identify the changed areas.
     * If true, then run a faster slightly less optimal diff.
     * @param deadline   Time when the diff should be complete by. Used
     * internally for recursive calls. Users should set `diffTimeout` instead.
     * @return Linked List of `Diff` objects.
     */
    private fun diffMain(
        text1: String?, text2: String?,
        checklines: Boolean, deadline: Long
    ): LinkedList<Diff> {
        // Check for null inputs.
        var text1 = text1
        var text2 = text2
        if (text1 == null || text2 == null) {
            throw IllegalArgumentException("Null inputs. (diffMain)")
        }

        // Check for equality (speedup).
        val diffs: LinkedList<Diff>
        if (text1 == text2) {
            diffs = LinkedList()
            if (text1.length != 0) {
                diffs.add(Diff(Operation.EQUAL, text1))
            }
            return diffs
        }

        // Trim off common prefix (speedup).
        var commonlength = diffCommonPrefix(text1, text2)
        val commonprefix = text1.substring(0, commonlength)
        text1 = text1.substring(commonlength)
        text2 = text2.substring(commonlength)

        // Trim off common suffix (speedup).
        commonlength = diffCommonSuffix(text1, text2)
        val commonsuffix = text1.substring(text1.length - commonlength)
        text1 = text1.substring(0, text1.length - commonlength)
        text2 = text2.substring(0, text2.length - commonlength)

        // Compute the diff on the middle block.
        diffs = diffCompute(text1, text2, checklines, deadline)

        // Restore the prefix and suffix.
        if (commonprefix.length != 0) {
            diffs.addFirst(Diff(Operation.EQUAL, commonprefix))
        }
        if (commonsuffix.length != 0) {
            diffs.addLast(Diff(Operation.EQUAL, commonsuffix))
        }
        diffCleanupMerge(diffs)
        return diffs
    }

    /**
     * Find the differences between two texts. Assumes that the texts do not
     * have any common prefix or suffix.
     *
     * @param text1      Old string to be diffed.
     * @param text2      New string to be diffed.
     * @param checklines Speedup flag. If false, then don't run a
     * line-level diff first to identify the changed areas.
     * If true, then run a faster slightly less optimal diff.
     * @param deadline   Time when the diff should be complete by.
     * @return Linked List of Diff objects.
     */
    private fun diffCompute(
        text1: String, text2: String,
        checklines: Boolean, deadline: Long
    ): LinkedList<Diff> {
        var diffs = LinkedList<Diff>()
        if (text1.length == 0) {
            // Just add some text (speedup).
            diffs.add(Diff(Operation.INSERT, text2))
            return diffs
        }
        if (text2.length == 0) {
            // Just delete some text (speedup).
            diffs.add(Diff(Operation.DELETE, text1))
            return diffs
        }
        val longtext = if (text1.length > text2.length) text1 else text2
        val shorttext = if (text1.length > text2.length) text2 else text1
        val i = longtext.indexOf(shorttext)
        if (i != -1) {
            // Shorter text is inside the longer text (speedup).
            val op = if (text1.length > text2.length) Operation.DELETE else Operation.INSERT
            diffs.add(Diff(op, longtext.substring(0, i)))
            diffs.add(Diff(Operation.EQUAL, shorttext))
            diffs.add(Diff(op, longtext.substring(i + shorttext.length)))
            return diffs
        }
        if (shorttext.length == 1) {
            // Single character string.
            // After the previous speedup, the character can't be an equality.
            diffs.add(Diff(Operation.DELETE, text1))
            diffs.add(Diff(Operation.INSERT, text2))
            return diffs
        }

        // Check to see if the problem can be split in two.
        val hm = diffHalfMatch(text1, text2)
        if (hm != null) {
            // A half-match was found, sort out the return data.
            val text1a = hm[0]
            val text1b = hm[1]
            val text2a = hm[2]
            val text2b = hm[3]
            val midCommon = hm[4]
            // Send both pairs off for separate processing.
            val diffsA = diffMain(
                text1a, text2a,
                checklines, deadline
            )
            val diffsB = diffMain(
                text1b, text2b,
                checklines, deadline
            )
            // Merge the results.
            diffs = diffsA
            diffs.add(Diff(Operation.EQUAL, midCommon))
            diffs.addAll(diffsB)
            return diffs
        }
        return if (checklines && (text1.length > 100) && (text2.length > 100)) {
            diffLineMode(text1, text2, deadline)
        } else diffBisect(text1, text2, deadline)
    }

    /**
     * Do a quick line-level diff on both strings, then rediff the parts for
     * greater accuracy.
     * This speedup can produce non-minimal diffs.
     *
     * @param text1    Old string to be diffed.
     * @param text2    New string to be diffed.
     * @param deadline Time when the diff should be complete by.
     * @return Linked List of Diff objects.
     */
    private fun diffLineMode(
        text1: String, text2: String,
        deadline: Long
    ): LinkedList<Diff> {
        // Scan the text on a line-by-line basis first.
        var text1 = text1
        var text2 = text2
        val a = diffLinesToChars(text1, text2)
        text1 = a.chars1
        text2 = a.chars2
        val linearray = a.lineArray
        val diffs = diffMain(text1, text2, false, deadline)

        // Convert the diff back to original text.
        diffCharsToLines(diffs, linearray)
        // Eliminate freak matches (e.g. blank lines)
        diffCleanupSemantic(diffs)

        // Rediff any replacement blocks, this time character-by-character.
        // Add a dummy entry at the end.
        diffs.add(Diff(Operation.EQUAL, ""))
        var countDelete = 0
        var countInsert = 0
        var textDelete: String = ""
        var textInsert: String = ""
        val pointer = diffs.listIterator()
        var thisDiff: Diff? = pointer.next()
        while (thisDiff != null) {
            when (thisDiff.operation) {
                Operation.INSERT -> {
                    countInsert++
                    textInsert += thisDiff.text
                }
                Operation.DELETE -> {
                    countDelete++
                    textDelete += thisDiff.text
                }
                Operation.EQUAL -> {
                    // Upon reaching an equality, check for prior redundancies.
                    if (countDelete >= 1 && countInsert >= 1) {
                        // Delete the offending records and add the merged ones.
                        pointer.previous()
                        var j = 0
                        while (j < countDelete + countInsert) {
                            pointer.previous()
                            pointer.remove()
                            j++
                        }
                        for (newDiff: Diff in diffMain(
                            textDelete, textInsert, false,
                            deadline
                        )) {
                            pointer.add(newDiff)
                        }
                    }
                    countInsert = 0
                    countDelete = 0
                    textDelete = ""
                    textInsert = ""
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }
        diffs.removeLast() // Remove the dummy entry at the end.
        return diffs
    }

    /**
     * Find the 'middle snake' of a diff, split the problem in two
     * and return the recursively constructed diff.
     * See Myers 1986 paper: An O(ND) Difference Algorithm and Its Variations.
     *
     * @param text1    Old string to be diffed.
     * @param text2    New string to be diffed.
     * @param deadline Time at which to bail if not yet complete.
     * @return LinkedList of Diff objects.
     */
    protected fun diffBisect(
        text1: String, text2: String,
        deadline: Long
    ): LinkedList<Diff> {
        // Cache the text lengths to prevent multiple calls.
        val text1Length = text1.length
        val text2Length = text2.length
        val maxD = (text1Length + text2Length + 1) / 2
        val vOffset = maxD
        val vLength = 2 * maxD
        val v1 = IntArray(vLength)
        val v2 = IntArray(vLength)
        for (x in 0 until vLength) {
            v1[x] = -1
            v2[x] = -1
        }
        v1[vOffset + 1] = 0
        v2[vOffset + 1] = 0
        val delta = text1Length - text2Length
        // If the total number of characters is odd, then the front path will
        // collide with the reverse path.
        val front = delta % 2 != 0
        // Offsets for start and end of k loop.
        // Prevents mapping of space beyond the grid.
        var k1start = 0
        var k1end = 0
        var k2start = 0
        var k2end = 0
        for (d in 0 until maxD) {
            // Bail out if deadline is reached.
            if (System.currentTimeMillis() > deadline) {
                break
            }

            // Walk the front path one step.
            var k1 = -d + k1start
            while (k1 <= d - k1end) {
                val k1Offset = vOffset + k1
                var x1: Int
                if (k1 == -d || k1 != d && v1[k1Offset - 1] < v1[k1Offset + 1]) {
                    x1 = v1[k1Offset + 1]
                } else {
                    x1 = v1[k1Offset - 1] + 1
                }
                var y1 = x1 - k1
                while (x1 < text1Length && y1 < text2Length && text1[x1] == text2[y1]) {
                    x1++
                    y1++
                }
                v1[k1Offset] = x1
                if (x1 > text1Length) {
                    // Ran off the right of the graph.
                    k1end += 2
                } else if (y1 > text2Length) {
                    // Ran off the bottom of the graph.
                    k1start += 2
                } else if (front) {
                    val k2Offset = vOffset + delta - k1
                    if (k2Offset >= 0 && k2Offset < vLength && v2[k2Offset] != -1) {
                        // Mirror x2 onto top-left coordinate system.
                        val x2 = text1Length - v2[k2Offset]
                        if (x1 >= x2) {
                            // Overlap detected.
                            return diffBisectSplit(text1, text2, x1, y1, deadline)
                        }
                    }
                }
                k1 += 2
            }

            // Walk the reverse path one step.
            var k2 = -d + k2start
            while (k2 <= d - k2end) {
                val k2Offset = vOffset + k2
                var x2: Int
                if (k2 == -d || k2 != d && v2[k2Offset - 1] < v2[k2Offset + 1]) {
                    x2 = v2[k2Offset + 1]
                } else {
                    x2 = v2[k2Offset - 1] + 1
                }
                var y2 = x2 - k2
                while (x2 < text1Length && y2 < text2Length && text1[text1Length - x2 - 1] ==
                    text2[text2Length - y2 - 1]
                ) {
                    x2++
                    y2++
                }
                v2[k2Offset] = x2
                if (x2 > text1Length) {
                    // Ran off the left of the graph.
                    k2end += 2
                } else if (y2 > text2Length) {
                    // Ran off the top of the graph.
                    k2start += 2
                } else if (!front) {
                    val k1Offset = vOffset + delta - k2
                    if (k1Offset >= 0 && k1Offset < vLength && v1[k1Offset] != -1) {
                        val x1 = v1[k1Offset]
                        val y1 = vOffset + x1 - k1Offset
                        // Mirror x2 onto top-left coordinate system.
                        x2 = text1Length - x2
                        if (x1 >= x2) {
                            // Overlap detected.
                            return diffBisectSplit(text1, text2, x1, y1, deadline)
                        }
                    }
                }
                k2 += 2
            }
        }
        // Diff took too long and hit the deadline or
        // number of diffs equals number of characters, no commonality at all.
        val diffs = LinkedList<Diff>()
        diffs.add(Diff(Operation.DELETE, text1))
        diffs.add(Diff(Operation.INSERT, text2))
        return diffs
    }

    /**
     * Given the location of the 'middle snake', split the diff in two parts
     * and recurse.
     *
     * @param text1    Old string to be diffed.
     * @param text2    New string to be diffed.
     * @param x        Index of split point in text1.
     * @param y        Index of split point in text2.
     * @param deadline Time at which to bail if not yet complete.
     * @return LinkedList of Diff objects.
     */
    private fun diffBisectSplit(
        text1: String, text2: String,
        x: Int, y: Int, deadline: Long
    ): LinkedList<Diff> {
        val text1a = text1.substring(0, x)
        val text2a = text2.substring(0, y)
        val text1b = text1.substring(x)
        val text2b = text2.substring(y)

        // Compute both diffs serially.
        val diffs = diffMain(text1a, text2a, false, deadline)
        val diffsb = diffMain(text1b, text2b, false, deadline)
        diffs.addAll(diffsb)
        return diffs
    }

    /**
     * Split two texts into a list of strings. Reduce the texts to a string of
     * hashes where each Unicode character represents one line.
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return An object containing the encoded text1, the encoded text2 and
     * the List of unique strings. The zeroth element of the List of
     * unique strings is intentionally blank.
     */
    protected fun diffLinesToChars(text1: String, text2: String): LinesToCharsResult {
        val lineArray: MutableList<String> = ArrayList()
        val lineHash: MutableMap<String, Int> = HashMap()
        // e.g. linearray[4] == "Hello\n"
        // e.g. linehash.get("Hello\n") == 4

        // "\x00" is a valid character, but various debuggers don't like it.
        // So we'll insert a junk entry to avoid generating a null character.
        lineArray.add("")

        // Allocate 2/3rds of the space for text1, the rest for text2.
        val chars1 = diffLinesToCharsMunge(text1, lineArray, lineHash, 40000)
        val chars2 = diffLinesToCharsMunge(text2, lineArray, lineHash, 65535)
        return LinesToCharsResult(chars1, chars2, lineArray)
    }

    /**
     * Split a text into a list of strings. Reduce the texts to a string of
     * hashes where each Unicode character represents one line.
     *
     * @param text      String to encode.
     * @param lineArray List of unique strings.
     * @param lineHash  Map of strings to indices.
     * @param maxLines  Maximum length of lineArray.
     * @return Encoded string.
     */
    private fun diffLinesToCharsMunge(
        text: String, lineArray: MutableList<String>,
        lineHash: MutableMap<String, Int>, maxLines: Int
    ): String {
        var lineStart = 0
        var lineEnd = -1
        var line: String
        val chars = StringBuilder()
        // Walk the text, pulling out a substring for each line.
        // text.split('\n') would would temporarily double our memory footprint.
        // Modifying text would create many large strings to garbage collect.
        while (lineEnd < text.length - 1) {
            lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd == -1) {
                lineEnd = text.length - 1
            }
            line = text.substring(lineStart, lineEnd + 1)
            if (lineHash.containsKey(line)) {
                chars.append((lineHash[line] as Int).toChar().toString())
            } else {
                if (lineArray.size == maxLines) {
                    // Bail out at 65535 because
                    // String.valueOf((char) 65536).equals(String.valueOf(((char) 0)))
                    line = text.substring(lineStart)
                    lineEnd = text.length
                }
                lineArray.add(line)
                lineHash[line] = lineArray.size - 1
                chars.append((lineArray.size - 1).toChar().toString())
            }
            lineStart = lineEnd + 1
        }
        return chars.toString()
    }

    /**
     * Rehydrate the text in a diff from a string of line hashes to real lines of
     * text.
     *
     * @param diffs     List of Diff objects.
     * @param lineArray List of unique strings.
     */
    protected fun diffCharsToLines(
        diffs: List<Diff>,
        lineArray: List<String>
    ) {
        var text: StringBuilder
        for (diff: Diff in diffs) {
            text = StringBuilder()
            for (y in 0 until diff.text!!.length) {
                text.append(lineArray[diff.text!![y].toInt()])
            }
            diff.text = text.toString()
        }
    }

    /**
     * Determine the common prefix of two strings
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the start of each string.
     */
    fun diffCommonPrefix(text1: String?, text2: String?): Int {
        // Performance analysis: https://neil.fraser.name/news/2007/10/09/
        val n = Math.min(text1!!.length, text2!!.length)
        for (i in 0 until n) {
            if (text1[i] != text2[i]) {
                return i
            }
        }
        return n
    }

    /**
     * Determine the common suffix of two strings
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of each string.
     */
    fun diffCommonSuffix(text1: String?, text2: String?): Int {
        // Performance analysis: https://neil.fraser.name/news/2007/10/09/
        val text1Length = text1!!.length
        val text2Length = text2!!.length
        val n = Math.min(text1Length, text2Length)
        for (i in 1..n) {
            if (text1[text1Length - i] != text2[text2Length - i]) {
                return i - 1
            }
        }
        return n
    }

    /**
     * Determine if the suffix of one string is the prefix of another.
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of the first
     * string and the start of the second string.
     */
    fun diffCommonOverlap(text1: String?, text2: String?): Int {
        // Cache the text lengths to prevent multiple calls.
        var text1 = text1
        var text2 = text2
        val text1Length = text1!!.length
        val text2Length = text2!!.length
        // Eliminate the null case.
        if (text1Length == 0 || text2Length == 0) {
            return 0
        }
        // Truncate the longer string.
        if (text1Length > text2Length) {
            text1 = text1.substring(text1Length - text2Length)
        } else if (text1Length < text2Length) {
            text2 = text2.substring(0, text1Length)
        }
        val textLength = Math.min(text1Length, text2Length)
        // Quick check for the worst case.
        if (text1 == text2) {
            return textLength
        }

        // Start by looking for a single character match
        // and increase length until no match is found.
        // Performance analysis: https://neil.fraser.name/news/2010/11/04/
        var best = 0
        var length = 1
        while (true) {
            val pattern = text1.substring(textLength - length)
            val found = text2.indexOf(pattern)
            if (found == -1) {
                return best
            }
            length += found
            if (found == 0 || text1.substring(textLength - length) ==
                text2.substring(0, length)
            ) {
                best = length
                length++
            }
        }
    }

    /**
     * Do the two texts share a substring which is at least half the length of
     * the longer text?
     * This speedup can produce non-minimal diffs.
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return Five element String array, containing the prefix of text1, the
     * suffix of text1, the prefix of text2, the suffix of text2 and the
     * common middle. Or null if there was no match.
     */
    protected fun diffHalfMatch(text1: String, text2: String): Array<String>? {
        if (diffTimeout <= 0) {
            // Don't risk returning a non-optimal diff if we have unlimited time.
            return null
        }
        val longtext = if (text1.length > text2.length) text1 else text2
        val shorttext = if (text1.length > text2.length) text2 else text1
        if (longtext.length < 4 || shorttext.length * 2 < longtext.length) {
            return null // Pointless.
        }

        // First check if the second quarter is the seed for a half-match.
        val hm1 = diffHalfMatchI(
            longtext, shorttext,
            (longtext.length + 3) / 4
        )
        // Check again based on the third quarter.
        val hm2 = diffHalfMatchI(
            longtext, shorttext,
            (longtext.length + 1) / 2
        )
        val hm: Array<String>?
        if (hm1 == null && hm2 == null) {
            return null
        } else if (hm2 == null) {
            hm = hm1
        } else if (hm1 == null) {
            hm = hm2
        } else {
            // Both matched.  Select the longest.
            hm = if (hm1[4].length > hm2[4].length) hm1 else hm2
        }

        // A half-match was found, sort out the return data.
        return if (text1.length > text2.length) {
            hm
            //return new String[]{hm[0], hm[1], hm[2], hm[3], hm[4]};
        } else {
            arrayOf(
                hm!!.get(2), hm.get(3), hm.get(0), hm.get(1), hm.get(4)
            )
        }
    }

    /**
     * Does a substring of shorttext exist within longtext such that the
     * substring is at least half the length of longtext?
     *
     * @param longtext  Longer string.
     * @param shorttext Shorter string.
     * @param i         Start index of quarter length substring within longtext.
     * @return Five element String array, containing the prefix of longtext, the
     * suffix of longtext, the prefix of shorttext, the suffix of shorttext
     * and the common middle. Or null if there was no match.
     */
    private fun diffHalfMatchI(longtext: String, shorttext: String, i: Int): Array<String>? {
        // Start with a 1/4 length substring at position i as a seed.
        val seed = longtext.substring(i, i + longtext.length / 4)
        var j = -1
        var bestCommon = ""
        var bestLongtextA = ""
        var bestLongtextB = ""
        var bestShorttextA = ""
        var bestShorttextB = ""
        while (shorttext.indexOf(seed, j + 1).also { j = it } != -1) {
            val prefixLength = diffCommonPrefix(
                longtext.substring(i),
                shorttext.substring(j)
            )
            val suffixLength = diffCommonSuffix(
                longtext.substring(0, i),
                shorttext.substring(0, j)
            )
            if (bestCommon.length < suffixLength + prefixLength) {
                bestCommon = shorttext.substring(j - suffixLength, j) +
                        shorttext.substring(j, j + prefixLength)
                bestLongtextA = longtext.substring(0, i - suffixLength)
                bestLongtextB = longtext.substring(i + prefixLength)
                bestShorttextA = shorttext.substring(0, j - suffixLength)
                bestShorttextB = shorttext.substring(j + prefixLength)
            }
        }
        return if (bestCommon.length * 2 >= longtext.length) {
            arrayOf(
                bestLongtextA, bestLongtextB,
                bestShorttextA, bestShorttextB, bestCommon
            )
        } else {
            null
        }
    }

    /**
     * Reduce the number of edits by eliminating semantically trivial equalities.
     *
     * @param diffs LinkedList of Diff objects.
     */
    fun diffCleanupSemantic(diffs: LinkedList<Diff>) {
        if (diffs.isEmpty()) {
            return
        }
        var changes = false
        val equalities: Deque<Diff> = ArrayDeque() // Double-ended queue of qualities.
        var lastEquality: String? = null // Always equal to equalities.peek().text
        var pointer = diffs.listIterator()
        // Number of characters that changed prior to the equality.
        var lengthInsertions1 = 0
        var lengthDeletions1 = 0
        // Number of characters that changed after the equality.
        var lengthInsertions2 = 0
        var lengthDeletions2 = 0
        var thisDiff: Diff? = pointer.next()
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {
                // Equality found.
                equalities.push(thisDiff)
                lengthInsertions1 = lengthInsertions2
                lengthDeletions1 = lengthDeletions2
                lengthInsertions2 = 0
                lengthDeletions2 = 0
                lastEquality = thisDiff.text
            } else {
                // An insertion or deletion.
                if (thisDiff.operation == Operation.INSERT) {
                    lengthInsertions2 += thisDiff.text!!.length
                } else {
                    lengthDeletions2 += thisDiff.text!!.length
                }
                // Eliminate an equality that is smaller or equal to the edits on both
                // sides of it.
                if (lastEquality != null && lastEquality.length <=
                    Math.max(lengthInsertions1, lengthDeletions1) &&
                    lastEquality.length <=
                    Math.max(lengthInsertions2, lengthDeletions2)
                ) {
                    //System.out.println("Splitting: '" + lastEquality + "'");
                    // Walk back to offending equality.
                    while (thisDiff !== equalities.peek()) {
                        thisDiff = pointer.previous()
                    }
                    pointer.next()

                    // Replace equality with a delete.
                    pointer.set(Diff(Operation.DELETE, lastEquality))
                    // Insert a corresponding an insert.
                    pointer.add(Diff(Operation.INSERT, lastEquality))
                    equalities.pop() // Throw away the equality we just deleted.
                    if (!equalities.isEmpty()) {
                        // Throw away the previous equality (it needs to be reevaluated).
                        equalities.pop()
                    }
                    if (equalities.isEmpty()) {
                        // There are no previous equalities, walk back to the start.
                        while (pointer.hasPrevious()) {
                            pointer.previous()
                        }
                    } else {
                        // There is a safe equality we can fall back to.
                        thisDiff = equalities.peek()
                        while (thisDiff !== pointer.previous()) {
                            // Intentionally empty loop.
                        }
                    }
                    lengthInsertions1 = 0 // Reset the counters.
                    lengthInsertions2 = 0
                    lengthDeletions1 = 0
                    lengthDeletions2 = 0
                    lastEquality = null
                    changes = true
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }

        // Normalize the diff.
        if (changes) {
            diffCleanupMerge(diffs)
        }
        diffCleanupSemanticLossless(diffs)

        // Find any overlaps between deletions and insertions.
        // e.g: <del>abcxxx</del><ins>xxxdef</ins>
        //   -> <del>abc</del>xxx<ins>def</ins>
        // e.g: <del>xxxabc</del><ins>defxxx</ins>
        //   -> <ins>def</ins>xxx<del>abc</del>
        // Only extract an overlap if it is as big as the edit ahead or behind it.
        pointer = diffs.listIterator()
        var prevDiff: Diff? = null
        thisDiff = null
        if (pointer.hasNext()) {
            prevDiff = pointer.next()
            if (pointer.hasNext()) {
                thisDiff = pointer.next()
            }
        }
        while (thisDiff != null) {
            if (prevDiff!!.operation == Operation.DELETE &&
                thisDiff.operation == Operation.INSERT
            ) {
                val deletion = prevDiff.text
                val insertion = thisDiff.text
                val overlapLength1 = diffCommonOverlap(deletion, insertion)
                val overlapLength2 = diffCommonOverlap(insertion, deletion)
                if (overlapLength1 >= overlapLength2) {
                    if (overlapLength1 >= deletion!!.length / 2.0 ||
                        overlapLength1 >= insertion!!.length / 2.0
                    ) {
                        // Overlap found. Insert an equality and trim the surrounding edits.
                        pointer.previous()
                        pointer.add(
                            Diff(
                                Operation.EQUAL,
                                insertion!!.substring(0, overlapLength1)
                            )
                        )
                        prevDiff.text = deletion.substring(0, deletion.length - overlapLength1)
                        thisDiff.text = insertion.substring(overlapLength1)
                        // pointer.add inserts the element before the cursor, so there is
                        // no need to step past the new element.
                    }
                } else if (overlapLength2 >= deletion!!.length / 2.0 ||
                    overlapLength2 >= insertion!!.length / 2.0
                ) {
                    // Reverse overlap found.
                    // Insert an equality and swap and trim the surrounding edits.
                    pointer.previous()
                    pointer.add(
                        Diff(
                            Operation.EQUAL,
                            deletion.substring(0, overlapLength2)
                        )
                    )
                    prevDiff.operation = Operation.INSERT
                    prevDiff.text = insertion!!.substring(0, insertion.length - overlapLength2)
                    thisDiff.operation = Operation.DELETE
                    thisDiff.text = deletion.substring(overlapLength2)
                    // pointer.add inserts the element before the cursor, so there is
                    // no need to step past the new element.
                }
                thisDiff = if (pointer.hasNext()) pointer.next() else null
            }
            prevDiff = thisDiff
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }
    }

    /**
     * Look for single edits surrounded on both sides by equalities
     * which can be shifted sideways to align the edit to a word boundary.
     * e.g: `The c<ins>at c</ins>ame. -> The <ins>cat </ins>came.`
     *
     * @param diffs LinkedList of Diff objects.
     */
    fun diffCleanupSemanticLossless(diffs: LinkedList<Diff>) {
        var equality1: String?
        var edit: String?
        var equality2: String
        var commonString: String
        var commonOffset: Int
        var score: Int
        var bestScore: Int
        var bestEquality1: String?
        var bestEdit: String?
        var bestEquality2: String?
        // Create a new iterator at the start.
        val pointer = diffs.listIterator()
        var prevDiff = if (pointer.hasNext()) pointer.next() else null
        var thisDiff = if (pointer.hasNext()) pointer.next() else null
        var nextDiff = if (pointer.hasNext()) pointer.next() else null
        // Intentionally ignore the first and last element (don't need checking).
        while (nextDiff != null) {
            if (prevDiff!!.operation == Operation.EQUAL &&
                nextDiff.operation == Operation.EQUAL
            ) {
                // This is a single edit surrounded by equalities.
                equality1 = prevDiff.text
                edit = thisDiff!!.text
                equality2 = nextDiff!!.text!!

                // First, shift the edit as far left as possible.
                commonOffset = diffCommonSuffix(equality1, edit)
                if (commonOffset != 0) {
                    commonString = edit!!.substring(edit!!.length - commonOffset)
                    equality1 = equality1!!.substring(0, equality1!!.length - commonOffset)
                    edit = commonString + edit!!.substring(0, edit!!.length - commonOffset)
                    equality2 = commonString + equality2
                }

                // Second, step character by character right, looking for the best fit.
                bestEquality1 = equality1
                bestEdit = edit
                bestEquality2 = equality2
                bestScore = diffCleanupSemanticScore(equality1, edit) +
                        diffCleanupSemanticScore(edit, equality2)
                while (edit!!.length != 0 && equality2.length != 0 && edit[0]!! == equality2[0]) {
                    equality1 += edit[0]!!
                    edit = edit.substring(1) + equality2[0]
                    equality2 = equality2.substring(1)
                    score = diffCleanupSemanticScore(equality1, edit) +
                            diffCleanupSemanticScore(edit, equality2)
                    // The >= encourages trailing rather than leading whitespace on edits.
                    if (score >= bestScore) {
                        bestScore = score
                        bestEquality1 = equality1
                        bestEdit = edit
                        bestEquality2 = equality2
                    }
                }
                if (prevDiff.text != bestEquality1) {
                    // We have an improvement, save it back to the diff.
                    if (bestEquality1!!.length != 0) {
                        prevDiff.text = bestEquality1
                    } else {
                        pointer.previous() // Walk past nextDiff.
                        pointer.previous() // Walk past thisDiff.
                        pointer.previous() // Walk past prevDiff.
                        pointer.remove() // Delete prevDiff.
                        pointer.next() // Walk past thisDiff.
                        pointer.next() // Walk past nextDiff.
                    }
                    thisDiff.text = bestEdit
                    if (bestEquality2!!.length != 0) {
                        nextDiff.text = bestEquality2
                    } else {
                        pointer.remove() // Delete nextDiff.
                        nextDiff = thisDiff
                        thisDiff = prevDiff
                    }
                }
            }
            prevDiff = thisDiff
            thisDiff = nextDiff
            nextDiff = if (pointer.hasNext()) pointer.next() else null
        }
    }

    /**
     * Given two strings, compute a score representing whether the internal
     * boundary falls on logical boundaries.
     * Scores range from 6 (best) to 0 (worst).
     *
     * @param one First string.
     * @param two Second string.
     * @return The score.
     */
    private fun diffCleanupSemanticScore(one: String?, two: String?): Int {
        if (one!!.length == 0 || two!!.length == 0) {
            // Edges are the best.
            return 6
        }

        // Each port of this function behaves slightly differently due to
        // subtle differences in each language's definition of things like
        // 'whitespace'.  Since this function's purpose is largely cosmetic,
        // the choice has been made to use each language's native features
        // rather than force total conformity.
        val char1 = one[one.length - 1]
        val char2 = two!![0]
        val nonAlphaNumeric1 = !Character.isLetterOrDigit(char1)
        val nonAlphaNumeric2 = !Character.isLetterOrDigit(char2)
        val whitespace1 = nonAlphaNumeric1 && Character.isWhitespace(char1)
        val whitespace2 = nonAlphaNumeric2 && Character.isWhitespace(char2)
        val lineBreak1 = whitespace1 && Character.getType(char1) == Character.CONTROL.toInt()
        val lineBreak2 = whitespace2 && Character.getType(char2) == Character.CONTROL.toInt()
        val blankLine1 = lineBreak1 && BLANKLINEEND.matcher(one).find()
        val blankLine2 = lineBreak2 && BLANKLINESTART.matcher(two).find()
        if (blankLine1 || blankLine2) {
            // Five points for blank lines.
            return 5
        } else if (lineBreak1 || lineBreak2) {
            // Four points for line breaks.
            return 4
        } else if (nonAlphaNumeric1 && !whitespace1 && whitespace2) {
            // Three points for end of sentences.
            return 3
        } else if (whitespace1 || whitespace2) {
            // Two points for whitespace.
            return 2
        } else if (nonAlphaNumeric1 || nonAlphaNumeric2) {
            // One point for non-alphanumeric.
            return 1
        }
        return 0
    }

    // Define some regex patterns for matching boundaries.
    private val BLANKLINEEND = Pattern.compile("\\n\\r?\\n\\Z", Pattern.DOTALL)
    private val BLANKLINESTART = Pattern.compile("\\A\\r?\\n\\r?\\n", Pattern.DOTALL)

    /**
     * Reduce the number of edits by eliminating operationally trivial equalities.
     *
     * @param diffs LinkedList of Diff objects.
     */
    fun diffCleanupEfficiency(diffs: LinkedList<Diff>) {
        if (diffs.isEmpty()) {
            return
        }
        var changes = false
        val equalities: Deque<Diff> = ArrayDeque() // Double-ended queue of equalities.
        var lastEquality: String? = null // Always equal to equalities.peek().text
        val pointer = diffs.listIterator()
        // Is there an insertion operation before the last equality.
        var preIns = false
        // Is there a deletion operation before the last equality.
        var preDel = false
        // Is there an insertion operation after the last equality.
        var postIns = false
        // Is there a deletion operation after the last equality.
        var postDel = false
        var thisDiff: Diff? = pointer.next()
        var safeDiff = thisDiff // The last Diff that is known to be unsplittable.
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {
                // Equality found.
                if (thisDiff.text!!.length < diffEditCost && (postIns || postDel)) {
                    // Candidate found.
                    equalities.push(thisDiff)
                    preIns = postIns
                    preDel = postDel
                    lastEquality = thisDiff.text
                } else {
                    // Not a candidate, and can never become one.
                    equalities.clear()
                    lastEquality = null
                    safeDiff = thisDiff
                }
                postDel = false
                postIns = postDel
            } else {
                // An insertion or deletion.
                if (thisDiff.operation == Operation.DELETE) {
                    postDel = true
                } else {
                    postIns = true
                }
                /*
				 * Five types to be split:
				 * <ins>A</ins><del>B</del>XY<ins>C</ins><del>D</del>
				 * <ins>A</ins>X<ins>C</ins><del>D</del>
				 * <ins>A</ins><del>B</del>X<ins>C</ins>
				 * <ins>A</del>X<ins>C</ins><del>D</del>
				 * <ins>A</ins><del>B</del>X<del>C</del>
				 */
                if (lastEquality != null &&
                    (
                        (preIns && preDel && postIns && postDel) ||
                        (
                            lastEquality.length < diffEditCost / 2 &&
                            (
                                (if (preIns) 1 else 0) +
                                (if (postIns) 1 else 0) +
                                (if (preDel) 1 else 0) +
                                (if (postDel) 1 else 0)
                            ) == 3
                        )
                    )
                ) {
                    //System.out.println("Splitting: '" + lastEquality + "'");
                    // Walk back to offending equality.
                    while (thisDiff !== equalities.peek()) {
                        thisDiff = pointer.previous()
                    }
                    pointer.next()

                    // Replace equality with a delete.
                    pointer.set(Diff(Operation.DELETE, lastEquality))
                    // Insert a corresponding an insert.
                    pointer.add(Diff(Operation.INSERT, lastEquality).also {
                        thisDiff = it
                    })
                    equalities.pop() // Throw away the equality we just deleted.
                    lastEquality = null
                    if (preIns && preDel) {
                        // No changes made which could affect previous entry, keep going.
                        postDel = true
                        postIns = postDel
                        equalities.clear()
                        safeDiff = thisDiff
                    } else {
                        if (!equalities.isEmpty()) {
                            // Throw away the previous equality (it needs to be reevaluated).
                            equalities.pop()
                        }
                        if (equalities.isEmpty()) {
                            // There are no previous questionable equalities,
                            // walk back to the last known safe diff.
                            thisDiff = safeDiff
                        } else {
                            // There is an equality we can fall back to.
                            thisDiff = equalities.peek()
                        }
                        while (thisDiff !== pointer.previous()) {
                            // Intentionally empty loop.
                        }
                        postDel = false
                        postIns = postDel
                    }
                    changes = true
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }
        if (changes) {
            diffCleanupMerge(diffs)
        }
    }

    /**
     * Reorder and merge like edit sections. Merge equalities.
     * Any edit section can move as long as it doesn't cross an equality.
     *
     * @param diffs LinkedList of Diff objects.
     */
    fun diffCleanupMerge(diffs: LinkedList<Diff>) {
        diffs.add(Diff(Operation.EQUAL, "")) // Add a dummy entry at the end.
        var pointer = diffs.listIterator()
        var countDelete = 0
        var countInsert = 0
        var textDelete: String? = ""
        var textInsert: String? = ""
        var thisDiff: Diff? = pointer.next()
        var prevEqual: Diff? = null
        var commonlength: Int
        while (thisDiff != null) {
            when (thisDiff.operation) {
                Operation.INSERT -> {
                    countInsert++
                    textInsert += thisDiff.text
                    prevEqual = null
                }
                Operation.DELETE -> {
                    countDelete++
                    textDelete += thisDiff.text
                    prevEqual = null
                }
                Operation.EQUAL -> {
                    if (countDelete + countInsert > 1) {
                        val bothTypes = countDelete != 0 && countInsert != 0
                        // Delete the offending records.
                        pointer.previous() // Reverse direction.
                        while (countDelete-- > 0) {
                            pointer.previous()
                            pointer.remove()
                        }
                        while (countInsert-- > 0) {
                            pointer.previous()
                            pointer.remove()
                        }
                        if (bothTypes) {
                            // Factor out any common prefixies.
                            commonlength = diffCommonPrefix(textInsert, textDelete)
                            if (commonlength != 0) {
                                if (pointer.hasPrevious()) {
                                    thisDiff = pointer.previous()
                                    assert(thisDiff.operation == Operation.EQUAL) { "Previous diff should have been an equality." }
                                    thisDiff.text += textInsert!!.substring(0, commonlength)
                                    pointer.next()
                                } else {
                                    pointer.add(
                                        Diff(
                                            Operation.EQUAL,
                                            textInsert!!.substring(0, commonlength)
                                        )
                                    )
                                }
                                textInsert = textInsert.substring(commonlength)
                                textDelete = textDelete!!.substring(commonlength)
                            }
                            // Factor out any common suffixies.
                            commonlength = diffCommonSuffix(textInsert, textDelete)
                            if (commonlength != 0) {
                                thisDiff = pointer.next()
                                thisDiff.text = textInsert!!.substring(
                                    textInsert.length -
                                            commonlength
                                ) + thisDiff.text
                                textInsert = textInsert.substring(
                                    0, textInsert.length -
                                            commonlength
                                )
                                textDelete = textDelete!!.substring(
                                    0, textDelete.length -
                                            commonlength
                                )
                                pointer.previous()
                            }
                        }
                        // Insert the merged records.
                        if (textDelete!!.length != 0) {
                            pointer.add(Diff(Operation.DELETE, textDelete))
                        }
                        if (textInsert!!.length != 0) {
                            pointer.add(Diff(Operation.INSERT, textInsert))
                        }
                        // Step forward to the equality.
                        thisDiff = if (pointer.hasNext()) pointer.next() else null
                    } else if (prevEqual != null) {
                        // Merge this equality with the previous one.
                        prevEqual.text += thisDiff.text
                        pointer.remove()
                        thisDiff = pointer.previous()
                        pointer.next() // Forward direction
                    }
                    countInsert = 0
                    countDelete = 0
                    textDelete = ""
                    textInsert = ""
                    prevEqual = thisDiff
                }
            }
            thisDiff = if (pointer.hasNext()) pointer.next() else null
        }
        if (diffs.last.text!!.length == 0) {
            diffs.removeLast() // Remove the dummy entry at the end.
        }

        /*
		 * Second pass: look for single edits surrounded on both sides by equalities
		 * which can be shifted sideways to eliminate an equality.
		 * e.g: A<ins>BA</ins>C -> <ins>AB</ins>AC
		 */
        var changes = false
        // Create a new iterator at the start.
        // (As opposed to walking the current one back.)
        pointer = diffs.listIterator()
        var prevDiff = if (pointer.hasNext()) pointer.next() else null
        thisDiff = if (pointer.hasNext()) pointer.next() else null
        var nextDiff = if (pointer.hasNext()) pointer.next() else null
        // Intentionally ignore the first and last element (don't need checking).
        while (nextDiff != null) {
            if (prevDiff!!.operation == Operation.EQUAL &&
                nextDiff.operation == Operation.EQUAL
            ) {
                // This is a single edit surrounded by equalities.
                if (thisDiff!!.text!!.endsWith((prevDiff.text)!!)) {
                    // Shift the edit over the previous equality.
                    thisDiff.text = prevDiff.text +
                            thisDiff.text!!.substring(
                                0, thisDiff.text!!.length -
                                        prevDiff.text!!.length
                            )
                    nextDiff.text = prevDiff.text + nextDiff.text
                    pointer.previous() // Walk past nextDiff.
                    pointer.previous() // Walk past thisDiff.
                    pointer.previous() // Walk past prevDiff.
                    pointer.remove() // Delete prevDiff.
                    pointer.next() // Walk past thisDiff.
                    thisDiff = pointer.next() // Walk past nextDiff.
                    nextDiff = if (pointer.hasNext()) pointer.next() else null
                    changes = true
                } else if (thisDiff.text!!.startsWith((nextDiff.text)!!)) {
                    // Shift the edit over the next equality.
                    prevDiff.text += nextDiff.text
                    thisDiff.text = thisDiff.text!!.substring(nextDiff.text!!.length) +
                            nextDiff.text
                    pointer.remove() // Delete nextDiff.
                    nextDiff = if (pointer.hasNext()) pointer.next() else null
                    changes = true
                }
            }
            prevDiff = thisDiff
            thisDiff = nextDiff
            nextDiff = if (pointer.hasNext()) pointer.next() else null
        }
        // If shifts were made, the diff needs reordering and another shift sweep.
        if (changes) {
            diffCleanupMerge(diffs)
        }
    }

    /**
     * `loc` is a location in text1, compute and return the equivalent location in
     * text2.
     * e.g. "The cat" vs "The big cat", `1->1`, `5->8`
     *
     * @param diffs List of Diff objects.
     * @param loc   Location within text1.
     * @return Location within text2.
     */
    fun diffXIndex(diffs: List<Diff>, loc: Int): Int {
        var chars1 = 0
        var chars2 = 0
        var lastChars1 = 0
        var lastChars2 = 0
        var lastDiff: Diff? = null
        for (aDiff: Diff in diffs) {
            if (aDiff.operation != Operation.INSERT) {
                // Equality or deletion.
                chars1 += aDiff.text!!.length
            }
            if (aDiff.operation != Operation.DELETE) {
                // Equality or insertion.
                chars2 += aDiff.text!!.length
            }
            if (chars1 > loc) {
                // Overshot the location.
                lastDiff = aDiff
                break
            }
            lastChars1 = chars1
            lastChars2 = chars2
        }
        return if (lastDiff != null && lastDiff.operation == Operation.DELETE) {
            // The location was deleted.
            lastChars2
        } else lastChars2 + (loc - lastChars1)
        // Add the remaining character length.
    }

    /**
     * Convert a Diff list into a pretty HTML report.
     *
     * @param diffs List of Diff objects.
     * @return HTML representation.
     */
    fun diffPrettyHtml(diffs: List<Diff>): String {
        val html = StringBuilder()
        for (aDiff: Diff in diffs) {
            val text = aDiff.text!!.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", "&para;<br>")
            when (aDiff.operation) {
                Operation.INSERT -> html.append("<ins style=\"background:#e6ffe6;\">").append(text)
                    .append("</ins>")
                Operation.DELETE -> html.append("<del style=\"background:#ffe6e6;\">").append(text)
                    .append("</del>")
                Operation.EQUAL -> html.append("<span>").append(text).append("</span>")
            }
        }
        return html.toString()
    }

    /**
     * Compute and return the source text (all equalities and deletions).
     *
     * @param diffs List of Diff objects.
     * @return Source text.
     */
    fun diffText1(diffs: List<Diff>): String {
        val text = StringBuilder()
        for (aDiff: Diff in diffs) {
            if (aDiff.operation != Operation.INSERT) {
                text.append(aDiff.text)
            }
        }
        return text.toString()
    }

    /**
     * Compute and return the destination text (all equalities and insertions).
     *
     * @param diffs List of Diff objects.
     * @return Destination text.
     */
    fun diffText2(diffs: List<Diff>): String {
        val text = StringBuilder()
        for (aDiff: Diff in diffs) {
            if (aDiff.operation != Operation.DELETE) {
                text.append(aDiff.text)
            }
        }
        return text.toString()
    }

    /**
     * Compute the Levenshtein distance; the number of inserted, deleted or
     * substituted characters.
     *
     * @param diffs List of Diff objects.
     * @return Number of changes.
     */
    fun diffLevenshtein(diffs: List<Diff>): Int {
        var levenshtein = 0
        var insertions = 0
        var deletions = 0
        for (aDiff: Diff in diffs) {
            when (aDiff.operation) {
                Operation.INSERT -> insertions += aDiff.text!!.length
                Operation.DELETE -> deletions += aDiff.text!!.length
                Operation.EQUAL -> {
                    // A deletion and an insertion is one substitution.
                    levenshtein += Math.max(insertions, deletions)
                    insertions = 0
                    deletions = 0
                }
            }
        }
        levenshtein += Math.max(insertions, deletions)
        return levenshtein
    }

    /**
     * Crush the diff into an encoded string which describes the operations
     * required to transform text1 into text2.
     * E.g. `=3\t-2\t+ing ->` Keep 3 chars, delete 2 chars, insert 'ing'.
     * Operations are tab-separated. Inserted text is escaped using %xx notation.
     *
     * @param diffs List of Diff objects.
     * @return Delta text.
     */
    fun diffToDelta(diffs: List<Diff>): String {
        val text = StringBuilder()
        for (aDiff: Diff in diffs) {
            when (aDiff.operation) {
                Operation.INSERT -> try {
                    text.append("+").append(
                        URLEncoder.encode(aDiff.text, "UTF-8")
                            .replace('+', ' ')
                    ).append("\t")
                } catch (e: UnsupportedEncodingException) {
                    // Not likely on modern system.
                    throw Error("This system does not support UTF-8.", e)
                }
                Operation.DELETE -> text.append("-").append(aDiff.text!!.length).append("\t")
                Operation.EQUAL -> text.append("=").append(aDiff.text!!.length).append("\t")
            }
        }
        var delta = text.toString()
        if (delta.length != 0) {
            // Strip off trailing tab character.
            delta = delta.substring(0, delta.length - 1)
            delta = unescapeForEncodeUriCompatability(delta)
        }
        return delta
    }

    /**
     * Given the original text1, and an encoded string which describes the
     * operations required to transform text1 into text2, compute the full diff.
     *
     * @param text1 Source string for the diff.
     * @param delta Delta text.
     * @return Array of Diff objects or null if invalid.
     * @throws IllegalArgumentException If invalid input.
     */
    @Throws(IllegalArgumentException::class)
    fun diffFromDelta(text1: String, delta: String): LinkedList<Diff> {
        val diffs = LinkedList<Diff>()
        var pointer = 0 // Cursor in text1
        val tokens = delta.split("\t").toTypedArray()
        for (token: String in tokens) {
            if (token.length == 0) {
                // Blank tokens are ok (from a trailing \t).
                continue
            }
            // Each token begins with a one character parameter which specifies the
            // operation of this token (delete, insert, equality).
            var param = token.substring(1)
            when (token[0]) {
                '+' -> {
                    // decode would change all "+" to " "
                    param = param.replace("+", "%2B")
                    try {
                        param = URLDecoder.decode(param, "UTF-8")
                    } catch (e: UnsupportedEncodingException) {
                        // Not likely on modern system.
                        throw Error("This system does not support UTF-8.", e)
                    } catch (e: IllegalArgumentException) {
                        // Malformed URI sequence.
                        throw IllegalArgumentException(
                            "Illegal escape in diffFromDelta: $param", e
                        )
                    }
                    diffs.add(Diff(Operation.INSERT, param))
                }
                '-', '=' -> {
                    var n: Int
                    try {
                        n = param.toInt()
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException(
                            "Invalid number in diffFromDelta: $param", e
                        )
                    }
                    if (n < 0) {
                        throw IllegalArgumentException(
                            "Negative number in diffFromDelta: $param"
                        )
                    }
                    var text: String
                    try {
                        text = text1.substring(pointer, n.let { pointer += it; pointer })
                    } catch (e: StringIndexOutOfBoundsException) {
                        throw IllegalArgumentException(
                            ("Delta length (" + pointer +
                                    ") larger than source text length (" + text1.length +
                                    ")."), e
                        )
                    }
                    if (token[0] == '=') {
                        diffs.add(Diff(Operation.EQUAL, text))
                    } else {
                        diffs.add(Diff(Operation.DELETE, text))
                    }
                }
                else -> throw IllegalArgumentException(
                    "Invalid diff operation in diffFromDelta: " + token[0]
                )
            }
        }
        if (pointer != text1.length) {
            throw IllegalArgumentException(
                ("Delta length (" + pointer +
                        ") smaller than source text length (" + text1.length + ").")
            )
        }
        return diffs
    }
    //  MATCH FUNCTIONS
    /**
     * Locate the best instance of 'pattern' in 'text' near 'loc'.
     * Returns -1 if no match found.
     *
     * @param text    The text to search.
     * @param pattern The pattern to search for.
     * @param loc     The location to search around.
     * @return Best match index or -1.
     */
    fun matchMain(text: String?, pattern: String?, loc: Int): Int {
        // Check for null inputs.
        var loc = loc
        if (text == null || pattern == null) {
            throw IllegalArgumentException("Null inputs. (matchMain)")
        }
        loc = Math.max(0, Math.min(loc, text.length))
        if ((text == pattern)) {
            // Shortcut (potentially not guaranteed by the algorithm)
            return 0
        } else if (text.length == 0) {
            // Nothing to match.
            return -1
        } else return if (loc + pattern.length <= text.length && (text.substring(
                loc,
                loc + pattern.length
            ) == pattern)
        ) {
            // Perfect match at the perfect spot!  (Includes case of null pattern)
            loc
        } else {
            // Do a fuzzy compare.
            matchBitap(text, pattern, loc)
        }
    }

    /**
     * Locate the best instance of 'pattern' in 'text' near 'loc' using the
     * Bitap algorithm. Returns -1 if no match found.
     *
     * @param text    The text to search.
     * @param pattern The pattern to search for.
     * @param loc     The location to search around.
     * @return Best match index or -1.
     */
    fun matchBitap(text: String, pattern: String, loc: Int): Int {
        assert((MATCH_MAX_BITS.toInt() == 0 || pattern.length <= MATCH_MAX_BITS)) { "Pattern too long for this application." }

        // Initialise the alphabet.
        val s = matchAlphabet(pattern)

        // Highest score beyond which we give up.
        var scoreThreshold = matchThreshold.toDouble()
        // Is there a nearby exact match? (speedup)
        var bestLoc = text.indexOf(pattern, loc)
        if (bestLoc != -1) {
            scoreThreshold = Math.min(
                matchBitapScore(0, bestLoc, loc, pattern),
                scoreThreshold
            )
            // What about in the other direction? (speedup)
            bestLoc = text.lastIndexOf(pattern, loc + pattern.length)
            if (bestLoc != -1) {
                scoreThreshold = Math.min(
                    matchBitapScore(0, bestLoc, loc, pattern),
                    scoreThreshold
                )
            }
        }

        // Initialise the bit arrays.
        val matchmask = 1 shl (pattern.length - 1)
        bestLoc = -1
        var binMin: Int
        var binMid: Int
        var binMax = pattern.length + text.length
        // Empty initialization added to appease Java compiler.
        var lastRd = IntArray(0)
        for (d in 0 until pattern.length) {
            // Scan for the best match; each iteration allows for one more error.
            // Run a binary search to determine how far from 'loc' we can stray at
            // this error level.
            binMin = 0
            binMid = binMax
            while (binMin < binMid) {
                if (matchBitapScore(d, loc + binMid, loc, pattern) <=
                    scoreThreshold
                ) {
                    binMin = binMid
                } else {
                    binMax = binMid
                }
                binMid = (binMax - binMin) / 2 + binMin
            }
            // Use the result from this iteration as the maximum for the next.
            binMax = binMid
            var start = Math.max(1, loc - binMid + 1)
            val finish = Math.min(loc + binMid, text.length) + pattern.length
            val rd = IntArray(finish + 2)
            rd[finish + 1] = (1 shl d) - 1
            for (j in finish downTo start) {
                var charMatch: Int
                if (text.length <= j - 1 || !s.containsKey(text[j - 1])) {
                    // Out of range.
                    charMatch = 0
                } else {
                    charMatch = (s[text[j - 1]])!!
                }
                if (d == 0) {
                    // First pass: exact match.
                    rd[j] = ((rd[j + 1] shl 1) or 1) and charMatch
                } else {
                    // Subsequent passes: fuzzy match.
                    rd[j] = ((((rd[j + 1] shl 1) or 1) and charMatch) or
                            (((lastRd[j + 1] or lastRd[j]) shl 1) or 1) or lastRd[j + 1])
                }
                if ((rd[j] and matchmask) != 0) {
                    val score = matchBitapScore(d, j - 1, loc, pattern)
                    // This match will almost certainly be better than any existing
                    // match.  But check anyway.
                    if (score <= scoreThreshold) {
                        // Told you so.
                        scoreThreshold = score
                        bestLoc = j - 1
                        if (bestLoc > loc) {
                            // When passing loc, don't exceed our current distance from loc.
                            start = Math.max(1, 2 * loc - bestLoc)
                        } else {
                            // Already passed loc, downhill from here on in.
                            break
                        }
                    }
                }
            }
            if (matchBitapScore(d + 1, loc, loc, pattern) > scoreThreshold) {
                // No hope for a (better) match at greater error levels.
                break
            }
            lastRd = rd
        }
        return bestLoc
    }

    /**
     * Compute and return the score for a match with e errors and x location.
     *
     * @param e       Number of errors in match.
     * @param x       Location of match.
     * @param loc     Expected location of match.
     * @param pattern Pattern being sought.
     * @return Overall score for match (0.0 = good, 1.0 = bad).
     */
    private fun matchBitapScore(e: Int, x: Int, loc: Int, pattern: String): Double {
        val accuracy = e.toFloat() / pattern.length
        val proximity = Math.abs(loc - x)
        return if (matchDistance == 0) {
            // Dodge divide by zero error.
            if (proximity == 0) accuracy.toDouble() else 1.0
        } else (accuracy + (proximity / matchDistance.toFloat())).toDouble()
    }

    /**
     * Initialise the alphabet for the Bitap algorithm.
     *
     * @param pattern The text to encode.
     * @return Hash of character locations.
     */
    fun matchAlphabet(pattern: String): Map<Char, Int> {
        val s: MutableMap<Char, Int> = HashMap()
        val charPattern = pattern.toCharArray()
        for (c: Char in charPattern) {
            s[c] = 0
        }
        var i = 0
        for (c: Char in charPattern) {
            s[c] = s.get(c)!! or (1 shl (pattern.length - i - 1))
            i++
        }
        return s
    }
    //  PATCH FUNCTIONS
    /**
     * Increase the context until it is unique,
     * but don't let the pattern expand beyond `MATCH_MAX_BITS`.
     *
     * @param patch The patch to grow.
     * @param text  Source text.
     */
    protected fun patchAddContext(patch: Patch, text: String) {
        if (text.length == 0) {
            return
        }
        var pattern = text.substring(patch.start2, patch.start2 + patch.length1)
        var padding = 0

        // Look for the first and last matches of pattern in text.  If two different
        // matches are found, increase the pattern length.
        while (text.indexOf(pattern) != text.lastIndexOf(pattern) &&
            pattern.length < MATCH_MAX_BITS - patchMargin - patchMargin
        ) {
            padding += patchMargin.toInt()
            pattern = text.substring(
                Math.max(0, patch.start2 - padding),
                Math.min(text.length, patch.start2 + patch.length1 + padding)
            )
        }
        // Add one chunk for good luck.
        padding += patchMargin.toInt()

        // Add the prefix.
        val prefix = text.substring(
            Math.max(0, patch.start2 - padding),
            patch.start2
        )
        if (prefix.length != 0) {
            patch.diffs.addFirst(Diff(Operation.EQUAL, prefix))
        }
        // Add the suffix.
        val suffix = text.substring(
            patch.start2 + patch.length1,
            Math.min(text.length, patch.start2 + patch.length1 + padding)
        )
        if (suffix.length != 0) {
            patch.diffs.addLast(Diff(Operation.EQUAL, suffix))
        }

        // Roll back the start points.
        patch.start1 -= prefix.length
        patch.start2 -= prefix.length
        // Extend the lengths.
        patch.length1 += prefix.length + suffix.length
        patch.length2 += prefix.length + suffix.length
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * A set of diffs will be computed.
     *
     * @param text1 Old text.
     * @param text2 New text.
     * @return LinkedList of `Patch` objects.
     */
    fun patchMake(text1: String?, text2: String?): LinkedList<Patch> {
        if (text1 == null || text2 == null) {
            throw IllegalArgumentException("Null inputs. (patchMake)")
        }
        // No diffs provided, compute our own.
        val diffs = diffMain(text1, text2, true)
        if (diffs.size > 2) {
            diffCleanupSemantic(diffs)
            diffCleanupEfficiency(diffs)
        }
        return patchMake(text1, diffs)
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * text1 will be derived from the provided diffs.
     *
     * @param diffs Array of Diff objects for text1 to text2.
     * @return LinkedList of `Patch` objects.
     */
    fun patchMake(diffs: LinkedList<Diff>?): LinkedList<Patch> {
        if (diffs == null) {
            throw IllegalArgumentException("Null inputs. (patchMake)")
        }
        // No origin string provided, compute our own.
        val text1 = diffText1(diffs)
        return patchMake(text1, diffs)
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * text2 is ignored, diffs are the delta between text1 and text2.
     *
     * @param text1 Old text
     * @param text2 Ignored.
     * @param diffs Array of Diff objects for text1 to text2.
     * @return LinkedList of `Patch` objects.
     */
    @Deprecated("Prefer {@link #patchMake(String, LinkedList)}.")
    fun patchMake(
        text1: String?, text2: String?,
        diffs: LinkedList<Diff>?
    ): LinkedList<Patch> {
        return patchMake(text1, diffs)
    }

    /**
     * Compute a list of patches to turn text1 into text2.
     * text2 is not provided, diffs are the delta between text1 and text2.
     *
     * @param text1 Old text.
     * @param diffs Array of Diff objects for text1 to text2.
     * @return LinkedList of `Patch` objects.
     */
    fun patchMake(text1: String?, diffs: LinkedList<Diff>?): LinkedList<Patch> {
        if (text1 == null || diffs == null) {
            throw IllegalArgumentException("Null inputs. (patchMake)")
        }
        val patches = LinkedList<Patch>()
        if (diffs.isEmpty()) {
            return patches // Get rid of the null case.
        }
        var patch = Patch()
        var charCount1 = 0 // Number of characters into the text1 string.
        var charCount2 = 0 // Number of characters into the text2 string.
        // Start with text1 (prepatchText) and apply the diffs until we arrive at
        // text2 (postpatchText). We recreate the patches one by one to determine
        // context info.
        var prepatchText: String = text1
        var postpatchText: String = text1
        for (aDiff: Diff in diffs) {
            if (patch.diffs.isEmpty() && aDiff.operation != Operation.EQUAL) {
                // A new patch starts here.
                patch.start1 = charCount1
                patch.start2 = charCount2
            }
            when (aDiff.operation) {
                Operation.INSERT -> {
                    patch.diffs.add(aDiff)
                    patch.length2 += aDiff.text!!.length
                    postpatchText = (postpatchText.substring(0, charCount2) +
                            aDiff.text + postpatchText.substring(charCount2))
                }
                Operation.DELETE -> {
                    patch.length1 += aDiff.text!!.length
                    patch.diffs.add(aDiff)
                    postpatchText = postpatchText.substring(0, charCount2) +
                            postpatchText.substring(charCount2 + aDiff.text!!.length)
                }
                Operation.EQUAL -> {
                    if (((aDiff.text!!.length <= 2 * patchMargin) &&
                                !patch.diffs.isEmpty() && (aDiff !== diffs.last))
                    ) {
                        // Small equality inside a patch.
                        patch.diffs.add(aDiff)
                        patch.length1 += aDiff.text!!.length
                        patch.length2 += aDiff.text!!.length
                    }
                    if (aDiff.text!!.length >= 2 * patchMargin && !patch.diffs.isEmpty()) {
                        // Time for a new patch.
                        if (!patch.diffs.isEmpty()) {
                            patchAddContext(patch, prepatchText)
                            patches.add(patch)
                            patch = Patch()
                            // Unlike Unidiff, our patch lists have a rolling context.
                            // https://github.com/google/diff-match-patch/wiki/Unidiff
                            // Update prepatch text & pos to reflect the application of the
                            // just completed patch.
                            prepatchText = postpatchText
                            charCount1 = charCount2
                        }
                    }
                }
            }

            // Update the current character count.
            if (aDiff.operation != Operation.INSERT) {
                charCount1 += aDiff.text!!.length
            }
            if (aDiff.operation != Operation.DELETE) {
                charCount2 += aDiff.text!!.length
            }
        }
        // Pick up the leftover patch if not empty.
        if (!patch.diffs.isEmpty()) {
            patchAddContext(patch, prepatchText)
            patches.add(patch)
        }
        return patches
    }

    /**
     * Given an array of patches, return another array that is identical.
     *
     * @param patches Array of `Patch` objects.
     * @return Array of `Patch` objects.
     */
    fun patchDeepCopy(patches: LinkedList<Patch>): LinkedList<Patch> {
        val patchesCopy = LinkedList<Patch>()
        for (aPatch: Patch in patches) {
            val patchCopy = Patch()
            for (aDiff: Diff in aPatch.diffs) {
                val diffCopy = Diff(aDiff.operation, aDiff.text)
                patchCopy.diffs.add(diffCopy)
            }
            patchCopy.start1 = aPatch.start1
            patchCopy.start2 = aPatch.start2
            patchCopy.length1 = aPatch.length1
            patchCopy.length2 = aPatch.length2
            patchesCopy.add(patchCopy)
        }
        return patchesCopy
    }

    /**
     * Merge a set of patches onto the text. Return a patched text, as well
     * as an array of true/false values indicating which patches were applied.
     *
     * @param patches Array of `Patch` objects
     * @param text    Old text.
     * @return Two element Object array, containing the new text and an array of
     * boolean values.
     */
    fun patchApply(patches: LinkedList<Patch>, text: String): Array<Any> {
        var patches = patches
        var text = text
        if (patches.isEmpty()) {
            return arrayOf(
                text, BooleanArray(0)
            )
        }

        // Deep copy the patches so that no changes are made to originals.
        patches = patchDeepCopy(patches)
        val nullPadding = patchAddPadding(patches)
        text = nullPadding + text + nullPadding
        patchSplitMax(patches)
        var x = 0
        // delta keeps track of the offset between the expected and actual location
        // of the previous patch.  If there are patches expected at positions 10 and
        // 20, but the first patch was found at 12, delta is 2 and the second patch
        // has an effective expected position of 22.
        var delta = 0
        val results = BooleanArray(patches.size)
        for (aPatch: Patch in patches) {
            val expectedLoc = aPatch.start2 + delta
            val text1 = diffText1(aPatch.diffs)
            var startLoc: Int
            var endLoc = -1
            if (text1.length > MATCH_MAX_BITS) {
                // patchSplitMax will only provide an oversized pattern in the case of
                // a monster delete.
                startLoc = matchMain(
                    text,
                    text1.substring(0, MATCH_MAX_BITS.toInt()), expectedLoc
                )
                if (startLoc != -1) {
                    endLoc = matchMain(
                        text,
                        text1.substring(text1.length - MATCH_MAX_BITS),
                        expectedLoc + text1.length - MATCH_MAX_BITS
                    )
                    if (endLoc == -1 || startLoc >= endLoc) {
                        // Can't find valid trailing context.  Drop this patch.
                        startLoc = -1
                    }
                }
            } else {
                startLoc = matchMain(text, text1, expectedLoc)
            }
            if (startLoc == -1) {
                // No match found.  :(
                results[x] = false
                // Subtract the delta for this failed patch from subsequent patches.
                delta -= aPatch.length2 - aPatch.length1
            } else {
                // Found a match.  :)
                results[x] = true
                delta = startLoc - expectedLoc
                var text2: String
                if (endLoc == -1) {
                    text2 = text.substring(
                        startLoc,
                        Math.min(startLoc + text1.length, text.length)
                    )
                } else {
                    text2 = text.substring(
                        startLoc,
                        Math.min(endLoc + MATCH_MAX_BITS, text.length)
                    )
                }
                if ((text1 == text2)) {
                    // Perfect match, just shove the replacement text in.
                    text = (text.substring(0, startLoc) + diffText2(aPatch.diffs) +
                            text.substring(startLoc + text1.length))
                } else {
                    // Imperfect match.  Run a diff to get a framework of equivalent
                    // indices.
                    val diffs = diffMain(text1, text2, false)
                    if (text1.length > MATCH_MAX_BITS &&
                        diffLevenshtein(diffs) / text1.length.toFloat() >
                        patchDeleteThreshold
                    ) {
                        // The end points match, but the content is unacceptably bad.
                        results[x] = false
                    } else {
                        diffCleanupSemanticLossless(diffs)
                        var index1 = 0
                        for (aDiff: Diff in aPatch.diffs) {
                            if (aDiff.operation != Operation.EQUAL) {
                                val index2 = diffXIndex(diffs, index1)
                                if (aDiff.operation == Operation.INSERT) {
                                    // Insertion
                                    text = (text.substring(0, startLoc + index2) + aDiff.text +
                                            text.substring(startLoc + index2))
                                } else if (aDiff.operation == Operation.DELETE) {
                                    // Deletion
                                    text = text.substring(0, startLoc + index2) +
                                            text.substring(
                                                startLoc + diffXIndex(
                                                    diffs,
                                                    index1 + aDiff.text!!.length
                                                )
                                            )
                                }
                            }
                            if (aDiff.operation != Operation.DELETE) {
                                index1 += aDiff.text!!.length
                            }
                        }
                    }
                }
            }
            x++
        }
        // Strip the padding off.
        text = text.substring(
            nullPadding.length, text.length -
                    nullPadding.length
        )
        return arrayOf(
            text, results
        )
    }

    /**
     * Add some padding on text start and end so that edges can match something.
     * Intended to be called only from within [.patchApply].
     *
     * @param patches Array of `Patch` objects.
     * @return The padding string added to each side.
     */
    fun patchAddPadding(patches: LinkedList<Patch>): String {
        val paddingLength = patchMargin
        var nullPadding = ""
        for (x in 1..paddingLength) {
            nullPadding += x.toChar().toString()
        }

        // Bump all the patches forward.
        for (aPatch: Patch in patches) {
            aPatch.start1 += paddingLength.toInt()
            aPatch.start2 += paddingLength.toInt()
        }

        // Add some padding on start of first diff.
        var patch = patches.first
        var diffs = patch.diffs
        if (diffs.isEmpty() || diffs.first.operation != Operation.EQUAL) {
            // Add nullPadding equality.
            diffs.addFirst(Diff(Operation.EQUAL, nullPadding))
            patch.start1 -= paddingLength.toInt() // Should be 0.
            patch.start2 -= paddingLength.toInt() // Should be 0.
            patch.length1 += paddingLength.toInt()
            patch.length2 += paddingLength.toInt()
        } else if (paddingLength > diffs.first.text!!.length) {
            // Grow first equality.
            val firstDiff = diffs.first
            val extraLength = paddingLength - firstDiff.text!!.length
            firstDiff.text = nullPadding.substring(firstDiff.text!!.length) +
                    firstDiff.text
            patch.start1 -= extraLength
            patch.start2 -= extraLength
            patch.length1 += extraLength
            patch.length2 += extraLength
        }

        // Add some padding on end of last diff.
        patch = patches.last
        diffs = patch.diffs
        if (diffs.isEmpty() || diffs.last.operation != Operation.EQUAL) {
            // Add nullPadding equality.
            diffs.addLast(Diff(Operation.EQUAL, nullPadding))
            patch.length1 += paddingLength.toInt()
            patch.length2 += paddingLength.toInt()
        } else if (paddingLength > diffs.last.text!!.length) {
            // Grow last equality.
            val lastDiff = diffs.last
            val extraLength = paddingLength - lastDiff.text!!.length
            lastDiff.text += nullPadding.substring(0, extraLength)
            patch.length1 += extraLength
            patch.length2 += extraLength
        }
        return nullPadding
    }

    /**
     * Look through the patches and break up any which are longer than the
     * maximum limit of the match algorithm.
     * Intended to be called only from within [.patchApply].
     *
     * @param patches LinkedList of `Patch` objects.
     */
    fun patchSplitMax(patches: LinkedList<Patch>) {
        val patchSize = MATCH_MAX_BITS
        var precontext: String
        var postcontext: String
        var patch: Patch
        var start1: Int
        var start2: Int
        var empty: Boolean
        var diffType: Operation?
        var diffText: String?
        val pointer = patches.listIterator()
        var bigpatch = if (pointer.hasNext()) pointer.next() else null
        while (bigpatch != null) {
            if (bigpatch.length1 <= MATCH_MAX_BITS) {
                bigpatch = if (pointer.hasNext()) pointer.next() else null
                continue
            }
            // Remove the big old patch.
            pointer.remove()
            start1 = bigpatch.start1
            start2 = bigpatch.start2
            precontext = ""
            while (!bigpatch.diffs.isEmpty()) {
                // Create one of several smaller patches.
                patch = Patch()
                empty = true
                patch.start1 = start1 - precontext.length
                patch.start2 = start2 - precontext.length
                if (precontext.length != 0) {
                    patch.length2 = precontext.length
                    patch.length1 = patch.length2
                    patch.diffs.add(Diff(Operation.EQUAL, precontext))
                }
                while (!bigpatch.diffs.isEmpty() &&
                    patch.length1 < patchSize - patchMargin
                ) {
                    diffType = bigpatch.diffs.first.operation
                    diffText = bigpatch.diffs.first.text
                    if (diffType == Operation.INSERT) {
                        // Insertions are harmless.
                        patch.length2 += diffText!!.length
                        start2 += diffText!!.length
                        patch.diffs.addLast(bigpatch.diffs.removeFirst())
                        empty = false
                    } else if ((diffType == Operation.DELETE) && (patch.diffs.size == 1) && (
                                patch.diffs.first.operation == Operation.EQUAL) && (
                                diffText!!.length > 2 * patchSize)
                    ) {
                        // This is a large deletion.  Let it pass in one chunk.
                        patch.length1 += diffText!!.length
                        start1 += diffText!!.length
                        empty = false
                        patch.diffs.add(Diff(diffType, diffText))
                        bigpatch.diffs.removeFirst()
                    } else {
                        // Deletion or equality.  Only take as much as we can stomach.
                        diffText = diffText!!.substring(
                            0, Math.min(
                                diffText!!.length,
                                patchSize - patch.length1 - patchMargin
                            )
                        )
                        patch.length1 += diffText.length
                        start1 += diffText.length
                        if (diffType == Operation.EQUAL) {
                            patch.length2 += diffText.length
                            start2 += diffText.length
                        } else {
                            empty = false
                        }
                        patch.diffs.add(Diff(diffType, diffText))
                        if ((diffText == bigpatch.diffs.first.text)) {
                            bigpatch.diffs.removeFirst()
                        } else {
                            bigpatch.diffs.first.text = bigpatch.diffs.first.text!!
                                .substring(diffText.length)
                        }
                    }
                }
                // Compute the head context for the next patch.
                precontext = diffText2(patch.diffs)
                precontext = precontext.substring(
                    Math.max(
                        0, precontext.length -
                                patchMargin
                    )
                )
                // Append the end context for this patch.
                if (diffText1(bigpatch.diffs).length > patchMargin) {
                    postcontext = diffText1(bigpatch.diffs).substring(0, patchMargin.toInt())
                } else {
                    postcontext = diffText1(bigpatch.diffs)
                }
                if (postcontext.length != 0) {
                    patch.length1 += postcontext.length
                    patch.length2 += postcontext.length
                    if (!patch.diffs.isEmpty() &&
                        patch.diffs.last.operation == Operation.EQUAL
                    ) {
                        patch.diffs.last.text += postcontext
                    } else {
                        patch.diffs.add(Diff(Operation.EQUAL, postcontext))
                    }
                }
                if (!empty) {
                    pointer.add(patch)
                }
            }
            bigpatch = if (pointer.hasNext()) pointer.next() else null
        }
    }

    /**
     * Take a list of patches and return a textual representation.
     *
     * @param patches List of `Patch` objects.
     * @return Text representation of patches.
     */
    fun patchToText(patches: List<Patch?>): String {
        val text = StringBuilder()
        for (aPatch: Patch? in patches) {
            text.append(aPatch)
        }
        return text.toString()
    }

    /**
     * Parse a textual representation of patches and return a List of `Patch` objects.
     *
     * @param textline Text representation of patches.
     * @return List of `Patch` objects.
     * @throws IllegalArgumentException If invalid input.
     */
    @Throws(IllegalArgumentException::class)
    fun patchFromText(textline: String): List<Patch> {
        val patches: MutableList<Patch> = LinkedList()
        if (textline.length == 0) {
            return patches
        }
        val textList = Arrays.asList(*textline.split("\n").toTypedArray())
        val text = LinkedList(textList)
        var patch: Patch
        val patchHeader = Pattern.compile("^@@ -(\\d+),?(\\d*) \\+(\\d+),?(\\d*) @@$")
        var m: Matcher
        var sign: Char
        var line: String
        while (!text.isEmpty()) {
            m = patchHeader.matcher(text.first)
            if (!m.matches()) {
                throw IllegalArgumentException(
                    "Invalid patch string: " + text.first
                )
            }
            patch = Patch()
            patches.add(patch)
            patch.start1 = m.group(1).toInt()
            if (m.group(2).length == 0) {
                patch.start1--
                patch.length1 = 1
            } else if ((m.group(2) == "0")) {
                patch.length1 = 0
            } else {
                patch.start1--
                patch.length1 = m.group(2).toInt()
            }
            patch.start2 = m.group(3).toInt()
            if (m.group(4).length == 0) {
                patch.start2--
                patch.length2 = 1
            } else if ((m.group(4) == "0")) {
                patch.length2 = 0
            } else {
                patch.start2--
                patch.length2 = m.group(4).toInt()
            }
            text.removeFirst()
            while (!text.isEmpty()) {
                try {
                    sign = text.first[0]
                } catch (e: IndexOutOfBoundsException) {
                    // Blank line?  Whatever.
                    text.removeFirst()
                    continue
                }
                line = text.first.substring(1)
                line = line.replace("+", "%2B") // decode would change all "+" to " "
                try {
                    line = URLDecoder.decode(line, "UTF-8")
                } catch (e: UnsupportedEncodingException) {
                    // Not likely on modern system.
                    throw Error("This system does not support UTF-8.", e)
                } catch (e: IllegalArgumentException) {
                    // Malformed URI sequence.
                    throw IllegalArgumentException(
                        "Illegal escape in patchFromText: $line", e
                    )
                }
                if (sign == '-') {
                    // Deletion.
                    patch.diffs.add(Diff(Operation.DELETE, line))
                } else if (sign == '+') {
                    // Insertion.
                    patch.diffs.add(Diff(Operation.INSERT, line))
                } else if (sign == ' ') {
                    // Minor equality.
                    patch.diffs.add(Diff(Operation.EQUAL, line))
                } else if (sign == '@') {
                    // Start of next patch.
                    break
                } else {
                    // WTF?
                    throw IllegalArgumentException(
                        "Invalid patch mode '$sign' in: $line"
                    )
                }
                text.removeFirst()
            }
        }
        return patches
    }

    /**
     * Class representing one diff operation.
     */
    class Diff(
        /**
         * One of: INSERT, DELETE or EQUAL.
         */
        var operation: Operation?,
        /**
         * The text associated with this diff operation.
         */
        var text: String?
    ) {
        /**
         * Display a human-readable version of this Diff.
         *
         * @return text version.
         */
        override fun toString(): String {
            val prettyText = text!!.replace('\n', '\u00b6')
            return "Diff(" + operation + ",\"" + prettyText + "\")"
        }

        /**
         * Create a numeric hash value for a Diff.
         * This function is not used by DMP.
         *
         * @return Hash value.
         */
        override fun hashCode(): Int {
            val prime = 31
            var result = if ((operation == null)) 0 else operation.hashCode()
            result += prime * (if ((text == null)) 0 else text.hashCode())
            return result
        }

        /**
         * Is this Diff equivalent to another Diff?
         *
         * @param obj Another Diff to compare against.
         * @return true or false.
         */
        override fun equals(obj: Any?): Boolean {
            if (this === obj) {
                return true
            }
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as Diff
            if (operation != other.operation) {
                return false
            }
            if (text == null) {
                if (other.text != null) {
                    return false
                }
            } else if (text != other.text) {
                return false
            }
            return true
        }

        /**
         * Constructor. Initializes the diff with the provided values.
         *
         * @param operation One of INSERT, DELETE or EQUAL.
         * @param text      The text being applied.
         */
        init {
            // Construct a diff with the specified operation and text.
            text = text
        }
    }

    /**
     * Class representing one patch operation.
     */
    class Patch() {
        var diffs: LinkedList<Diff>
        var start1 = 0
        var start2 = 0
        var length1 = 0
        var length2 = 0

        /**
         * Emulate GNU diff's format.
         * Header: @@ -382,8 +481,9 @@
         * Indices are printed as 1-based, not 0-based.
         *
         * @return The GNU diff string.
         */
        override fun toString(): String {
            val coords1: String
            val coords2: String
            if (length1 == 0) {
                coords1 = start1.toString() + ",0"
            } else if (length1 == 1) {
                coords1 = Integer.toString(start1 + 1)
            } else {
                coords1 = (start1 + 1).toString() + "," + length1
            }
            if (length2 == 0) {
                coords2 = start2.toString() + ",0"
            } else if (length2 == 1) {
                coords2 = Integer.toString(start2 + 1)
            } else {
                coords2 = (start2 + 1).toString() + "," + length2
            }
            val text = StringBuilder()
            text.append("@@ -").append(coords1).append(" +").append(coords2)
                .append(" @@\n")
            // Escape the body of the patch with %xx notation.
            for (aDiff: Diff in diffs) {
                when (aDiff.operation) {
                    Operation.INSERT -> text.append('+')
                    Operation.DELETE -> text.append('-')
                    Operation.EQUAL -> text.append(' ')
                }
                try {
                    text.append(URLEncoder.encode(aDiff.text, "UTF-8").replace('+', ' '))
                        .append("\n")
                } catch (e: UnsupportedEncodingException) {
                    // Not likely on modern system.
                    throw Error("This system does not support UTF-8.", e)
                }
            }
            return unescapeForEncodeUriCompatability(text.toString())
        }

        /**
         * Constructor. Initializes with an empty list of diffs.
         */
        init {
            diffs = LinkedList()
        }
    }

    companion object {
        /**
         * The number of bits in an int.
         */
        private val MATCH_MAX_BITS: Short = 32

        /**
         * Unescape selected chars for compatibility with JavaScript's encodeURI.
         * In speed critical applications this could be dropped since the
         * receiving application will certainly decode these fine.
         * Note that this function is case-sensitive. Thus "%3f" would not be
         * unescaped. But this is ok because it is only called with the output of
         * URLEncoder.encode which returns uppercase hex.
         *
         *
         * Example: "%3F" -> "?", "%24" -> "$", etc.
         *
         * @param str The string to escape.
         * @return The escaped string.
         */
        private fun unescapeForEncodeUriCompatability(str: String): String {
            return str.replace("%21", "!").replace("%7E", "~")
                .replace("%27", "'").replace("%28", "(").replace("%29", ")")
                .replace("%3B", ";").replace("%2F", "/").replace("%3F", "?")
                .replace("%3A", ":").replace("%40", "@").replace("%26", "&")
                .replace("%3D", "=").replace("%2B", "+").replace("%24", "$")
                .replace("%2C", ",").replace("%23", "#")
        }
    }
}
