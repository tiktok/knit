package knit.demo

import knit.Component
import knit.Provides

@Component
@Provides
class Test {
    private val app: MemoryGitApplication by knit.di
    private val testValue: String = "default"

    fun runTest() {
        println("testing code")
        println("testing incremental nature")
        // touch the app to ensure the DI edge is realized
        if (testValue.isNotEmpty()) app.toString()
        return
    }
}