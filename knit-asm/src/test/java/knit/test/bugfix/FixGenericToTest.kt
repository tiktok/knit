package knit.test.bugfix

import knit.Component
import knit.test.base.KnitTestCase
import knit.test.base.readContainers
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.KnitSimpleError

/**
 * Created by yuejunyu on 2024/8/27
 * @author yuejunyu.0
 */

class FixGenericToTest : KnitTestCase {
    open class BizBaseData

    @Component
    abstract class ProfilePlatformProtocol<T : BizBaseData>

    abstract class NavbarPlatformProtocol<T : BizBaseData> : ProfilePlatformProtocol<T>()

    @Component
    class ProfileNavbarMenuProtocol : NavbarPlatformProtocol<BizBaseData>()

    @Test
    fun `class with type parameter parent`() {
        val exception = assertThrows<KnitSimpleError> {
            readContainers(
                ProfileNavbarMenuProtocol::class, ProfilePlatformProtocol::class,
            ).toContext()
        }
        val cause = exception.cause?.cause as KnitSimpleError
        val message = "Please add the @Component annotation for ${NavbarPlatformProtocol::class.java.name} and " +
            "${ProfilePlatformProtocol::class.java.name}. :)"
        Assertions.assertEquals(message, cause.message)
    }
}