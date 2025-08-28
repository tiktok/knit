package knit.demo

import knit.IntoList
import knit.Provides
import knit.Singleton
import java.time.Instant

// Command interface
interface GitCommand {
    val name: String
    fun execute(args: List<String>): CommandResult
    fun getHelp(): String
}

sealed class CommandResult {
    data class Success(val message: String) : CommandResult()
    data class Error(val message: String) : CommandResult()
    data class Info(val data: Any) : CommandResult()
}

// Staging area management
@Provides
@Singleton
class StagingArea {
    private val stagedFiles = mutableMapOf<String, String>() // path -> blob sha

    fun add(path: String, blobSha: String) {
        stagedFiles[path] = blobSha
    }

    fun remove(path: String): Boolean = stagedFiles.remove(path) != null
    fun getStagedFiles(): Map<String, String> = stagedFiles.toMap()
    fun isEmpty(): Boolean = stagedFiles.isEmpty()
    fun clear() = stagedFiles.clear()
    fun getStagedFileCount(): Int = stagedFiles.size
}

// Git Add command
@Provides(GitCommand::class)
@IntoList
class AddCommand(
    private val fileSystem: MemoryFileSystem,
    private val objectStore: MemoryObjectStore,
    private val index: StagingArea
) : GitCommand {
    override val name: String = "add"

    override fun execute(args: List<String>): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.Error("Need to specify file path")
        }

        val addedFiles = mutableListOf<String>()

        for (path in args) {
            val node = fileSystem.resolvePath(path)
            when (node) {
                is FileNode -> {
                    val blob = Blob(node.content)
                    objectStore.store(blob)
                    index.add(path, blob.sha)
                    addedFiles.add(path)
                }

                is DirectoryNode -> {
                    addDirectory(node, path)
                }

                else -> {
                    return CommandResult.Error("File not found: $path")
                }
            }
        }

        return CommandResult.Success("Added ${addedFiles.size} files to staging area")
    }

    private fun addDirectory(dir: DirectoryNode, basePath: String) {
        dir.listChildren().forEach { child ->
            when (child) {
                is FileNode -> {
                    val relativePath = if (basePath.endsWith("/")) basePath + child.name else "$basePath/${child.name}"
                    val blob = Blob(child.content)
                    objectStore.store(blob)
                    index.add(relativePath, blob.sha)
                }

                is DirectoryNode -> {
                    val relativePath = if (basePath.endsWith("/")) basePath + child.name else "$basePath/${child.name}"
                    addDirectory(child, relativePath)
                }
            }
        }
    }

    override fun getHelp(): String = "git add <path> - add file to staging area"
}

// Git Commit command
@Provides(GitCommand::class)
@IntoList
class CommitCommand(
    private val objectStore: MemoryObjectStore,
    private val index: StagingArea,
    private val refManager: MemoryReferenceManager,
    private val historyTracker: ReferenceHistoryTracker
) : GitCommand {
    override val name: String = "commit"

    override fun execute(args: List<String>): CommandResult {
        val message = args.firstOrNull { it.startsWith("-m") }?.substringAfter("-m")
            ?: args.firstOrNull { !it.startsWith("-") }
            ?: "Commit at ${Instant.now()}"

        if (index.isEmpty()) {
            return CommandResult.Error("No changes to commit")
        }

        val stagedFiles = index.getStagedFiles()
        val tree = buildTreeFromIndex(stagedFiles)
        objectStore.store(tree)

        val currentCommitSha = refManager.getCurrentCommitSha()
        val parents = currentCommitSha?.let { listOf(it) } ?: emptyList()

        val commit = Commit(
                tree = tree.sha,
                parents = parents,
                author = "User <user@example.com>",
                committer = "User <user@example.com>",
                message = message,
        )

        objectStore.store(commit)
        refManager.updateHead(commit.sha)

        historyTracker.recordChange("HEAD", currentCommitSha ?: "", commit.sha, "commit")
        index.clear()

        return CommandResult.Success("Commit successful: ${commit.sha.substring(0, 7)}")
    }

    private fun buildTreeFromIndex(stagedFiles: Map<String, String>): Tree {
        val entries = stagedFiles.map { (path, sha) ->
            TreeEntry(path, sha)
        }
        return Tree(entries)
    }

    override fun getHelp(): String = "git commit [-m <message>] - commit staged changes"
}

// Git Log command
@Provides(GitCommand::class)
@IntoList
class LogCommand(
    private val objectStore: MemoryObjectStore,
    private val refManager: MemoryReferenceManager
) : GitCommand {
    override val name: String = "log"

    override fun execute(args: List<String>): CommandResult {
        val commitSha = refManager.getCurrentCommitSha() ?: return CommandResult.Error("No commits found")

        val logEntries = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val queue = mutableListOf(commitSha)

        while (queue.isNotEmpty()) {
            val sha = queue.removeFirst()
            if (sha in visited) continue
            visited.add(sha)

            val commit = objectStore.retrieve(sha) as? Commit ?: continue
            logEntries.add(formatCommitLog(sha, commit))

            queue.addAll(commit.getParentShas())
        }

        return CommandResult.Info(logEntries.joinToString("\n\n"))
    }

    private fun formatCommitLog(sha: String, commit: Commit): String {
        val shortSha = sha.substring(0, 7)
        val message = commit.getMessage()
        val parents = commit.getParentShas().joinToString(" ") { it.substring(0, 7) }

        return """
            commit $shortSha
            Author: User <user@example.com>
            Date: ${Instant.now()}
            
                $message
            
            Parents: $parents
        """.trimIndent()
    }

    override fun getHelp(): String = "git log - show commit history"
}