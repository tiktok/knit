package knit.test

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import knit.Component
import knit.IgnoreInjection
import knit.KnitExperimental
import knit.KnitViewModel
import knit.Priority
import knit.Provides
import knit.android.internal.VMPFactoryImpl
import knit.android.knitViewModel
import knit.internal.GlobalProvides
import knit.test.base.KnitTestCase
import knit.test.base.TestTargetClass
import knit.test.base.knitTypeOf
import knit.test.base.new
import knit.test.base.readContainers3
import knit.test.base.readContainers5
import knit.test.base.readContainers7
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.NoProvidesFoundException
import tiktok.knit.plugin.internalName
import tiktok.knit.plugin.writer.ComponentWriter

/**
 * Created by yuejunyu on 2023/7/26
 * @author yuejunyu.0
 */
@TestTargetClass(ComponentWriter::class)
class VMInjectTest : KnitTestCase {
    class CustomVM @KnitViewModel constructor(val str: String) : ViewModel()
    class ChildVM @KnitViewModel constructor(val str: String) : ViewModel()

    @Component
    open class VMContainer : Fragment() {
        protected val vm by knitViewModel<CustomVM>()

        @Provides
        val myStr: String = "114514"
        fun validate() {
            Assertions.assertEquals(myStr, vm.str)
        }
    }

    @Component
    class ChildContainer : VMContainer() {
        private val childVM by knitViewModel<ChildVM>()

        fun validateChild() {
            Assertions.assertEquals(myStr, vm.str)
            Assertions.assertEquals(myStr, childVM.str)
        }
    }

    @Component
    class VMContainerFails : Fragment() {
        private val vm by knitViewModel<CustomVM>()
    }

    @Test
    fun `test view model injection`() {
        val containers = readContainers5<
            CustomVM, VMContainer, GlobalProvides, VMInjectTest, VMPFactoryImpl,
            >()
        val loader = containers.toContext().toClassLoader()
        loader.new<VMContainer>()["validate"]()
    }

    @Test
    fun `test child view model injection`() {
        val containers = readContainers7<
            CustomVM, VMContainer, GlobalProvides, VMInjectTest, VMPFactoryImpl,
            ChildContainer, ChildVM,
            >()
        val loader = containers.toContext().toClassLoader()
        loader.new<ChildContainer>()["validateChild"]()
    }

    @Test
    fun `test fails when cannot injected`() {
        val containers = readContainers3<CustomVM, VMContainerFails, GlobalProvides>()
        val e = assertThrows<NoProvidesFoundException> { containers.toContext().toClassLoader() }
        val expected = NoProvidesFoundException(
            VMContainerFails::class.internalName, knitTypeOf<String>(), emptyList(),
        )
        Assertions.assertEquals(expected.message, e.message)
    }

    class IgnoreInjectionTestTarget : VMContainer() {
        @IgnoreInjection
        private val childVM by knitViewModel<ChildVM>()
    }

    @Test
    fun `test vm injection is ignored`() {
        val containers = readContainers3<IgnoreInjectionTestTarget, ChildVM, GlobalProvides>()
        containers.toContext().toClassLoader()
    }

    abstract class PrVM(val str: String) : ViewModel()
    class VMLowPr @KnitViewModel(PrVM::class) constructor(str: String) : PrVM(str)

    @OptIn(KnitExperimental::class)
    class VMHighPr @KnitViewModel(PrVM::class) @Priority(1) constructor(str: String) : PrVM(str)

    class VMPrTest : Fragment() {
        private val vm by knitViewModel<PrVM>()

        @Provides
        val myStr: String = "114515"
        fun validate() {
            Assertions.assertInstanceOf(VMHighPr::class.java, vm)
            Assertions.assertEquals(myStr, vm.str)
        }
    }

    @Test
    fun `test vm injection priority`() {
        val containers = readContainers5<PrVM, VMLowPr, VMHighPr, VMPrTest, GlobalProvides>()
        val loader = containers.toContext().toClassLoader()
        loader.new<VMPrTest>()["validate"]()
    }
}
