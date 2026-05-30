package com.github.damontecres.stashapp.ui.nav

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import com.github.damontecres.stashapp.PreferenceScreenOption
import com.github.damontecres.stashapp.api.fragment.ImageData
import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.api.fragment.StashData
import com.github.damontecres.stashapp.api.fragment.TagData
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.navigation.FilterAndPosition
import com.github.damontecres.stashapp.navigation.NavigationManagerCompose
import com.github.damontecres.stashapp.proto.StashPreferences
import com.github.damontecres.stashapp.suppliers.FilterArgs
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.LocalSceneCurationOverrides
import com.github.damontecres.stashapp.ui.NavDrawerFragment.Companion.TAG
import com.github.damontecres.stashapp.ui.compat.isTvDevice
import com.github.damontecres.stashapp.ui.components.DefaultLongClicker
import com.github.damontecres.stashapp.ui.components.DialogPopup
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.components.MarkerDurationDialog
import com.github.damontecres.stashapp.ui.components.SceneQuickActionHandlers
import com.github.damontecres.stashapp.ui.pages.DialogParams
import com.github.damontecres.stashapp.ui.pages.SearchForDialog
import com.github.damontecres.stashapp.util.MutationEngine
import com.github.damontecres.stashapp.util.SceneCurationOverrides
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.StashServer
import dev.olshevski.navigation.reimagined.NavController
import dev.olshevski.navigation.reimagined.NavHost
import kotlinx.coroutines.launch

/**
 * Shows the actual compose content of the application
 *
 * This is a Navigation Drawer and its content or a full screen destination
 */
@Composable
fun ApplicationContent(
    server: StashServer,
    preferences: StashPreferences,
    navigationManager: NavigationManagerCompose,
    navController: NavController<Destination>,
    onChangeTheme: (String?) -> Unit,
    onSwitchServer: (StashServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var composeUiConfig by remember(server) {
        mutableStateOf(
            ComposeUiConfig.fromStashServer(
                preferences,
                server,
            ),
        )
    }

    val scrollToNextPage = preferences.interfacePreferences.scrollNextViewAll

    val itemOnClick =
        ItemOnClicker { item: Any, filterAndPosition ->
            when (item) {
                is FilterArgs -> {
                    navigationManager.navigate(
                        Destination.Filter(
                            item,
                            scrollToNextPage,
                        ),
                    )
                }

                is ImageData -> {
                    val (filter, position) = filterAndPosition!!
                    navigationManager.navigate(
                        Destination.Slideshow(
                            filter,
                            position,
                            false,
                        ),
                    )
                }

                is StashData -> {
                    navigationManager.navigate(
                        Destination.fromStashData(item),
                    )
                }

                else -> {
                    Toast
                        .makeText(
                            context,
                            "Unknown item. This is probably a bug",
                            Toast.LENGTH_SHORT,
                        ).show()
                    Log.e(TAG, "Unknown item type: ${item::class.qualifiedName}")
                }
            }
        }

    var dialogParams by remember { mutableStateOf<DialogParams?>(null) }
    var showMarkerDialog by remember { mutableStateOf<FilterAndPosition?>(null) }

    // Optimistic, in-memory curation overrides applied from scene-card quick actions.
    // Cards read these through LocalSceneCurationOverrides so an edit shows immediately
    // even though the paged list source still holds the pre-edit value. The handlers
    // object captures the MutableState directly (not the `by`-delegated local) so the
    // remembered instance always reads/writes the live value.
    val curationOverridesState = remember(server) { mutableStateOf(SceneCurationOverrides()) }
    val curationOverrides = curationOverridesState.value
    var addTagToScene by remember { mutableStateOf<SlimSceneData?>(null) }
    val curationScope = rememberCoroutineScope()
    val mutationEngine = remember(server) { MutationEngine(server) }

    val quickActionHandlers =
        remember(server) {
            object : SceneQuickActionHandlers {
                override fun currentOrganized(scene: SlimSceneData): Boolean =
                    curationOverridesState.value.effectiveOrganized(scene.id, scene.organized)

                override fun currentRating100(scene: SlimSceneData): Int? =
                    curationOverridesState.value.effectiveRating100(scene.id, scene.rating100)

                override fun onToggleOrganized(
                    scene: SlimSceneData,
                    newValue: Boolean,
                ) {
                    // Optimistic first, then write back; on failure the toast-handler
                    // surfaces the error and the next list refresh corrects the display.
                    curationOverridesState.value =
                        curationOverridesState.value.withOrganized(scene.id, newValue)
                    curationScope.launch(StashCoroutineExceptionHandler(autoToast = true)) {
                        mutationEngine.setOrganizedOnScene(scene.id, newValue)
                    }
                }

                override fun onSetRating(
                    scene: SlimSceneData,
                    rating100: Int,
                ) {
                    curationOverridesState.value =
                        curationOverridesState.value.withRating100(scene.id, rating100)
                    curationScope.launch(StashCoroutineExceptionHandler(autoToast = true)) {
                        mutationEngine.setRating(scene.id, rating100)
                    }
                }

                override fun onIncrementOCounter(scene: SlimSceneData) {
                    curationScope.launch(StashCoroutineExceptionHandler(autoToast = true)) {
                        val result = mutationEngine.incrementOCounter(scene.id)
                        curationOverridesState.value =
                            curationOverridesState.value.withOCounter(scene.id, result.count)
                    }
                }

                override fun onAddTag(scene: SlimSceneData) {
                    addTagToScene = scene
                }
            }
        }

    val longClicker =
        remember {
            DefaultLongClicker(
                navigationManager,
                itemOnClick,
                server.serverPreferences.alwaysStartFromBeginning,
                markerPlayAllOnClick = { showMarkerDialog = it },
                quickActionHandlers =
                    if (composeUiConfig.readOnlyModeDisabled) quickActionHandlers else null,
            ) { dialogParams = it }
        }

    val pages =
        buildList {
            add(DrawerPage.SearchPage)
            add(DrawerPage.HomePage)
            add(DrawerPage.NewPage)
            add(DrawerPage.FoldersPage)
            addAll(
                DataType.entries
                    .filter(::isTopLevelMenuDataType)
                    .filter { server.serverPreferences.showMenuItem(it) }
                    .map { DrawerPage.DataTypePage(it) },
            )
            add(DrawerPage.SettingPage)
        }
    val defaultSelection: DrawerPage = DrawerPage.HomePage
    var selectedScreen by rememberSaveable { mutableStateOf<DrawerPage?>(defaultSelection) }

    // TODO Using AnimatedNavHost breaks the grid focus restoration
//    AnimatedNavHost(
//        controller = navController,
//        transitionSpec = DestinationTransitionSpec(),
//        modifier = modifier,
//    ) { destination ->
    CompositionLocalProvider(LocalSceneCurationOverrides provides curationOverrides) {
        NavHost(
            controller = navController,
            modifier = modifier,
        ) { destination ->
        LaunchedEffect(Unit) {
            // Refresh server preferences on each page change
            navigationManager.serverViewModel.updateServerPreferences()
            composeUiConfig = ComposeUiConfig.fromStashServer(preferences, server)

            navigationManager.previousDestination = destination
            navigationManager.serverViewModel.setCurrentDestination(destination)
        }
        val fullScreen = if (isTvDevice) destination.fullScreen else destination.fullScreenTouch

        if (fullScreen) {
            DestinationContent(
                navManager = navigationManager,
                server = server,
                destination = destination,
                composeUiConfig = composeUiConfig,
                itemOnClick = itemOnClick,
                longClicker = longClicker,
                onChangeTheme = onChangeTheme,
                onSwitchServer = onSwitchServer,
                modifier = Modifier.fillMaxSize(),
                onUpdateTitle = null,
            )
        } else {
            // Highlight on the nav drawer as user navigates around the app
            selectedScreen =
                when (destination) {
                    Destination.Main -> {
                        DrawerPage.HomePage
                    }

                    Destination.Search -> {
                        DrawerPage.SearchPage
                    }

                    Destination.New -> {
                        DrawerPage.NewPage
                    }

                    is Destination.Folders -> {
                        DrawerPage.FoldersPage
                    }

                    Destination.SettingsPin,
                    is Destination.Settings,
                    -> {
                        DrawerPage.SettingPage
                    }

                    is Destination.Item -> {
                        pages.firstOrNull {
                            it is DrawerPage.DataTypePage && it.dataType == destination.dataType
                        }
                    }

                    is Destination.MarkerDetails -> {
                        pages.firstOrNull {
                            it is DrawerPage.DataTypePage && it.dataType == DataType.MARKER
                        }
                    }

                    is Destination.Filter -> {
                        pages.firstOrNull {
                            it is DrawerPage.DataTypePage && it.dataType == destination.filterArgs.dataType
                        }
                    }

                    else -> {
                        null
                    }
                }

            val onSelectScreen = { page: DrawerPage ->
                val refreshMain =
                    selectedScreen == DrawerPage.HomePage && page == DrawerPage.HomePage
                Log.v(
                    TAG,
                    "Navigating to $page",
                )
                selectedScreen = page
                if (refreshMain) {
                    navigationManager.goToMain()
                } else {
                    val pageDest =
                        when (page) {
                            DrawerPage.HomePage -> {
                                Destination.Main
                            }

                            DrawerPage.SearchPage -> {
                                Destination.Search
                            }

                            DrawerPage.NewPage -> {
                                Destination.New
                            }

                            DrawerPage.FoldersPage -> {
                                Destination.Folders()
                            }

                            DrawerPage.SettingPage -> {
                                if (composeUiConfig.readOnlyModeDisabled) {
                                    Destination.Settings(
                                        PreferenceScreenOption.BASIC,
                                    )
                                } else {
                                    Destination.SettingsPin
                                }
                            }

                            is DrawerPage.DataTypePage -> {
                                Destination.Filter(
                                    server.serverPreferences.getDefaultFilter(
                                        page.dataType,
                                    ),
                                )
                            }
                        }
                    navigationManager.navigateFromNavDrawer(pageDest)
                }
            }

            if (isTvDevice) {
                NavDrawer(
                    server = server,
                    navigationManager = navigationManager,
                    composeUiConfig = composeUiConfig,
                    destination = destination,
                    selectedScreen = selectedScreen,
                    pages = pages,
                    itemOnClick = itemOnClick,
                    longClicker = longClicker,
                    onSelectScreen = onSelectScreen,
                    onChangeTheme = onChangeTheme,
                    onSwitchServer = onSwitchServer,
                    modifier = Modifier,
                )
            } else {
                NavScaffold(
                    server = server,
                    navigationManager = navigationManager,
                    composeUiConfig = composeUiConfig,
                    destination = destination,
                    selectedScreen = selectedScreen,
                    pages = pages,
                    itemOnClick = itemOnClick,
                    longClicker = longClicker,
                    onSelectScreen = onSelectScreen,
                    onChangeTheme = onChangeTheme,
                    onSwitchServer = onSwitchServer,
                    modifier = Modifier,
                )
            }
        }
        dialogParams?.let { params ->
            DialogPopup(
                showDialog = true,
                title = params.title,
                dialogItems = params.items,
                onDismissRequest = { dialogParams = null },
                dismissOnClick = true,
                waitToLoad = true,
                properties = DialogProperties(),
            )
        }
        if (showMarkerDialog != null) {
            MarkerDurationDialog(
                onDismissRequest = { showMarkerDialog = null },
                onClick = {
                    showMarkerDialog?.let { filterAndPosition ->
                        val dest =
                            Destination.Playlist(
                                filterAndPosition.filter,
                                filterAndPosition.position,
                                it,
                            )
                        navigationManager.navigate(dest)
                    }
                    showMarkerDialog = null
                },
            )
        }
        // Quick "add tag" from a scene card: reuse the searchable + recent/suggested
        // tag picker, then write back via setTagsOnScene (existing tags + the new one).
        addTagToScene?.let { scene ->
            SearchForDialog(
                show = true,
                dataType = DataType.TAG,
                onItemClick = { item ->
                    addTagToScene = null
                    if (item is TagData) {
                        val existing = scene.tags.map { it.slimTagData.id }
                        if (item.id !in existing) {
                            curationScope.launch(StashCoroutineExceptionHandler(autoToast = true)) {
                                mutationEngine.setTagsOnScene(scene.id, existing + item.id)
                            }
                        }
                    }
                },
                onDismissRequest = { addTagToScene = null },
                uiConfig = composeUiConfig,
                dismissOnClick = true,
            )
        }
    }
    }
}

internal fun isTopLevelMenuDataType(dataType: DataType): Boolean =
    when (dataType) {
        DataType.GROUP,
        DataType.PERFORMER,
        DataType.STUDIO,
        -> false

        DataType.SCENE,
        DataType.MARKER,
        DataType.TAG,
        DataType.IMAGE,
        DataType.GALLERY,
        -> true
    }
