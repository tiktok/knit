package knit.demo

import knit.Component
import knit.Provides
import knit.di
import java.util.Scanner

// CLI interactive system
@Provides
class SampleCli(
    @Component val storeComponent: MemoryStoreComponent,
    @Component val monitorComponent: MonitorComponent,
) {
    private val scanner = Scanner(System.`in`)
    private var running = true

    private val fileSystem: MemoryFileSystem by di
    private val refManager: MemoryReferenceManager by di
    private val commandRegistry: CommandRegistry by di

    fun start() {
        while (running) {
            val currentDir = fileSystem.getCurrentDirectory()
            val branch = refManager.listBranches()
                .find { it.sha == refManager.getCurrentCommitSha() }
                ?.getShortName() ?: "HEAD"

            print("git:$branch:${currentDir.path} > ")
            val input = scanner.nextLine().trim()

            if (input.isEmpty()) continue

            val parts = input.split(" ")
            val command = parts[0]
            val args = parts.drop(1)

            when (command) {
                "exit", "quit" -> {
                    running = false
                }

                "help" -> println(commandRegistry.getHelp())
                else -> executeCommand(command, args)
            }
        }
    }

    private fun executeCommand(command: String, args: List<String>) {
        try {
            when (val result = commandRegistry.executeCommand(command, args)) {
                is CommandResult.Success -> println(result.message)
                is CommandResult.Error -> println(result.message)
                is CommandResult.Info -> println(result.data)
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
