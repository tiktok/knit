package knit.demo

import knit.Provides

@Provides
class CommandRegistry(
    private val gitCommands: List<GitCommand>,
    private val basicCommands: List<BasicCommand>,
) {
    private val commandsMap = mutableMapOf<String, Any>()
    
    init {
        // Register all Git commands
        gitCommands.forEach { command ->
            commandsMap[command.name] = command
        }
        
        // Register all basic commands
        basicCommands.forEach { command ->
            commandsMap[command.name] = command
        }
    }
    
    fun getCommand(name: String): Any? = commandsMap[name]
    
    val allCommands: Set<String> = commandsMap.keys
    
    fun executeCommand(commandName: String, args: List<String>): CommandResult {
        return when (val command = getCommand(commandName)) {
            is GitCommand -> command.execute(args)
            is BasicCommand -> command.execute(args)
            else -> CommandResult.Error("Unknown command: $commandName")
        }
    }
    
    fun getHelp(): String {
        val help = StringBuilder()
        help.appendLine("Available commands:")
        help.appendLine()
        
        help.appendLine("Git Commands:")
        gitCommands.forEach { command ->
            help.appendLine("  ${command.name} - ${command.getHelp()}")
        }
        
        help.appendLine()
        help.appendLine("Basic Commands:")
        basicCommands.forEach { command ->
            help.appendLine("  ${command.name} - ${command.getHelp()}")
        }
        
        return help.toString()
    }
}
