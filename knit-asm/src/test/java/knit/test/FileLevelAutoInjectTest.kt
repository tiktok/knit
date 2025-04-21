package knit.test

import knit.internal.GlobalProvides
import knit.test.base.KnitTestCase
import knit.test.base.readAsNode
import knit.test.base.readContainers2
import knit.test.base.toContext
import knit.test.sample.FileLevelAutoDetectSample
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.asMetadataContainer

/**
 * Created by yuejunyu on 2024/7/26
 * @author yuejunyu.0
 */
class FileLevelAutoInjectTest : KnitTestCase {
    @Test
    fun `test inject file level`() {
        val sampleClass = FileLevelAutoDetectSample::class.java
        val fooClassName = sampleClass.name + "Kt"
        val containers = readContainers2<GlobalProvides, FileLevelAutoDetectSample>() +
            requireNotNull(readAsNode(fooClassName).asMetadataContainer())
        val loader = containers.toContext().toClassLoader()
        val clazz = loader.loadClass(fooClassName)
        val redefinedSampleClass = loader.loadClass(sampleClass.name)
        val verifyField = redefinedSampleClass.getField("verifyValue")
        val fieldGetter = clazz.getMethod("getFileLevelInjected")
        val injectedObj = fieldGetter.invoke(null)
        verifyField.isAccessible = true
        val value = verifyField.get(injectedObj)
        Assertions.assertEquals(1145, value)
    }
}