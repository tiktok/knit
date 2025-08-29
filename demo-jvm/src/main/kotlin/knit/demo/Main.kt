package knit.demo

import knit.di


class MemoryGitApplication {
    private val cli: SampleCli by di
    fun start() = cli.start()
}

fun main() = MemoryGitApplication().start()