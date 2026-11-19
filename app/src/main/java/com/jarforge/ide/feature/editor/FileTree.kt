package com.jarforge.ide.feature.editor

import com.jarforge.ide.data.model.FileEntry

/** Key of the project root in [FileTreeState] maps. */
const val ROOT_PATH = ""

/**
 * Lazily loaded file tree. Folders are read one level at a time;
 * [children] caches the result per folder path.
 */
data class FileTreeState(
    val children: Map<String, List<FileEntry>> = emptyMap(),
    val expanded: Set<String> = emptySet(),
    val loading: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap()
)

/** One visible line of the explorer: a file or folder, or a status message under a folder. */
sealed interface TreeRow {
    val key: String
    val depth: Int

    data class Node(
        val entry: FileEntry,
        override val depth: Int,
        val expanded: Boolean,
        val loading: Boolean
    ) : TreeRow {
        override val key: String get() = "n:${entry.relativePath}"
    }

    data class Message(
        val parentPath: String,
        override val depth: Int,
        val text: String,
        val isError: Boolean
    ) : TreeRow {
        override val key: String get() = "m:$parentPath"
    }
}

/** Flattens the tree into the rows that are currently visible. */
fun buildRows(tree: FileTreeState): List<TreeRow> {
    val rows = mutableListOf<TreeRow>()
    appendChildren(rows, tree, ROOT_PATH, 0)
    return rows
}

private fun appendChildren(
    rows: MutableList<TreeRow>,
    tree: FileTreeState,
    parentPath: String,
    depth: Int
) {
    val error = tree.errors[parentPath]
    if (error != null) {
        rows.add(TreeRow.Message(parentPath, depth, error, isError = true))
        return
    }

    val children = tree.children[parentPath]
    if (children == null) {
        if (parentPath in tree.loading) {
            rows.add(TreeRow.Message(parentPath, depth, "Loading...", isError = false))
        }
        return
    }

    if (children.isEmpty()) {
        rows.add(TreeRow.Message(parentPath, depth, "Empty", isError = false))
        return
    }

    for (entry in children) {
        val isExpanded = entry.isDirectory && entry.relativePath in tree.expanded
        rows.add(
            TreeRow.Node(
                entry = entry,
                depth = depth,
                expanded = isExpanded,
                loading = entry.relativePath in tree.loading
            )
        )
        if (isExpanded) {
            appendChildren(rows, tree, entry.relativePath, depth + 1)
        }
    }
}
