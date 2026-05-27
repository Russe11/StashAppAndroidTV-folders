package com.github.damontecres.stashapp.folders.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.damontecres.stashapp.folders.data.FolderListRow
import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.ui.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Debug-only preview of the Folders destination's single-level browser.
 *
 * Renders [FolderListPane] against a hard-coded in-memory folder tree so the
 * UX can be exercised on the emulator without standing up a Stash server,
 * populating Room, or going through InitialSetup. Not packaged in release
 * builds — lives under `src/debug/`.
 *
 * Navigation is driven by an `onPreviewKeyEvent` on the root, not by per-row
 * focus modifiers. Compose-on-TV focus + LazyColumn lazy composition turn out
 * to be hard to make work reliably for initial focus; the host-driven model
 * keeps the highlight and the activation perfectly in sync.
 *
 * Launch with:
 *   adb shell am start -n com.github.damontecres.stashapp.folders.debug/com.github.damontecres.stashapp.folders.ui.FoldersPreviewActivity
 */
class FoldersPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                FoldersPreviewScreen()
            }
        }
    }
}

@Composable
private fun FoldersPreviewScreen() {
    val tree = remember { MockFolderTree() }
    val currentPath by tree.currentPath.collectAsState()
    val childList by tree.childrenOf(currentPath).collectAsState(initial = emptyList())
    val childPagingItems = remember(childList) { flowOf(PagingData.from(childList)) }.collectAsLazyPagingItems()
    val showParent = currentPath != ROOT
    val rowCount = (if (showParent) 1 else 0) + childList.size

    // Reset selection to the top whenever the path changes — that's `..`
    // when present, otherwise the first child.
    var focusedRowIndex by rememberSaveable(currentPath) { mutableIntStateOf(0) }

    // Clamp when the row count shrinks (defensive — not currently expected
    // because mock data is static).
    LaunchedEffect(rowCount) {
        if (focusedRowIndex >= rowCount) {
            focusedRowIndex = (rowCount - 1).coerceAtLeast(0)
        }
    }

    BackHandler(enabled = currentPath != ROOT) { tree.goUp() }

    // The root focus requester anchors the activity so it stays foregrounded
    // (without it the TV launcher eclipses an unfocused window in seconds)
    // and dispatches DPad input to the navigation handlers below.
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { rootFocus.requestFocus() }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (focusedRowIndex > 0) focusedRowIndex--
                            true
                        }

                        Key.DirectionDown -> {
                            if (focusedRowIndex < rowCount - 1) focusedRowIndex++
                            true
                        }

                        Key.DirectionLeft -> {
                            tree.goUp()
                            true
                        }

                        Key.DirectionRight, Key.DirectionCenter, Key.Enter -> {
                            when (val target =
                                folderRowTargetAt(showParent, focusedRowIndex, childList)) {
                                FolderRowTarget.GoUp -> tree.goUp()
                                is FolderRowTarget.Enter -> tree.enter(target.node)
                                null -> { /* nothing to do */
                                }
                            }
                            true
                        }

                        else -> false
                    }
                },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar — mimics the real FoldersTopBar position but keeps it
            // simple; the prototype is about the navigation pane below.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "Folders (preview)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(0.30f),
                ) {
                    FolderListPane(
                        currentPath = currentPath,
                        children = childPagingItems,
                        focusedRowIndex = focusedRowIndex,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(0.70f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                ) {
                    val sceneCount = tree.directSceneCount(currentPath)
                    Text(
                        text =
                            "Right pane: scene grid for\n" +
                                "$currentPath\n" +
                                "($sceneCount direct scenes)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val ROOT = "/"
private const val MOCK_SERVER = "mock://preview"

/**
 * Tiny mock backend. Holds a static directory tree plus per-folder scene counts,
 * exposes a `currentPath` StateFlow and reactive `childrenOf` flows so the pane
 * behaves exactly like it does against the real Room DAO.
 */
private class MockFolderTree {
    private val rowsByPath: Map<String, List<FolderListRow>>
    private val directCountByPath: Map<String, Int>
    private val recursiveCountByPath: Map<String, Int>

    val currentPath: MutableStateFlow<String> = MutableStateFlow(ROOT)

    init {
        val raw =
            mapOf(
                ROOT to listOf("Movies", "Series", "Documentaries", "Stand-Up", "Concerts"),
                "/Movies/" to listOf("Action", "Drama", "Sci-Fi", "Animated"),
                "/Movies/Action/" to listOf("Heat", "John Wick", "Mad Max"),
                "/Movies/Drama/" to listOf("There Will Be Blood", "The Master"),
                "/Movies/Sci-Fi/" to listOf("Arrival", "Annihilation", "Dune"),
                "/Movies/Animated/" to emptyList(),
                "/Series/" to listOf("The Wire", "Breaking Bad", "Severance"),
                "/Series/The Wire/" to listOf("Season 1", "Season 2", "Season 3", "Season 4", "Season 5"),
                "/Series/Breaking Bad/" to listOf("Season 1", "Season 2"),
                "/Series/Severance/" to listOf("Season 1", "Season 2"),
                "/Documentaries/" to listOf("Nature", "History"),
                "/Documentaries/Nature/" to listOf("Planet Earth", "Blue Planet"),
                "/Documentaries/History/" to emptyList(),
                "/Stand-Up/" to emptyList(),
                "/Concerts/" to emptyList(),
            )
        val direct =
            mapOf(
                "/Movies/Action/Heat/" to 1,
                "/Movies/Action/John Wick/" to 4,
                "/Movies/Action/Mad Max/" to 4,
                "/Movies/Drama/There Will Be Blood/" to 1,
                "/Movies/Drama/The Master/" to 1,
                "/Movies/Sci-Fi/Arrival/" to 1,
                "/Movies/Sci-Fi/Annihilation/" to 1,
                "/Movies/Sci-Fi/Dune/" to 2,
                "/Series/The Wire/Season 1/" to 13,
                "/Series/The Wire/Season 2/" to 12,
                "/Series/The Wire/Season 3/" to 12,
                "/Series/The Wire/Season 4/" to 13,
                "/Series/The Wire/Season 5/" to 10,
                "/Series/Breaking Bad/Season 1/" to 7,
                "/Series/Breaking Bad/Season 2/" to 13,
                "/Series/Severance/Season 1/" to 9,
                "/Series/Severance/Season 2/" to 10,
                "/Documentaries/Nature/Planet Earth/" to 11,
                "/Documentaries/Nature/Blue Planet/" to 8,
            )

        val rows = mutableMapOf<String, List<FolderListRow>>()
        val recCount = mutableMapOf<String, Int>()
        val allPaths =
            buildSet {
                add(ROOT)
                raw.forEach { (parent, kids) ->
                    add(parent)
                    kids.forEach { add(parent + it + "/") }
                }
                direct.keys.forEach { add(it) }
            }
        for (path in allPaths) {
            val sum = direct.entries.filter { it.key.startsWith(path) }.sumOf { it.value }
            recCount[path] = sum
        }
        for ((parent, names) in raw) {
            rows[parent] =
                names.map { name ->
                    val childPath = parent + name + "/"
                    val recursiveCount = recCount[childPath] ?: 0
                    FolderListRow(
                        node =
                            FolderNode(
                                serverUrl = MOCK_SERVER,
                                path = childPath,
                                name = name,
                                parentPath = parent,
                                recursiveCount = recursiveCount,
                                directCount = direct[childPath] ?: 0,
                                thumbnailUrl =
                                    if (recursiveCount > 0) {
                                        "https://example.test/previews/${name.lowercase().replace(' ', '-')}.jpg"
                                    } else {
                                        null
                                    },
                            ),
                    )
                }
        }
        for (path in allPaths) {
            rows.putIfAbsent(path, emptyList())
        }

        rowsByPath = rows
        directCountByPath = direct
        recursiveCountByPath = recCount
    }

    fun childrenOf(path: String): Flow<List<FolderListRow>> =
        currentPath.map { rowsByPath[path].orEmpty() }

    fun enter(node: FolderNode) {
        currentPath.value = node.path
    }

    fun goUp(): Boolean {
        val current = currentPath.value
        val parent = FoldersViewModel.parentOf(current)
        if (parent == current) return false
        currentPath.value = parent
        return true
    }

    fun directSceneCount(path: String): Int = directCountByPath[path] ?: 0
}
