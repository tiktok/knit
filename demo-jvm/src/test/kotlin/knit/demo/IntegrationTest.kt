package knit.demo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class IntegrationTest {
    private lateinit var fileSystem: MemoryFileSystem
    private lateinit var commandRegistry: CommandRegistry

    @BeforeEach
    fun setup() {
        fileSystem = MemoryFileSystem()
        val basicCommands = listOf(
            CdCommand(fileSystem),
            LsCommand(fileSystem),
            MkdirCommand(fileSystem),
            RmCommand(fileSystem),
            PwdCommand(fileSystem),
            TouchCommand(fileSystem),
            CatCommand(fileSystem),
            EchoCommand(fileSystem)
        )
        val gitCommands = emptyList<GitCommand>()
        commandRegistry = CommandRegistry(gitCommands, basicCommands)
    }

    @Test
    fun `test complete workflow`() {
        // Create directory structure
        commandRegistry.executeCommand("mkdir", listOf("/workspace"))
        commandRegistry.executeCommand("mkdir", listOf("/workspace/project"))
        commandRegistry.executeCommand("mkdir", listOf("/workspace/project/src"))
        
        // Create files
        commandRegistry.executeCommand("touch", listOf("/workspace/project/README.md"))
        commandRegistry.executeCommand("touch", listOf("/workspace/project/src/main.kt"))
        
        // Verify structure
        val lsResult = commandRegistry.executeCommand("ls", listOf("/workspace/project"))
        assertTrue(lsResult is CommandResult.Info)
        val output = (lsResult as CommandResult.Info).data.toString()
        assertTrue(output.contains("README.md"))
        assertTrue(output.contains("src"))
        
        // Change directory and verify
        commandRegistry.executeCommand("cd", listOf("/workspace/project"))
        assertEquals("/workspace/project", fileSystem.getCurrentPath())
        
        // List current directory
        val lsCurrent = commandRegistry.executeCommand("ls", emptyList())
        assertTrue(lsCurrent is CommandResult.Info)
        val currentOutput = (lsCurrent as CommandResult.Info).data.toString()
        assertTrue(currentOutput.contains("README.md"))
        assertTrue(currentOutput.contains("src"))
    }

    @Test
    fun `test directory navigation`() {
        // Create nested directories
        commandRegistry.executeCommand("mkdir", listOf("/a"))
        commandRegistry.executeCommand("mkdir", listOf("/a/b"))
        commandRegistry.executeCommand("mkdir", listOf("/a/b/c"))
        
        // Navigate through directories
        commandRegistry.executeCommand("cd", listOf("/a"))
        assertEquals("/a", fileSystem.getCurrentPath())
        
        commandRegistry.executeCommand("cd", listOf("b"))
        assertEquals("/a/b", fileSystem.getCurrentPath())
        
        commandRegistry.executeCommand("cd", listOf("c"))
        assertEquals("/a/b/c", fileSystem.getCurrentPath())
        
        // Navigate back up
        commandRegistry.executeCommand("cd", listOf(".."))
        assertEquals("/a/b", fileSystem.getCurrentPath())
        
        commandRegistry.executeCommand("cd", listOf(".."))
        assertEquals("/a", fileSystem.getCurrentPath())
        
        commandRegistry.executeCommand("cd", listOf(".."))
        assertEquals("/", fileSystem.getCurrentPath())
    }

    @Test
    fun `test file operations workflow`() {
        // Create directory and file
        commandRegistry.executeCommand("mkdir", listOf("/docs"))
        commandRegistry.executeCommand("touch", listOf("/docs/note.txt"))
        
        // Write to file
        fileSystem.writeFile("/docs/note.txt", "Important notes here".toByteArray())
        
        // Read file
        val catResult = commandRegistry.executeCommand("cat", listOf("/docs/note.txt"))
        assertTrue(catResult is CommandResult.Info)
        assertEquals("Important notes here", (catResult as CommandResult.Info).data)
        
        // Delete file
        commandRegistry.executeCommand("rm", listOf("/docs/note.txt"))
        assertFalse(fileSystem.exists("/docs/note.txt"))
        
        // Try to read deleted file
        val catDeleted = commandRegistry.executeCommand("cat", listOf("/docs/note.txt"))
        assertTrue(catDeleted is CommandResult.Error)
    }

    @Test
    fun `test error handling`() {
        // Try to cd to nonexistent directory
        val cdResult = commandRegistry.executeCommand("cd", listOf("/nonexistent"))
        assertTrue(cdResult is CommandResult.Error)
        
        // Try to ls nonexistent path
        val lsResult = commandRegistry.executeCommand("ls", listOf("/nonexistent"))
        assertTrue(lsResult is CommandResult.Error)
        
        // Try to rm nonexistent file
        val rmResult = commandRegistry.executeCommand("rm", listOf("/nonexistent"))
        assertTrue(rmResult is CommandResult.Error)
        
        // Try to cat nonexistent file
        val catResult = commandRegistry.executeCommand("cat", listOf("/nonexistent"))
        assertTrue(catResult is CommandResult.Error)
    }

    @Test
    fun `test edge cases`() {
        // Create directory with spaces in name
        commandRegistry.executeCommand("mkdir", listOf("/My Documents"))
        assertTrue(fileSystem.exists("/My Documents"))
        
        // Create file with special characters
        commandRegistry.executeCommand("touch", listOf("/file-with-dashes.txt"))
        assertTrue(fileSystem.exists("/file-with-dashes.txt"))
        
        // Test empty directory listing
        commandRegistry.executeCommand("mkdir", listOf("/empty"))
        val lsEmpty = commandRegistry.executeCommand("ls", listOf("/empty"))
        assertTrue(lsEmpty is CommandResult.Info)
        assertEquals("", (lsEmpty as CommandResult.Info).data)
    }

    @Test
    fun `test command help`() {
        val help = commandRegistry.getHelp()
        assertTrue(help.contains("cd"))
        assertTrue(help.contains("ls"))
        assertTrue(help.contains("mkdir"))
        assertTrue(help.contains("rm"))
        assertTrue(help.contains("pwd"))
        assertTrue(help.contains("touch"))
        assertTrue(help.contains("cat"))
        assertTrue(help.contains("echo"))
    }
}