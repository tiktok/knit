package knit.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class CLITest {
    private lateinit var fileSystem: MemoryFileSystem
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var mockOutput: ByteArrayOutputStream

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
        mockOutput = ByteArrayOutputStream()
    }

    @Test
    fun `test mkdir and cd bug reproduction`() {
        // This test reproduces the bug mentioned in the issue
        // where mkdir creates a directory but cd fails to find it
        
        // Create directory
        val mkdirResult = commandRegistry.executeCommand("mkdir", listOf("a"))
        assertTrue(mkdirResult is CommandResult.Success)
        
        // Verify directory exists
        assertTrue(fileSystem.exists("/a"))
        
        // Try to change to directory - this should work
        val cdResult = commandRegistry.executeCommand("cd", listOf("a"))
        assertTrue(cdResult is CommandResult.Success, "cd should succeed after mkdir")
        
        // Verify current directory changed
        assertEquals("/a", fileSystem.getCurrentPath())
    }

    @Test
    fun `test relative path navigation`() {
        // Test the specific scenario from the issue
        commandRegistry.executeCommand("mkdir", listOf("a"))
        
        // This should work - relative path
        val cdResult = commandRegistry.executeCommand("cd", listOf("a"))
        assertTrue(cdResult is CommandResult.Success)
        
        // Verify we're in the right directory
        val pwdResult = commandRegistry.executeCommand("pwd", emptyList())
        assertTrue(pwdResult is CommandResult.Info)
        assertEquals("/a", (pwdResult as CommandResult.Info).data)
    }

    @Test
    fun `test absolute path navigation`() {
        // Test absolute path navigation
        commandRegistry.executeCommand("mkdir", listOf("a"))
        
        // Change to root first
        commandRegistry.executeCommand("cd", listOf("/"))
        
        // Then use absolute path
        val cdResult = commandRegistry.executeCommand("cd", listOf("/a"))
        assertTrue(cdResult is CommandResult.Success)
        
        assertEquals("/a", fileSystem.getCurrentPath())
    }

    @Test
    fun `test nested directory creation and navigation`() {
        // Test creating nested directories and navigating
        commandRegistry.executeCommand("mkdir", listOf("a"))
        commandRegistry.executeCommand("mkdir", listOf("a/b"))
        commandRegistry.executeCommand("mkdir", listOf("a/b/c"))
        
        // Navigate through nested directories
        commandRegistry.executeCommand("cd", listOf("a"))
        assertEquals("/a", fileSystem.getCurrentPath())
        
        commandRegistry.executeCommand("cd", listOf("b"))
        assertEquals("/a/b", fileSystem.getCurrentPath())
        
        commandRegistry.executeCommand("cd", listOf("c"))
        assertEquals("/a/b/c", fileSystem.getCurrentPath())
    }

    @Test
    fun `test directory listing after creation`() {
        // Test that directories appear in ls after creation
        commandRegistry.executeCommand("mkdir", listOf("a"))
        
        val lsResult = commandRegistry.executeCommand("ls", emptyList())
        assertTrue(lsResult is CommandResult.Info)
        val output = (lsResult as CommandResult.Info).data.toString()
        assertTrue(output.contains("a"), "Directory 'a' should appear in ls output")
    }

    @Test
    fun `test file creation and directory navigation`() {
        // Test file operations combined with directory navigation
        commandRegistry.executeCommand("mkdir", listOf("workspace"))
        commandRegistry.executeCommand("cd", listOf("workspace"))
        
        commandRegistry.executeCommand("touch", listOf("file.txt"))
        
        // Verify file exists in the workspace
        val lsResult = commandRegistry.executeCommand("ls", emptyList())
        assertTrue(lsResult is CommandResult.Info)
        val output = (lsResult as CommandResult.Info).data.toString()
        assertTrue(output.contains("file.txt"))
    }

    @Test
    fun `test error handling for directory operations`() {
        // Test error cases for directory operations
        
        // Try to cd to nonexistent directory
        val cdNonexistent = commandRegistry.executeCommand("cd", listOf("nonexistent"))
        assertTrue(cdNonexistent is CommandResult.Error)
        
        // Try to mkdir existing directory
        commandRegistry.executeCommand("mkdir", listOf("existing"))
        val mkdirExisting = commandRegistry.executeCommand("mkdir", listOf("existing"))
        assertTrue(mkdirExisting is CommandResult.Error)
        
        // Try to rm nonexistent
        val rmNonexistent = commandRegistry.executeCommand("rm", listOf("nonexistent"))
        assertTrue(rmNonexistent is CommandResult.Error)
    }

    @Test
    fun `test complex workflow`() {
        // Test a complete workflow that might reveal the bug
        
        // Create directory structure
        commandRegistry.executeCommand("mkdir", listOf("project"))
        commandRegistry.executeCommand("cd", listOf("project"))
        commandRegistry.executeCommand("mkdir", listOf("src"))
        commandRegistry.executeCommand("mkdir", listOf("tests"))
        
        // Create files
        commandRegistry.executeCommand("touch", listOf("README.md"))
        commandRegistry.executeCommand("touch", listOf("src/main.kt"))
        commandRegistry.executeCommand("touch", listOf("tests/test.kt"))
        
        // Navigate and verify
        commandRegistry.executeCommand("cd", listOf("src"))
        assertEquals("/project/src", fileSystem.getCurrentPath())
        
        commandRegistry.executeCommand("cd", listOf(".."))
        val actualPath = fileSystem.getCurrentPath()
        assertEquals("/project", actualPath, "Expected /project but got $actualPath")
        
        commandRegistry.executeCommand("cd", listOf("tests"))
        assertEquals("/project/tests", fileSystem.getCurrentPath())
        
        // List contents
        val lsResult = commandRegistry.executeCommand("ls", emptyList())
        assertTrue(lsResult is CommandResult.Info)
        val output = (lsResult as CommandResult.Info).data.toString()
        assertTrue(output.contains("test.kt"))
    }
}