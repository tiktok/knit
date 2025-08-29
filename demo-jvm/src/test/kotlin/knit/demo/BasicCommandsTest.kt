package knit.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BasicCommandsTest {
    private lateinit var fileSystem: MemoryFileSystem
    private lateinit var cdCommand: CdCommand
    private lateinit var lsCommand: LsCommand
    private lateinit var mkdirCommand: MkdirCommand
    private lateinit var rmCommand: RmCommand
    private lateinit var pwdCommand: PwdCommand
    private lateinit var touchCommand: TouchCommand
    private lateinit var catCommand: CatCommand
    private lateinit var echoCommand: EchoCommand

    @BeforeEach
    fun setup() {
        fileSystem = MemoryFileSystem()
        cdCommand = CdCommand(fileSystem)
        lsCommand = LsCommand(fileSystem)
        mkdirCommand = MkdirCommand(fileSystem)
        rmCommand = RmCommand(fileSystem)
        pwdCommand = PwdCommand(fileSystem)
        touchCommand = TouchCommand(fileSystem)
        catCommand = CatCommand(fileSystem)
        echoCommand = EchoCommand(fileSystem)
    }

    @Test
    fun `test cd command to existing directory`() {
        fileSystem.createDirectory("test")
        val result = cdCommand.execute(listOf("test"))
        assertTrue(result is CommandResult.Success)
        assertEquals("/test", fileSystem.getCurrentPath())
    }

    @Test
    fun `test cd command to nonexistent directory`() {
        val result = cdCommand.execute(listOf("/nonexistent"))
        assertTrue(result is CommandResult.Error)
        assertEquals("Directory does not exist: /nonexistent", (result as CommandResult.Error).message)
    }

    @Test
    fun `test cd command to file`() {
        fileSystem.createFile("test.txt", "content".toByteArray())
        val result = cdCommand.execute(listOf("test.txt"))
        assertTrue(result is CommandResult.Error)
        assertEquals("Not a directory: test.txt", (result as CommandResult.Error).message)
    }

    @Test
    fun `test cd command without arguments`() {
        fileSystem.createDirectory("test")
        fileSystem.setCurrentDirectory("test")
        val result = cdCommand.execute(emptyList())
        assertTrue(result is CommandResult.Success)
        assertEquals("/", fileSystem.getCurrentPath())
    }

    @Test
    fun `test ls command on empty directory`() {
        val result = lsCommand.execute(emptyList())
        assertTrue(result is CommandResult.Info)
        assertEquals("", (result as CommandResult.Info).data)
    }

    @Test
    fun `test ls command with files`() {
        fileSystem.createFile("/file1.txt", "content1".toByteArray())
        fileSystem.createFile("/file2.txt", "content2".toByteArray())
        val result = lsCommand.execute(emptyList())
        assertTrue(result is CommandResult.Info)
        val output = (result as CommandResult.Info).data.toString()
        assertTrue(output.contains("file1.txt"))
        assertTrue(output.contains("file2.txt"))
    }

    @Test
    fun `test mkdir command`() {
        val result = mkdirCommand.execute(listOf("/newdir"))
        assertTrue(result is CommandResult.Success)
        assertTrue(fileSystem.exists("/newdir"))
    }

    @Test
    fun `test mkdir command with existing directory`() {
        fileSystem.createDirectory("existing")
        val result = mkdirCommand.execute(listOf("existing"))
        assertTrue(result is CommandResult.Error)
        assertEquals("Path already exists: existing", (result as CommandResult.Error).message)
    }

    @Test
    fun `test rm command`() {
        fileSystem.createFile("todelete.txt", "content".toByteArray())
        val result = rmCommand.execute(listOf("todelete.txt"))
        assertTrue(result is CommandResult.Success)
        assertFalse(fileSystem.exists("todelete.txt"))
    }

    @Test
    fun `test rm command without arguments`() {
        val result = rmCommand.execute(emptyList())
        assertTrue(result is CommandResult.Error)
        assertEquals("File or directory path required", (result as CommandResult.Error).message)
    }

    @Test
    fun `test pwd command`() {
        val result = pwdCommand.execute(emptyList())
        assertTrue(result is CommandResult.Info)
        assertEquals("/", (result as CommandResult.Info).data)
    }

    @Test
    fun `test touch command create new file`() {
        val result = touchCommand.execute(listOf("/newfile.txt"))
        assertTrue(result is CommandResult.Success)
        assertTrue(fileSystem.exists("/newfile.txt"))
    }

    @Test
    fun `test touch command update existing file`() {
        fileSystem.createFile("existing.txt", "content".toByteArray())
        val result = touchCommand.execute(listOf("existing.txt"))
        assertTrue(result is CommandResult.Success)
        assertTrue(fileSystem.exists("existing.txt"))
    }

    @Test
    fun `test cat command`() {
        val content = "Hello, World!"
        fileSystem.createFile("test.txt", content.toByteArray())
        val result = catCommand.execute(listOf("test.txt"))
        assertTrue(result is CommandResult.Info)
        assertEquals(content, (result as CommandResult.Info).data)
    }

    @Test
    fun `test cat command on nonexistent file`() {
        val result = catCommand.execute(listOf("/nonexistent.txt"))
        assertTrue(result is CommandResult.Error)
        assertEquals("File does not exist: /nonexistent.txt", (result as CommandResult.Error).message)
    }

    @Test
    fun `test echo command`() {
        val result = echoCommand.execute(listOf("Hello", "World"))
        assertTrue(result is CommandResult.Info)
        assertEquals("Hello World", (result as CommandResult.Info).data)
    }

    @Test
    fun `test echo command without arguments`() {
        val result = echoCommand.execute(emptyList())
        assertTrue(result is CommandResult.Info)
        assertEquals("", (result as CommandResult.Info).data)
    }

}