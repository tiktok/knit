package knit.demo

import knit.IntoList
import knit.Provides

// Git Status command
@Provides(GitCommand::class)
@IntoList
class StatusCommand(
    private val fileSystem: MemoryFileSystem,
    private val objectStore: MemoryObjectStore,
    private val index: StagingArea,
    private val refManager: MemoryReferenceManager
) : GitCommand {
    override val name: String = "status"

    override fun execute(args: List<String>): CommandResult {
        val currentCommitSha = refManager.getCurrentCommitSha()
        val stagedFiles = index.getStagedFiles()
        val currentDir = fileSystem.getCurrentDirectory()

        val status = StringBuilder()
        status.appendLine("On branch ${getCurrentBranch()}")

        if (stagedFiles.isNotEmpty()) {
            status.appendLine("Changes to be committed:")
            stagedFiles.keys.forEach { path ->
                status.appendLine("  new file: $path")
            }
        } else {
            status.appendLine("No changes added to commit")
        }

        val untrackedFiles = findUntrackedFiles(currentDir)
        if (untrackedFiles.isNotEmpty()) {
            status.appendLine("Untracked files:")
            untrackedFiles.forEach { path ->
                status.appendLine("  $path")
            }
        }

        return CommandResult.Info(status.toString())
    }

    private fun getCurrentBranch(): String {
        return refManager.listBranches()
            .find { it.sha == refManager.getCurrentCommitSha() }
            ?.getShortName() ?: "HEAD"
    }

    private fun findUntrackedFiles(dir: DirectoryNode): List<String> {
        val untracked = mutableListOf<String>()
        dir.listChildren().forEach { child ->
            when (child) {
                is FileNode -> {
                    if (!isTracked(child.path)) {
                        untracked.add(child.path)
                    }
                }

                is DirectoryNode -> {
                    untracked.addAll(findUntrackedFiles(child))
                }
            }
        }
        return untracked
    }

    private fun isTracked(path: String): Boolean {
        return index.getStagedFiles().containsKey(path)
    }

    override fun getHelp(): String = "git status - show working tree status"
}