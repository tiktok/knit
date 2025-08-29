package knit.demo

import knit.Provides
import java.util.concurrent.ConcurrentHashMap

// Memory object storage implementation
@Provides
class MemoryObjectStore {
    private val objects = ConcurrentHashMap<String, GitObject>()
    private val typeIndex = ConcurrentHashMap<String, MutableList<String>>()

    fun store(obj: GitObject): String {
        objects[obj.sha] = obj
        typeIndex.getOrPut(obj.type) { mutableListOf() }.add(obj.sha)
        return obj.sha
    }

    fun retrieve(sha: String): GitObject? = objects[sha]

    fun exists(sha: String): Boolean = objects.containsKey(sha)

    fun getAllObjects(): Set<String> = objects.keys.toSet()

    fun getObjectsByType(type: String): List<GitObject> {
        return typeIndex[type]?.mapNotNull { objects[it] } ?: emptyList()
    }

    fun delete(sha: String): Boolean {
        val obj = objects.remove(sha) ?: return false
        typeIndex[obj.type]?.remove(sha)
        return true
    }

    fun getSize(sha: String): Long? = objects[sha]?.content?.size?.toLong()

    fun getObjectCount(): Int = objects.size
}

// Object indexing service - provides fast lookup
@Provides
class ObjectIndexService(
    private val objectStore: MemoryObjectStore
) {
    private val commitIndex = mutableMapOf<String, MutableList<String>>() // author -> commits
    private val treeIndex = mutableMapOf<String, MutableList<String>>() // directory -> trees
    private val blobIndex = mutableMapOf<String, MutableList<String>>() // filename -> blobs

    fun indexCommit(commit: Commit) {
        val author = commit.getMessage().substringBefore("\n").substringAfter("author ").substringBefore(" ")
        commitIndex.getOrPut(author) { mutableListOf() }.add(commit.sha)

        val treeSha = commit.getTreeSha()
        val tree = objectStore.retrieve(treeSha) as? Tree ?: return
        treeIndex.getOrPut("/") { mutableListOf() }.add(tree.sha)
    }

    fun findCommitsByAuthor(author: String): List<Commit> {
        return commitIndex[author]?.mapNotNull {
            objectStore.retrieve(it) as? Commit
        } ?: emptyList()
    }

    fun findTreesByDirectory(dir: String): List<Tree> {
        return treeIndex[dir]?.mapNotNull {
            objectStore.retrieve(it) as? Tree
        } ?: emptyList()
    }

    fun findBlobsByFilename(filename: String): List<Blob> {
        return blobIndex[filename]?.mapNotNull {
            objectStore.retrieve(it) as? Blob
        } ?: emptyList()
    }
}

// Object validation service
@Provides
class ObjectValidator(
    private val objectStore: MemoryObjectStore,
    private val indexService: ObjectIndexService
) {
    fun validateObject(sha: String): Boolean {
        val obj = objectStore.retrieve(sha) ?: return false

        return when (obj) {
            is Blob -> validateBlob(obj)
            is Tree -> validateTree(obj)
            is Commit -> validateCommit(obj)
            else -> false
        }
    }

    private fun validateBlob(blob: Blob): Boolean {
        return blob.content.isNotEmpty()
    }

    private fun validateTree(tree: Tree): Boolean {
        val entries = tree.getEntries()
        return entries.all { entry ->
            objectStore.exists(entry.sha)
        }
    }

    private fun validateCommit(commit: Commit): Boolean {
        val treeSha = commit.getTreeSha()
        return objectStore.exists(treeSha) &&
            commit.getParentShas().all { objectStore.exists(it) }
    }

    fun validateRepositoryIntegrity(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()

        objectStore.getAllObjects().forEach { sha ->
            results[sha] = validateObject(sha)
        }

        return results
    }
}

// Object relationship analyzer
@Provides
class ObjectGraphAnalyzer(
    private val objectStore: MemoryObjectStore,
    private val validator: ObjectValidator
) {
    data class ObjectGraph(val nodes: Set<String>, val edges: List<Pair<String, String>>)

    fun buildObjectGraph(): ObjectGraph {
        val nodes = mutableSetOf<String>()
        val edges = mutableListOf<Pair<String, String>>()

        objectStore.getAllObjects().forEach { sha ->
            nodes.add(sha)
            val obj = objectStore.retrieve(sha) ?: return@forEach

            when (obj) {
                is Tree -> {
                    obj.getEntries().forEach { entry ->
                        edges.add(sha to entry.sha)
                    }
                }

                is Commit -> {
                    edges.add(sha to obj.getTreeSha())
                    obj.getParentShas().forEach { parent ->
                        edges.add(sha to parent)
                    }
                }

                else -> {}
            }
        }

        return ObjectGraph(nodes, edges)
    }

    fun findDanglingObjects(): List<String> {
        val graph = buildObjectGraph()
        val referenced = graph.edges.map { it.second }.toSet()
        return graph.nodes.filter { it !in referenced }
    }

    fun findOrphanCommits(): List<Commit> {
        val commits = objectStore.getObjectsByType("commit").map { it as Commit }
        return commits.filter { commit ->
            commit.getParentShas().any { !objectStore.exists(it) }
        }
    }

    fun calculateRepositoryStats(): RepositoryStats {
        val objects = objectStore.getAllObjects()
        val totalSize = objects.sumOf { objectStore.getSize(it) ?: 0 }

        return RepositoryStats(
            totalObjects = objects.size,
            totalSize = totalSize,
            blobCount = objectStore.getObjectsByType("blob").size,
            treeCount = objectStore.getObjectsByType("tree").size,
            commitCount = objectStore.getObjectsByType("commit").size,
            danglingObjects = findDanglingObjects().size,
        )
    }
}

data class RepositoryStats(
    val totalObjects: Int,
    val totalSize: Long,
    val blobCount: Int,
    val treeCount: Int,
    val commitCount: Int,
    val danglingObjects: Int
)