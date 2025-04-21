package knit.test

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import knit.Component
import knit.KnitViewModel
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

    @Component
    class VMContainer : Fragment() {
        private val vm by knitViewModel<CustomVM>()

        @Provides
        val myStr: String = "114514"
        fun validate() {
            Assertions.assertEquals(myStr, vm.str)
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
        val obj0 = loader.new<VMContainer>()["validate"]().obj
        val obj1 = loader.new<VMContainer>()["validate"]().obj
        Assertions.assertTrue(obj0 === obj1)
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
}