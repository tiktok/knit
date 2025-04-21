package knit.test

import knit.Component
import knit.Provides
import knit.di
import knit.test.base.KnitTestCase
import knit.test.base.readContainers4
import knit.test.base.toContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tiktok.knit.plugin.TypeConflictInCompositeException
import tiktok.knit.plugin.fqn

/**
 * Created by yuejunyu on 2023/10/24
 * @author yuejunyu.0
 */
class ComponentCheckerTest : KnitTestCase {
    @Component
    class Comp {
        @Provides
        val s: String = "s"
    }

    @Component
    class Parent1 {
        @Component
        val c: Comp = Comp()
    }

    @Component
    class Parent2 {
        @Component
        val c: Comp = Comp()
    }

    @Component
    class Child {
        @Component
        val c: Comp = Comp()

        @Component
        val p1: Parent1 = Parent1()

        @Component
        val p2: Parent2 = Parent2()

        private val s: String by di
    }

    @Test
    fun `check component duplicated`() {
        val containers = readContainers4<Comp, Parent1, Parent2, Child>()
        val e = assertThrows<TypeConflictInCompositeException> {
            containers.toContext()
        }
        val expected = """
            In component ${Child::class.fqn}, Multiple(3) type injections has conflicts in different composite component.
            
            Type: ${String::class.fqn}, conflict providers for this component:
            
            COMPOSITE ${Comp::class.fqn}.getS -> ${String::class.fqn}
              there has multiple paths to provides this injection: [
                getC -> getS,
                getP1 -> getC -> getS,
                getP2 -> getC -> getS
              ]
        """.trimIndent()
        Assertions.assertEquals(expected, e.message)
    }
}