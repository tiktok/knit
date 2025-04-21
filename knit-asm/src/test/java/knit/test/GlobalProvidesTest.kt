package knit.test

import knit.Provides
import knit.Singleton
import knit.internal.GlobalProvides
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.new
import knit.test.base.readContainer
import knit.test.base.readContainers4
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.writer.GlobalProvidesWriter
import tiktok.knit.plugin.internalName

/**
 * Created by yuejunyu on 2023/7/4
 * @author yuejunyu.0
 */
@TestTargetClass(GlobalProvidesWriter::class)
class GlobalProvidesTest : KnitTestCase {
    @Test
    fun `test none global singleton node`() {
        val containers = readContainer<GlobalProvidesTest>()
        val context = containers.toContext()
        val writer = GlobalProvidesWriter(context)
        val e = assertThrows<IllegalArgumentException> {
            writer.write(containers[0].node)
        }
        Assertions.assertEquals(
            "GlobalSingletonWriter's node name must be ${GlobalProvides::class.internalName}, actual: ${GlobalProvidesTest::class.internalName}",
            e.message,
        )
    }

    @Provides
    @Singleton(false)
    class UnsafetyProvides

    @Provides
    @Singleton(true)
    class SafetyProvides

    @Test
    fun `test write global singleton safety`() {
        val containers = readContainers4<
            GlobalProvides, GlobalProvidesTest,
            SafetyProvides, UnsafetyProvides,
            >()
        val classloader = containers.toContext().toClassLoader()
        classloader.new<GlobalProvides>()
    }
}