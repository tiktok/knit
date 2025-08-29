package knit.demo

import knit.Component
import knit.Provides

@Component
@Provides
class Test {
    private val testValue: String = "default"

    fun runTest() {
        println("testing code")
        println("testing incremental nature")
        return
    }
}