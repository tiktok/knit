package knit.demo

import knit.Component
import knit.Provides

@Provides
class Test {
    private val testValue: String = "default"

    fun runTest() {
        println("testing code")
        println("testing incremental nature")
    // keep some logic so the method does work
    check(testValue.isNotEmpty())
        return
    }
}