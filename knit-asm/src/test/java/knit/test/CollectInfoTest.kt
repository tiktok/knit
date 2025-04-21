package knit.test

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import knit.Component
import knit.Provides
import knit.android.knitViewModel
import knit.di
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.readAsNode
import knit.test.base.readContainer
import knit.test.base.readContainers3
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tiktok.knit.plugin.CollectInfo

/**
 * Created by yuejunyu on 2023/7/4
 * @author yuejunyu.0
 */
@TestTargetClass(CollectInfo::class)
class CollectInfoTest : KnitTestCase {
    @Component
    class ComponentSample

    interface InterfaceComponent {
        @Provides
        fun providesString(): String
    }

    interface InterfaceWithComposite {
        @get:Component
        val parentComponent: ComponentSample
    }

    @Test
    fun `test success collect info`() {
        val context = readContainers3<ComponentSample, InterfaceComponent, InterfaceWithComposite>()
            .toContext()
        val collectInfo = CollectInfo(context)
        for (node in context.nodes) {
            collectInfo.collect(node)
        }
    }

    @Test
    fun `test none collect info`() {
        val context = readContainer<CollectInfoTest>().toContext()
        val collectInfo = CollectInfo(context)
        for (node in context.nodes) {
            collectInfo.collect(node)
        }
    }

    class CannotAutoCollectedSample {
        val str: String by lazy { "s" }
    }

    class AutoCollectSample {
        val sample: String by di
    }

    class AutoCollectVMSample : Fragment() {
        val vm: SimpleVM by knitViewModel()
    }

    @Test
    fun `test auto collect`() {
        val cannotAutoCollectedSample = readAsNode(CannotAutoCollectedSample::class.java)
        Assertions.assertFalse(CollectInfo.needProcess(cannotAutoCollectedSample))
        val autoCollectedSample = readAsNode(AutoCollectSample::class.java)
        Assertions.assertTrue(CollectInfo.needProcess(autoCollectedSample))
        val autoCollectVMSample = readAsNode(AutoCollectVMSample::class.java)
        Assertions.assertTrue(CollectInfo.needProcess(autoCollectVMSample))
    }

    class SimpleVM : ViewModel()

}