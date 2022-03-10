package com.github.andronikusgametech.ijreplaymod.replay

import com.github.andronikusgametech.ijreplaymod.stopper.ReplayStopFlag
import com.github.andronikusgametech.ijreplaymod.util.IDocumentMutator
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import org.mockito.Mockito
import java.lang.IllegalArgumentException

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class ReplayActorTest : BasePlatformTestCase() {

    private lateinit var documentMutator: IDocumentMutator
    private lateinit var objectUnderTest: ReplayActor

    override fun setUp() {
        super.setUp()
        documentMutator = Mockito.mock(IDocumentMutator::class.java)
        objectUnderTest = ReplayActor(documentMutator, ReplayStopFlag())
    }

    fun testRun_happyPath() {
        val textVersions = listOf(
            readFile("TestFile0.java.txt"),
            readFile("TestFile1.java.txt")
        )
        objectUnderTest.run(textVersions)

        Mockito.verify(documentMutator, Mockito.times(1)).setText(
            "package com.andronikus.test.util;\n" +
            "\n" +
            "public class TestClass {\n" +
            "}"
        )
        Mockito.verify(documentMutator, Mockito.times(1)).deleteSegment(52, 53)
        Mockito.verify(documentMutator, Mockito.times(1)).writeSegment("Fi", 52)
        Mockito.verify(documentMutator, Mockito.times(1)).writeSegment(
            "e {\n" +
            "\n" +
            "    priv",
            55
        )
        Mockito.verify(documentMutator, Mockito.times(1)).deleteSegment(69, 71)
        Mockito.verify(documentMutator, Mockito.times(1)).writeSegment("te int", 69)
        Mockito.verify(documentMutator, Mockito.times(1)).deleteSegment(76, 77)
        Mockito.verify(documentMutator, Mockito.times(1)).writeSegment("a;", 76)
    }

    fun testRun_notEnoughVersions() {
        val textVersions = listOf("")
        try {
            objectUnderTest.run(textVersions)
            Assert.assertFalse("Run was supposed to fail.", true)
        } catch(exception: IllegalArgumentException) {}
    }

    private fun readFile(fileName: String): String {
        return ReplayActorTest::class.java.classLoader.getResource(fileName)
            .readText()
            .replace("${13.toChar()}", "")
    }
}