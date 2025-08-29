package knit.demo

import knit.IntoList
import knit.Provides

// Basic command interface
interface BasicCommand {
    val name: String
    fun execute(args: List<String>): CommandResult
    fun getHelp(): String
}

@Provides(BasicCommand::class)
@IntoList
class CdCommand(
    private val fileSystem: MemoryFileSystem
) : BasicCommand {
    override val name: String = "cd"

    override fun execute(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            fileSystem.setCurrentDirectory("/")
            return CommandResult.Success("Switched to root directory")
        }

        val path = args[0]
        val node = fileSystem.resolvePath(path)

        if (node == null) {
            return CommandResult.Error("Directory does not exist: $path")
        }

        if (node !is DirectoryNode) {
            return CommandResult.Error("Not a directory: $path")
        }

        fileSystem.setCurrentDirectory(path)
        return CommandResult.Success("Switched to: ${node.path}")
    }

    override fun getHelp(): String = "cd <directory path> - change directory"
}

@Provides(BasicCommand::class)
@IntoList
class LsCommand(
    private val fileSystem: MemoryFileSystem
) : BasicCommand {
    override val name: String = "ls"

    override fun execute(args: List<String>): CommandResult {
        val path = args.firstOrNull() ?: "."
        val node = fileSystem.resolvePath(path)

        if (node == null) {
            return CommandResult.Error("Path does not exist: $path")
        }

        val contents = when (node) {
            is DirectoryNode -> node.listChildren()
            is FileNode -> listOf(node)
            else -> emptyList()
        }

        val result = contents.joinToString("\n") { child ->
            val type = if (child is DirectoryNode) "d" else "-"
            val size = child.size
            val name = child.name
            "$type $size $name"
        }

        return CommandResult.Info(result)
    }

    override fun getHelp(): String = "ls [path] - list directory contents"
}

@Provides(BasicCommand::class)
@IntoList
class RmCommand(
    private val fileSystem: MemoryFileSystem
) : BasicCommand {
    override val name: String = "rm"

    override fun execute(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("File or directory path required")
        }

        val path = args[0]
        val success = fileSystem.delete(path)

        return if (success) {
            CommandResult.Success("Deleted: $path")
        } else {
            CommandResult.Error("Failed to delete: $path")
        }
    }

    override fun getHelp(): String = "rm <path> - delete file or directory"
}

@Provides(BasicCommand::class)
@IntoList
class MkdirCommand(
    private val fileSystem: MemoryFileSystem
) : BasicCommand {
    override val name: String = "mkdir"

    override fun execute(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("Directory name required")
        }

        val path = args[0]
        val node = fileSystem.resolvePath(path)

        if (node != null) {
            return CommandResult.Error("Path already exists: $path")
        }

        try {
            fileSystem.createDirectory(path)
            return CommandResult.Success("Created directory: $path")
        } catch (e: Exception) {
            return CommandResult.Error("Failed to create directory: ${e.message}")
        }
    }

    override fun getHelp(): String = "mkdir <directory name> - create directory"
}

@Provides(BasicCommand::class)
@IntoList
class PwdCommand(
    private val fileSystem: MemoryFileSystem
) : BasicCommand {
    override val name: String = "pwd"

    override fun execute(args: List<String>): CommandResult {
        val currentPath = fileSystem.getCurrentPath()
        return CommandResult.Info(currentPath)
    }

    override fun getHelp(): String = "pwd - show current directory path"
}

@Provides(BasicCommand::class)
@IntoList
class TouchCommand(
    private val fileSystem: MemoryFileSystem
) : BasicCommand {
    override val name: String = "touch"

    override fun execute(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("File name required")
        }

        val path = args[0]
        val node = fileSystem.resolvePath(path)

        if (node != null) {
            if (node is FileNode) {
                node.content = node.content // Keep content, just update timestamp conceptually
                return CommandResult.Success("Updated timestamp: $path")
            } else {
                return CommandResult.Error("Path is a directory: $path")
            }
        }

        try {
            fileSystem.createFile(path, ByteArray(0))
            return CommandResult.Success("Created file: $path")
        } catch (e: Exception) {
            return CommandResult.Error("Failed to create file: ${e.message}")
        }
    }

    override fun getHelp(): String = "touch <file name> - create empty file or update timestamp"
}

// CAT command
@Provides(BasicCommand::class)
@IntoList
class CatCommand(
    private val fileSystem: MemoryFileSystem
) : BasicCommand {
    override val name: String = "cat"

    override fun execute(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("File path required")
        }

        val path = args[0]
        val node = fileSystem.resolvePath(path)

        if (node == null) {
            return CommandResult.Error("File does not exist: $path")
        }

        if (node !is FileNode) {
            return CommandResult.Error("Not a file: $path")
        }

        val content = String(node.content)
        return CommandResult.Info(content)
    }

    override fun getHelp(): String = "cat <file path> - display file contents"
}

@Provides(BasicCommand::class)
@IntoList
class EchoCommand(
    private val fileSystem: MemoryFileSystem
) : BasicCommand {
    override val name: String = "echo"

    override fun execute(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Info("")
        }

        val message = args.joinToString(" ")
        return CommandResult.Info(message)
    }

    override fun getHelp(): String = "echo <message> - display message"
}