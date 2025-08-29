package knit.demo

import java.security.MessageDigest
import java.time.Instant

// Core data model layer
sealed class GitObject(val type: String, val content: ByteArray) {
    val sha: String by lazy { calculateSha() }

    private fun calculateSha(): String {
        val header = "$type ${content.size}\u0000".toByteArray()
        val store = header + content
        return MessageDigest.getInstance("SHA-1").digest(store).joinToString("") { "%02x".format(it) }
    }
}

class Blob(content: ByteArray) : GitObject("blob", content) {
    fun asString(): String = String(content)
}

data class TreeEntry(val name: String, val sha: String)

class Tree(entries: List<TreeEntry>) : GitObject("tree", formatTree(entries)) {

    companion object {
        private fun formatTree(entries: List<TreeEntry>): ByteArray {
            val sorted = entries.sortedBy { it.name }
            val content = sorted.joinToString("") { entry ->
                val shaBytes = try {
                    entry.sha.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                } catch (e: NumberFormatException) {
                    // Handle invalid hex strings gracefully
                    ByteArray(20) { 0 }
                }
                "${entry.name}\u0000" + String(shaBytes, Charsets.ISO_8859_1)
            }
            return content.toByteArray(Charsets.ISO_8859_1)
        }
    }

    fun getEntries(): List<TreeEntry> = parseTree(content)

    private fun parseTree(content: ByteArray): List<TreeEntry> {
        val entries = mutableListOf<TreeEntry>()
        var offset = 0
        while (offset < content.size) {
            // Find null byte separator
            var nullByte = offset
            while (nullByte < content.size && content[nullByte] != 0.toByte()) nullByte++
            if (nullByte >= content.size) break

            val name = String(content, offset, nullByte - offset)
            
            // Check if we have enough bytes for SHA (20 bytes = 40 hex chars)
            if (nullByte + 20 > content.size) break
            
            val shaBytes = content.copyOfRange(nullByte + 1, nullByte + 21)
            val sha = shaBytes.joinToString("") { "%02x".format(it) }
            entries.add(TreeEntry(name, sha))
            offset = nullByte + 21
        }
        return entries
    }
}

class Commit(
    tree: String,
    parents: List<String>,
    author: String,
    committer: String,
    message: String,
    timestamp: Instant = Instant.now()
) : GitObject("commit", formatCommit(tree, parents, author, committer, message, timestamp)) {

    companion object {
        private fun formatCommit(
            tree: String,
            parents: List<String>,
            author: String,
            committer: String,
            message: String,
            timestamp: Instant
        ): ByteArray {
            val lines = mutableListOf<String>()
            lines.add("tree $tree")
            parents.forEach { lines.add("parent $it") }
            lines.add("author $author ${timestamp.epochSecond} +0000")
            lines.add("committer $committer ${timestamp.epochSecond} +0000")
            lines.add("")
            lines.add(message)
            return lines.joinToString("\n").toByteArray()
        }
    }

    fun getTreeSha(): String = parseCommit().first
    fun getParentShas(): List<String> = parseCommit().second
    fun getMessage(): String = parseCommit().third

    private fun parseCommit(): Triple<String, List<String>, String> {
        val lines = String(content).lines()
        var tree = ""
        val parents = mutableListOf<String>()
        var messageStart = 0

        for ((index, line) in lines.withIndex()) {
            when {
                line.startsWith("tree ") -> tree = line.substring(5)
                line.startsWith("parent ") -> parents.add(line.substring(7))
                line.isEmpty() -> {
                    messageStart = index + 1
                    break
                }
            }
        }

        val message = lines.subList(messageStart, lines.size).joinToString("\n").trim()
        return Triple(tree, parents, message)
    }
}

// Reference system
class Ref(val name: String, var sha: String) {
    fun isBranch(): Boolean = name.startsWith("refs/heads/")
    fun isTag(): Boolean = name.startsWith("refs/tags/")
    fun getShortName(): String = name.substringAfterLast("/")
}

interface FileSystemNode {
    val name: String
    val path: String
    val size: Long
    val lastModified: Instant
}

class FileNode(
    override val name: String,
    override val path: String,
    var content: ByteArray,
    override val lastModified: Instant = Instant.now()
) : FileSystemNode {
    override val size: Long = content.size.toLong()
}

class DirectoryNode(
    override val name: String,
    override val path: String,
    val children: MutableMap<String, FileSystemNode> = mutableMapOf(),
    override val lastModified: Instant = Instant.now()
) : FileSystemNode {
    override val size: Long = children.values.sumOf { it.size }

    fun addChild(node: FileSystemNode) {
        children[node.name] = node
    }

    fun removeChild(name: String) {
        children.remove(name)
    }

    fun getChild(name: String): FileSystemNode? = children[name]
    fun listChildren(): List<FileSystemNode> = children.values.toList()
}