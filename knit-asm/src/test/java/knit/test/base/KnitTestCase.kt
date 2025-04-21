package knit.test.base

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import tiktok.knit.plugin.ILogger
import tiktok.knit.plugin.Logger

/**
 * Created by yuejunyu on 2023/6/9
 * @author yuejunyu.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KnitTestInterceptor::class)
interface KnitTestCase {
    @BeforeAll
    fun beforeAll() {
        Logger.delegate = TestLogger
    }

    @AfterAll
    fun afterAll() {
        Logger.delegate = null
    }
}

object TestLogger : ILogger {
    override fun i(tag: String, information: String) {
        println("[$tag] $information")
    }

    override fun w(tag: String, warning: String, t: Throwable?) {
        System.err.println("[$tag] $warning")
        t?.printStackTrace()
    }

    override fun e(tag: String, error: String, t: Throwable?) {
        System.err.println("[$tag] $error")
        t?.printStackTrace()
    }
}
