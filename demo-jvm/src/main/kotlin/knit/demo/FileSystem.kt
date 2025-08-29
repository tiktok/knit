package knit.demo

import knit.Provides

// Memory file system implementation
@Provides
class MemoryFileSystem {
    private val root: DirectoryNode = DirectoryNode("", "/")
    private var currentDir: DirectoryNode = root

    fun getRoot(): DirectoryNode = root
    fun getCurrentDirectory(): DirectoryNode = currentDir

    fun setCurrentDirectory(path: String) {
        val node = resolvePath(path)
        if (node is DirectoryNode) {
            currentDir = node
        }
    }

    fun getCurrentPath(): String {
        if (currentDir == root) return "/"
        return "/${currentDir.path}".replace("//", "/")
    }

    fun resolvePath(path: String): FileSystemNode? {
        if (path == "/") return root
        if (path == ".") return currentDir
        if (path == "..") {
            if (currentDir == root) return root
            return findParentDirectory(currentDir)
        }

        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        var current: DirectoryNode = if (path.startsWith("/")) root else currentDir

        for (part in parts) {
            when (part) {
                "." -> continue
                ".." -> {
                    if (current != root) {
                        current = findParentDirectory(current) ?: return null
                    }
                }

                else -> {
                    when (val child = current.getChild(part)) {
                        is DirectoryNode -> current = child
                        is FileNode -> return child
                        null -> return null
                    }
                }
            }
        }
        return current
    }

    private fun findParentDirectory(dir: DirectoryNode): DirectoryNode? {
        if (dir == root) return null

        val targetPath = dir.path
        if (targetPath.isEmpty()) return root

        val parentPath = if (targetPath.contains("/")) {
            targetPath.substringBeforeLast("/")
        } else {
            ""
        }

        // Navigate from root to find the parent directory
        return if (parentPath.isEmpty()) {
            root
        } else {
            navigateFromRoot(parentPath)
        }
    }

    private fun navigateFromRoot(path: String): DirectoryNode? {
        if (path.isEmpty()) return root

        val parts = path.split("/").filter { it.isNotEmpty() }
        var current: DirectoryNode = root

        for (part in parts) {
            val child = current.getChild(part) as? DirectoryNode ?: return null
            current = child
        }

        return current
    }

    fun createFile(path: String, content: ByteArray): FileNode {
        val normalizedPath = if (path.startsWith("/")) path.removePrefix("/") else path
        val parentPath = if (normalizedPath.contains("/")) {
            normalizedPath.substringBeforeLast("/")
        } else {
            ""
        }
        val fileName = normalizedPath.substringAfterLast("/")

        val parent = if (path.startsWith("/")) {
            // Absolute path
            if (parentPath.isEmpty()) {
                root
            } else {
                resolvePath(parentPath) as? DirectoryNode
                    ?: throw IllegalArgumentException("Parent directory not found: $parentPath")
            }
        } else {
            // Relative path - use current directory
            if (parentPath.isEmpty()) {
                currentDir
            } else {
                resolvePath(parentPath) as? DirectoryNode
                    ?: throw IllegalArgumentException("Parent directory not found: $parentPath")
            }
        }

        val fullPath =
            if (parent == root) fileName else if (parent.path.isEmpty()) fileName else "${parent.path}/$fileName"
        val fileNode = FileNode(fileName, fullPath, content)
        parent.addChild(fileNode)
        return fileNode
    }

    fun createDirectory(path: String): DirectoryNode {
        val normalizedPath = if (path.startsWith("/")) path.removePrefix("/") else path
        val parentPath = if (normalizedPath.contains("/")) {
            normalizedPath.substringBeforeLast("/")
        } else {
            ""
        }
        val dirName = normalizedPath.substringAfterLast("/")

        val parent = if (path.startsWith("/")) {
            // Absolute path
            if (parentPath.isEmpty()) {
                root
            } else {
                resolvePath(parentPath) as? DirectoryNode
                    ?: throw IllegalArgumentException("Parent directory not found: $parentPath")
            }
        } else {
            // Relative path - use current directory
            if (parentPath.isEmpty()) {
                currentDir
            } else {
                resolvePath(parentPath) as? DirectoryNode
                    ?: throw IllegalArgumentException("Parent directory not found: $parentPath")
            }
        }

        val fullPath =
            if (parent == root) dirName else if (parent.path.isEmpty()) dirName else "${parent.path}/$dirName"
        val dirNode = DirectoryNode(dirName, fullPath)
        parent.addChild(dirNode)
        return dirNode
    }

    fun delete(path: String): Boolean {
        val parentPath = path.substringBeforeLast("/", "/")
        val name = path.substringAfterLast("/")
        val parent = resolvePath(parentPath) as? DirectoryNode ?: return false

        if (!parent.children.containsKey(name)) {
            return false
        }

        parent.removeChild(name)
        return true
    }

    fun exists(path: String): Boolean = resolvePath(path) != null

    fun readFile(path: String): ByteArray? {
        val file = resolvePath(path) as? FileNode ?: return null
        return file.content
    }

    fun writeFile(path: String, content: ByteArray): Boolean {
        val file = resolvePath(path) as? FileNode ?: return false
        file.content = content
        return true
    }

    fun listDirectory(path: String): List<FileSystemNode> {
        val dir = resolvePath(path) as? DirectoryNode ?: return emptyList()
        return dir.listChildren()
    }
}
