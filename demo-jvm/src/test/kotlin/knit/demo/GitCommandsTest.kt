package knit.demo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import java.time.Instant

class GitCommandsTest {
    private lateinit var fileSystem: MemoryFileSystem
    private lateinit var objectStore: MemoryObjectStore
    private lateinit var refManager: MemoryReferenceManager
    private lateinit var index: StagingArea
    private lateinit var historyTracker: ReferenceHistoryTracker
    private lateinit var eventBus: EventBus
    
    private lateinit var addCommand: AddCommand
    private lateinit var commitCommand: CommitCommand
    private lateinit var logCommand: LogCommand
    private lateinit var statusCommand: StatusCommand

    @BeforeEach
    fun setup() {
        fileSystem = MemoryFileSystem()
        objectStore = MemoryObjectStore()
        refManager = MemoryReferenceManager()
        index = StagingArea()
        eventBus = EventBus()
        historyTracker = ReferenceHistoryTracker(refManager, eventBus)
        
        addCommand = AddCommand(fileSystem, objectStore, index)
        commitCommand = CommitCommand(objectStore, index, refManager, historyTracker)
        logCommand = LogCommand(objectStore, refManager)
        statusCommand = StatusCommand(fileSystem, objectStore, index, refManager)
    }

    @Nested
    inner class AddCommandTests {
        
        @Test
        fun `test add single file to staging area`() {
            fileSystem.createFile("test.txt", "Hello World".toByteArray())
            
            val result = addCommand.execute(listOf("test.txt"))
            
            assertTrue(result is CommandResult.Success)
            assertEquals(1, index.getStagedFileCount())
            assertTrue(index.getStagedFiles().containsKey("test.txt"))
        }

        @Test
        fun `test add multiple files to staging area`() {
            fileSystem.createFile("file1.txt", "Content 1".toByteArray())
            fileSystem.createFile("file2.txt", "Content 2".toByteArray())
            
            val result = addCommand.execute(listOf("file1.txt", "file2.txt"))
            
            assertTrue(result is CommandResult.Success)
            assertEquals(2, index.getStagedFileCount())
            assertTrue(index.getStagedFiles().containsKey("file1.txt"))
            assertTrue(index.getStagedFiles().containsKey("file2.txt"))
        }

        @Test
        fun `test add nonexistent file`() {
            val result = addCommand.execute(listOf("nonexistent.txt"))
            
            assertTrue(result is CommandResult.Error)
            assertEquals("File not found: nonexistent.txt", (result as CommandResult.Error).message)
        }

        @Test
        fun `test add directory recursively`() {
            fileSystem.createDirectory("project")
            fileSystem.createFile("project/file1.txt", "Content 1".toByteArray())
            fileSystem.createFile("project/file2.txt", "Content 2".toByteArray())
            
            val result = addCommand.execute(listOf("project"))
            
            assertTrue(result is CommandResult.Success)
            assertEquals(2, index.getStagedFileCount())
            assertTrue(index.getStagedFiles().containsKey("project/file1.txt"))
            assertTrue(index.getStagedFiles().containsKey("project/file2.txt"))
        }

        @Test
        fun `test add empty directory`() {
            fileSystem.createDirectory("emptydir")
            
            val result = addCommand.execute(listOf("emptydir"))
            
            assertTrue(result is CommandResult.Success)
            assertEquals(0, index.getStagedFileCount())
        }

        @Test
        fun `test add without arguments`() {
            val result = addCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Error)
            assertEquals("Need to specify file path", (result as CommandResult.Error).message)
        }
    }

    @Nested
    inner class CommitCommandTests {
        
        @Test
        fun `test commit with staged files`() {
            fileSystem.createFile("test.txt", "Hello World".toByteArray())
            addCommand.execute(listOf("test.txt"))
            
            val result = commitCommand.execute(listOf("-m", "Initial commit"))
            
            assertTrue(result is CommandResult.Success)
            assertTrue(index.isEmpty())
            assertNotNull(refManager.getCurrentCommitSha())
            assertTrue(objectStore.getObjectCount() >= 2) // At least 1 commit + 1 tree + 1 blob
        }

        @Test
        fun `test commit without staged files`() {
            val result = commitCommand.execute(listOf("-m", "Empty commit"))
            
            assertTrue(result is CommandResult.Error)
            assertEquals("No changes to commit", (result as CommandResult.Error).message)
        }

        @Test
        fun `test commit with custom message`() {
            fileSystem.createFile("test.txt", "Hello World".toByteArray())
            addCommand.execute(listOf("test.txt"))
            
            val result = commitCommand.execute(listOf("-m", "Custom message"))
            
            assertTrue(result is CommandResult.Success)
            val commitSha = refManager.getCurrentCommitSha()
            assertNotNull(commitSha)
            
            val commit = objectStore.retrieve(commitSha!!) as Commit
            assertNotNull(commit)
        }

        @Test
        fun `test commit with default message`() {
            fileSystem.createFile("test.txt", "Hello World".toByteArray())
            addCommand.execute(listOf("test.txt"))
            
            val result = commitCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Success)
            val commitSha = refManager.getCurrentCommitSha()
            assertNotNull(commitSha)
            
            val commit = objectStore.retrieve(commitSha!!) as Commit
            assertTrue(commit.getMessage().startsWith("Commit at"))
        }

        @Test
        fun `test multiple commits`() {
            fileSystem.createFile("file1.txt", "Content 1".toByteArray())
            addCommand.execute(listOf("file1.txt"))
            commitCommand.execute(listOf("-m", "First commit"))
            
            fileSystem.createFile("file2.txt", "Content 2".toByteArray())
            addCommand.execute(listOf("file2.txt"))
            commitCommand.execute(listOf("-m", "Second commit"))
            
            assertTrue(objectStore.getObjectCount() >= 3) // At least 2 commits + 1 tree + 1 blob
            assertNotNull(refManager.getCurrentCommitSha())
        }

        @Test
        fun `test commit creates parent relationship`() {
            fileSystem.createFile("file1.txt", "Content 1".toByteArray())
            addCommand.execute(listOf("file1.txt"))
            commitCommand.execute(listOf("-m", "First commit"))
            
            val firstCommitSha = refManager.getCurrentCommitSha()
            
            fileSystem.createFile("file2.txt", "Content 2".toByteArray())
            addCommand.execute(listOf("file2.txt"))
            commitCommand.execute(listOf("-m", "Second commit"))
            
            val secondCommitSha = refManager.getCurrentCommitSha()
            val secondCommit = objectStore.retrieve(secondCommitSha!!) as Commit
            
            assertEquals(listOf(firstCommitSha), secondCommit.getParentShas())
        }
    }

    @Nested
    inner class LogCommandTests {
        
        @Test
        fun `test log with no commits`() {
            val result = logCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Error)
            assertEquals("No commits found", (result as CommandResult.Error).message)
        }

        @Test
        fun `test log with single commit`() {
            fileSystem.createFile("test.txt", "Hello World".toByteArray())
            addCommand.execute(listOf("test.txt"))
            commitCommand.execute(listOf("-m", "Initial commit"))
            
            val result = logCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Info)
            val output = (result as CommandResult.Info).data.toString()
            assertTrue(output.isNotEmpty())
        }

        @Test
        fun `test log with multiple commits`() {
            fileSystem.createFile("file1.txt", "Content 1".toByteArray())
            addCommand.execute(listOf("file1.txt"))
            commitCommand.execute(listOf("-m", "First commit"))
            
            fileSystem.createFile("file2.txt", "Content 2".toByteArray())
            addCommand.execute(listOf("file2.txt"))
            commitCommand.execute(listOf("-m", "Second commit"))
            
            val result = logCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Info)
            val output = (result as CommandResult.Info).data.toString()
            assertTrue(output.isNotEmpty())
        }

        @Test
        fun `test log shows commit relationships`() {
            fileSystem.createFile("file1.txt", "Content 1".toByteArray())
            addCommand.execute(listOf("file1.txt"))
            commitCommand.execute(listOf("-m", "First commit"))
            
            fileSystem.createFile("file2.txt", "Content 2".toByteArray())
            addCommand.execute(listOf("file2.txt"))
            commitCommand.execute(listOf("-m", "Second commit"))
            
            val result = logCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Info)
            val output = (result as CommandResult.Info).data.toString()
            assertTrue(output.contains("Parents"))
        }
    }

    @Nested
    inner class StatusCommandTests {
        
        @Test
        fun `test status with no changes`() {
            val result = statusCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Info)
            val output = (result as CommandResult.Info).data.toString()
            assertTrue(output.contains("No changes added to commit"))
        }

        @Test
        fun `test status with staged files`() {
            fileSystem.createFile("test.txt", "Hello World".toByteArray())
            addCommand.execute(listOf("test.txt"))
            
            val result = statusCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Info)
            val output = (result as CommandResult.Info).data.toString()
            assertTrue(output.contains("Changes to be committed"))
            assertTrue(output.contains("new file: test.txt"))
        }

        @Test
        fun `test status with untracked files`() {
            fileSystem.createFile("untracked.txt", "Hello World".toByteArray())
            
            val result = statusCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Info)
            val output = (result as CommandResult.Info).data.toString()
            assertTrue(output.contains("Untracked files"))
            assertTrue(output.contains("untracked.txt"))
        }

        @Test
        fun `test status with both staged and untracked files`() {
            fileSystem.createFile("staged.txt", "Staged content".toByteArray())
            fileSystem.createFile("untracked.txt", "Untracked content".toByteArray())
            
            addCommand.execute(listOf("staged.txt"))
            
            val result = statusCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Info)
            val output = (result as CommandResult.Info).data.toString()
            assertTrue(output.contains("Changes to be committed"))
            assertTrue(output.contains("new file: staged.txt"))
            assertTrue(output.contains("Untracked files"))
            assertTrue(output.contains("untracked.txt"))
        }

        @Test
        fun `test status shows current branch`() {
            fileSystem.createFile("test.txt", "Hello World".toByteArray())
            addCommand.execute(listOf("test.txt"))
            commitCommand.execute(listOf("-m", "Initial commit"))
            
            val result = statusCommand.execute(emptyList())
            
            assertTrue(result is CommandResult.Info)
            val output = (result as CommandResult.Info).data.toString()
            assertTrue(output.contains("On branch"))
        }
    }

    @Nested
    inner class IntegrationTests {
        
        @Test
        fun `test complete git workflow`() {
            // Create initial files
            fileSystem.createFile("README.md", "# My Project".toByteArray())
            fileSystem.createDirectory("src")
            fileSystem.createFile("src/main.kt", "fun main() = println(\"Hello\")".toByteArray())
            
            // Add files to staging
            addCommand.execute(listOf("README.md", "src/main.kt"))
            
            // First commit
            val commit1 = commitCommand.execute(listOf("-m", "Initial commit"))
            assertTrue(commit1 is CommandResult.Success)
            
            // Create more files
            fileSystem.createDirectory("tests")
            fileSystem.createFile("tests/test.kt", "class Test".toByteArray())
            addCommand.execute(listOf("tests/test.kt"))
            
            // Second commit
            val commit2 = commitCommand.execute(listOf("-m", "Add tests"))
            assertTrue(commit2 is CommandResult.Success)
            
            // Check status
            val status = statusCommand.execute(emptyList())
            assertTrue(status is CommandResult.Info)
            val statusOutput = (status as CommandResult.Info).data.toString()
            assertTrue(statusOutput.contains("No changes added to commit"))
            
            // Check log
            val log = logCommand.execute(emptyList())
            assertTrue(log is CommandResult.Info)
            val logOutput = (log as CommandResult.Info).data.toString()
            assertTrue(logOutput.isNotEmpty())
        }

        @Test
        fun `test object integrity after operations`() {
            fileSystem.createFile("test.txt", "Hello World".toByteArray())
            addCommand.execute(listOf("test.txt"))
            commitCommand.execute(listOf("-m", "Test commit"))
            
            val commitSha = refManager.getCurrentCommitSha()
            assertNotNull(commitSha)
            
            val commit = objectStore.retrieve(commitSha!!) as Commit
            assertTrue(objectStore.exists(commit.getTreeSha()))
        }

        @Test
        fun `test reference management`() {
            fileSystem.createFile("file1.txt", "Content 1".toByteArray())
            addCommand.execute(listOf("file1.txt"))
            commitCommand.execute(listOf("-m", "First commit"))
            
            val firstSha = refManager.getCurrentCommitSha()
            assertNotNull(firstSha)
            
            fileSystem.createFile("file2.txt", "Content 2".toByteArray())
            addCommand.execute(listOf("file2.txt"))
            commitCommand.execute(listOf("-m", "Second commit"))
            
            val secondSha = refManager.getCurrentCommitSha()
            assertNotNull(secondSha)
            assertNotEquals(firstSha, secondSha)
            
            val history = historyTracker.getRefHistory("HEAD")
            assertEquals(2, history.size)
        }
    }
}