package knit.demo

import knit.di

class MemoryGitApplication {
    private val cli: SampleCli by di
    private val test: Test by di
    fun start() {
        test.runTest()
        cli.start()
    }
}

fun main() = MemoryGitApplication().start()