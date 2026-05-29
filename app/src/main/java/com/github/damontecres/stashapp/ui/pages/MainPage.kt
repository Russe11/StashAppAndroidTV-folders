package com.github.damontecres.stashapp.ui.pages

import android.content.Context
import android.util.Log
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.stashapp.StashApplication
import com.github.damontecres.stashapp.R
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.api.StatisticsQuery
import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.api.fragment.GalleryData
import com.github.damontecres.stashapp.api.fragment.GroupData
import com.github.damontecres.stashapp.api.fragment.ImageData
import com.github.damontecres.stashapp.api.fragment.MarkerData
import com.github.damontecres.stashapp.api.fragment.PerformerData
import com.github.damontecres.stashapp.api.fragment.StudioData
import com.github.damontecres.stashapp.api.fragment.TagData
import com.github.damontecres.stashapp.api.type.CriterionModifier
import com.github.damontecres.stashapp.api.type.HierarchicalMultiCriterionInput
import com.github.damontecres.stashapp.api.type.IntCriterionInput
import com.github.damontecres.stashapp.api.type.MultiCriterionInput
import com.github.damontecres.stashapp.api.type.SceneFilterType
import com.github.damontecres.stashapp.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.navigation.FilterAndPosition
import com.github.damontecres.stashapp.navigation.NavigationManager
import com.github.damontecres.stashapp.playback.PlaybackMode
import com.github.damontecres.stashapp.proto.StashPreferences
import com.github.damontecres.stashapp.proto.UpdatePreferences
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.data.SortAndDirection
import com.github.damontecres.stashapp.data.SortOption
import com.github.damontecres.stashapp.data.StashFindFilter
import com.github.damontecres.stashapp.folders.data.NewItemRow
import com.github.damontecres.stashapp.suppliers.FilterArgs
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.LocalGlobalContext
import com.github.damontecres.stashapp.ui.cards.StashCard
import com.github.damontecres.stashapp.ui.cards.ViewAllCard
import com.github.damontecres.stashapp.ui.compat.Button
import com.github.damontecres.stashapp.ui.components.CircularProgress
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.components.LongClicker
import com.github.damontecres.stashapp.ui.components.RowColumn
import com.github.damontecres.stashapp.ui.components.TitleValueText
import com.github.damontecres.stashapp.ui.components.main.MainPageHeader
import com.github.damontecres.stashapp.ui.isPlayKeyUp
import com.github.damontecres.stashapp.ui.tryRequestFocus
import com.github.damontecres.stashapp.ui.util.CrossFadeFactory
import com.github.damontecres.stashapp.ui.util.OneTimeLaunchedEffect
import com.github.damontecres.stashapp.ui.util.getPlayDestinationForItem
import com.github.damontecres.stashapp.ui.util.ifElse
import com.github.damontecres.stashapp.util.FrontPageParser
import com.github.damontecres.stashapp.util.LoggingCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.RecommendationEngine
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.SurpriseMePicker
import com.github.damontecres.stashapp.util.UpdateChecker
import com.github.damontecres.stashapp.util.isNotNullOrBlank
import com.github.damontecres.stashapp.util.launchIO
import com.github.damontecres.stashapp.views.formatBytes
import com.github.damontecres.stashapp.views.formatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "MainPage"

class MainPageViewModel : ViewModel() {
    private lateinit var server: StashServer
    private val folderDao = StashApplication.getDatabase().folderDao()

    val frontPageRows = mutableStateListOf<FrontPageParser.FrontPageRow.Success>()

    private val _serverStats = MutableLiveData<StatisticsQuery.Stats?>()
    val serverStats: LiveData<StatisticsQuery.Stats?> = _serverStats

    fun init(
        context: Context,
        server: StashServer,
        prefs: StashPreferences,
    ) {
        this.server = server
        val pageSize = prefs.searchPreferences.maxResults
        viewModelScope.launch(LoggingCoroutineExceptionHandler(server, viewModelScope)) {
            val rowTitle = context.getString(R.string.home_newest_videos)
            val newestRows =
                withContext(Dispatchers.IO) {
                    folderDao.newestSceneItems(
                        serverUrl = server.url,
                        limit = pageSize,
                    )
                }
            frontPageRows.clear()
            frontPageRows.add(
                FrontPageParser.FrontPageRow.Success(
                    name = rowTitle,
                    filter = homeNewestVideosFilter(rowTitle),
                    data = newestRows.toHomeNewestScenes(),
                ),
            )
        }
        // Continue Watching + Recommended are derived from engagement metadata on the server,
        // so they lazily append to the front-page rows as each query resolves (the newest-videos
        // row above renders immediately from the local Room cache).
        loadDiscoveryRows(context, server, pageSize)
    }

    /**
     * Fetch the Continue Watching and Recommended rows and append them to [frontPageRows].
     *
     * Both run off a single QueryEngine and use only existing Apollo fields (resume_time,
     * play_count, last_played_at, tags, performers). The recommendation heuristic itself lives in
     * [RecommendationEngine] (pure + unit-tested); this method just supplies it with local data.
     */
    private fun loadDiscoveryRows(
        context: Context,
        server: StashServer,
        pageSize: Int,
    ) {
        viewModelScope.launch(LoggingCoroutineExceptionHandler(server, viewModelScope)) {
            val queryEngine = QueryEngine(server)

            // ---- Continue Watching ----
            val continueTitle = context.getString(R.string.home_continue_watching)
            val continueFilter = continueWatchingFilter(continueTitle)
            val continueScenes =
                withContext(Dispatchers.IO) {
                    queryEngine
                        .findScenes(
                            findFilter = continueFilter.findFilter?.toFindFilterType(1, pageSize),
                            sceneFilter = continueFilter.objectFilter as SceneFilterType?,
                            useRandom = false,
                        ).filterNot(::isEffectivelyFinished)
                }
            if (continueScenes.isNotEmpty()) {
                frontPageRows.add(
                    FrontPageParser.FrontPageRow.Success(
                        name = continueTitle,
                        filter = continueFilter,
                        data = continueScenes,
                    ),
                )
            }

            // ---- Recommended (local-metadata heuristic) ----
            val recommended =
                withContext(Dispatchers.IO) {
                    buildRecommendedRow(context, queryEngine, continueScenes, pageSize)
                }
            if (recommended != null) {
                frontPageRows.add(recommended)
            }
        }
    }

    /**
     * Build the Recommended row using the [RecommendationEngine] heuristic, or null if there is
     * not enough local engagement data to recommend anything yet.
     */
    private suspend fun buildRecommendedRow(
        context: Context,
        queryEngine: QueryEngine,
        continueScenes: List<SlimSceneData>,
        pageSize: Int,
    ): FrontPageParser.FrontPageRow.Success? {
        // Seed: the user's most-recently-played scenes (the basis for "more of what you watch").
        val recentlyPlayed =
            queryEngine.findScenes(
                findFilter =
                    StashFindFilter(
                        SortAndDirection(SortOption.LastPlayedAt, SortDirectionEnum.DESC),
                    ).toFindFilterType(1, RECOMMENDED_SEED_SIZE),
                sceneFilter = playedScenesFilter(),
                useRandom = false,
            )
        val profile = RecommendationEngine.buildProfile(recentlyPlayed)
        if (profile.isEmpty) return null

        val recommendedFilter = recommendedFilter(context, profile)
        // Over-fetch a candidate pool so client-side ranking + dedup still fills the row.
        val candidates =
            queryEngine.findScenes(
                findFilter = recommendedFilter.findFilter?.toFindFilterType(1, pageSize * 3),
                sceneFilter = recommendedFilter.objectFilter as SceneFilterType?,
                useRandom = false,
            )
        // Never recommend something already watched or already in Continue Watching.
        val exclude =
            (recentlyPlayed.map { it.id } + continueScenes.map { it.id }).toSet()
        val ranked =
            RecommendationEngine.rankRecommendations(
                candidates = candidates,
                profile = profile,
                excludeSceneIds = exclude,
                limit = pageSize,
            )
        if (ranked.isEmpty()) return null
        return FrontPageParser.FrontPageRow.Success(
            name = context.getString(R.string.home_recommended),
            filter = recommendedFilter,
            data = ranked,
        )
    }

    /** True while a Surprise Me fetch is in flight, so the UI can disable/spin the control. */
    private val _surpriseMeLoading = MutableLiveData(false)
    val surpriseMeLoading: LiveData<Boolean> = _surpriseMeLoading

    /**
     * "Surprise Me": fetch a small page of randomly-sorted scenes from the server and open one of
     * them for playback — low-friction couch discovery.
     *
     * The randomness is server-sourced: we request [SortOption.Random], which [QueryEngine]
     * resolves to a fresh `random_<seed>` sort on every call, so each press reshuffles the
     * candidate pool. The choice among the returned candidates is the pure, unit-tested
     * [SurpriseMePicker] (which also biases toward not-yet-watched scenes). Pass a [sceneFilter]
     * to respect an active filter (e.g. from a filtered scene list); null means the whole library.
     */
    fun surpriseMe(
        server: StashServer,
        navigationManager: NavigationManager,
        sceneFilter: SceneFilterType? = null,
    ) {
        if (_surpriseMeLoading.value == true) return
        _surpriseMeLoading.value = true
        viewModelScope.launch(LoggingCoroutineExceptionHandler(server, viewModelScope)) {
            try {
                val candidates =
                    withContext(Dispatchers.IO) {
                        QueryEngine(server).findScenes(
                            findFilter =
                                StashFindFilter(SortAndDirection.random())
                                    .toFindFilterType(1, SURPRISE_ME_CANDIDATES),
                            sceneFilter = sceneFilter,
                            useRandom = true,
                        )
                    }
                val pick = SurpriseMePicker.pick(candidates) ?: return@launch
                val destination =
                    getPlayDestinationForItem(server, pick, null)
                        ?: Destination.Playback(pick.id, 0L, PlaybackMode.Choose)
                navigationManager.navigate(destination)
            } finally {
                _surpriseMeLoading.value = false
            }
        }
    }

    fun checkForUpdate(
        context: Context,
        prefs: UpdatePreferences,
    ) {
        if (prefs.checkForUpdates) {
            viewModelScope.launchIO {
                try {
                    UpdateChecker.maybeShowUpdateToast(
                        context,
                        prefs.updateUrl,
                        false,
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Error checking for updates", ex)
                }
            }
        }
    }

    fun updateStatistics() {
        viewModelScope.launch(StashCoroutineExceptionHandler()) {
            val queryEngine = QueryEngine(server)
            _serverStats.value = queryEngine.executeQuery(StatisticsQuery()).data?.stats
        }
    }
}

internal fun List<NewItemRow>.toHomeNewestScenes(): List<SlimSceneData> =
    filter(NewItemRow::isScene).map(NewItemRow::toHomeNewestScene)

private fun NewItemRow.toHomeNewestScene(): SlimSceneData =
    SlimSceneData(
        id = itemId,
        title = homeNewestVideoTitle(),
        code = null,
        details = null,
        director = null,
        urls = emptyList(),
        date = null,
        rating100 = null,
        play_count = null,
        play_duration = null,
        o_counter = null,
        organized = false,
        resume_time = null,
        created_at = null,
        updated_at = updatedAtEpochMs,
        files = emptyList(),
        paths =
            SlimSceneData.Paths(
                screenshot = thumbnailUrl,
                preview = previewUrl,
                stream = null,
                sprite = null,
                caption = null,
            ),
        scene_markers = emptyList(),
        galleries = emptyList(),
        studio = null,
        groups = emptyList(),
        tags = emptyList(),
        performers = emptyList(),
    )

private fun NewItemRow.homeNewestVideoTitle(): String {
    val trimmedTitle = title?.trim().orEmpty()
    if (trimmedTitle.isNotEmpty()) return trimmedTitle
    return path.substringAfterLast('/').substringBeforeLast('.').trim().ifEmpty { "Untitled" }
}

private fun homeNewestVideosFilter(name: String): FilterArgs =
    FilterArgs(
        dataType = DataType.SCENE,
        name = name,
        findFilter =
            StashFindFilter(
                SortAndDirection(
                    SortOption.UpdatedAt,
                    SortDirectionEnum.DESC,
                ),
            ),
    )

/** How many recently-played scenes to sample when building the recommendation profile. */
private const val RECOMMENDED_SEED_SIZE = 50

/**
 * Size of the randomly-sorted candidate pool a Surprise Me press fetches. Small (so the query is
 * cheap and the server does the shuffling) but >1 so [SurpriseMePicker]'s prefer-unwatched bias
 * has something to choose from.
 */
private const val SURPRISE_ME_CANDIDATES = 25

/**
 * Fraction of a scene's duration past which a saved resume position is treated as "finished"
 * (so a scene the user watched to the end doesn't clutter Continue Watching). Combined with an
 * absolute end-margin so very long scenes don't need the full last few minutes watched.
 */
private const val FINISHED_FRACTION = 0.95
private const val FINISHED_END_MARGIN_SECONDS = 30.0

/**
 * Continue Watching: scenes with a saved resume position, most-recently-played first.
 *
 * The `resume_time > 0` predicate is pushed to the server; the "effectively finished" trim
 * (resume near the end of the file) is done client-side in [isEffectivelyFinished] because the
 * server can't compare resume_time against per-file duration in a single criterion.
 */
private fun continueWatchingFilter(name: String): FilterArgs =
    FilterArgs(
        dataType = DataType.SCENE,
        name = name,
        findFilter =
            StashFindFilter(
                SortAndDirection(SortOption.LastPlayedAt, SortDirectionEnum.DESC),
            ),
        objectFilter =
            SceneFilterType(
                resume_time =
                    Optional.present(
                        IntCriterionInput(value = 0, modifier = CriterionModifier.GREATER_THAN),
                    ),
            ),
    )

/** A scene is hidden from Continue Watching once its resume position is near the end. */
internal fun isEffectivelyFinished(scene: SlimSceneData): Boolean {
    val resume = scene.resume_time ?: return false
    if (resume <= 0.0) return true
    val duration =
        scene.files.firstOrNull()?.videoFile?.duration?.takeIf { it > 0.0 } ?: return false
    return resume >= duration * FINISHED_FRACTION ||
        resume >= duration - FINISHED_END_MARGIN_SECONDS
}

/** Scenes the user has actually played at least once — the seed for recommendations. */
private fun playedScenesFilter(): SceneFilterType =
    SceneFilterType(
        play_count =
            Optional.present(
                IntCriterionInput(value = 0, modifier = CriterionModifier.GREATER_THAN),
            ),
    )

/**
 * Recommended: candidate pool of scenes that share the user's top tags or performers, highest
 * rated first. The final affinity ranking + dedup against already-watched scenes happens
 * client-side in [RecommendationEngine.rankRecommendations].
 */
private fun recommendedFilter(
    context: Context,
    profile: RecommendationEngine.AffinityProfile,
): FilterArgs =
    FilterArgs(
        dataType = DataType.SCENE,
        name = context.getString(R.string.home_recommended),
        findFilter =
            StashFindFilter(
                SortAndDirection(SortOption.Rating, SortDirectionEnum.DESC),
            ),
        objectFilter =
            SceneFilterType(
                tags =
                    if (profile.tagIds.isNotEmpty()) {
                        Optional.present(
                            HierarchicalMultiCriterionInput(
                                value = Optional.present(profile.tagIds),
                                modifier = CriterionModifier.INCLUDES,
                                depth = Optional.present(0),
                            ),
                        )
                    } else {
                        Optional.Absent
                    },
                performers =
                    if (profile.performerIds.isNotEmpty()) {
                        Optional.present(
                            MultiCriterionInput(
                                value = Optional.present(profile.performerIds),
                                modifier = CriterionModifier.INCLUDES,
                            ),
                        )
                    } else {
                        Optional.Absent
                    },
            ),
    )

@Composable
fun MainPage(
    server: StashServer,
    uiConfig: ComposeUiConfig,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    modifier: Modifier = Modifier,
    viewModel: MainPageViewModel = viewModel(),
) {
    val context = LocalContext.current
    OneTimeLaunchedEffect {
        viewModel.init(context, server, uiConfig.preferences)
    }

    val frontPageRows = viewModel.frontPageRows // .observeAsState(listOf())
    val serverStats by viewModel.serverStats.observeAsState()
    val surpriseMeLoading by viewModel.surpriseMeLoading.observeAsState(false)
    val navigationManager = LocalGlobalContext.current.navigationManager

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate(context, uiConfig.preferences.updatePreferences)
        viewModel.updateStatistics()
    }
    if (frontPageRows.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            CircularProgress(
                modifier =
                    Modifier
                        .size(160.dp)
                        .align(Alignment.Center),
            )
        }
    } else {
        LaunchedEffect(server, frontPageRows) {
            focusRequester.tryRequestFocus()
        }
        HomePage(
            modifier = modifier.focusRequester(focusRequester),
            server = server,
            serverStats = serverStats,
            uiConfig = uiConfig,
            rows = frontPageRows,
            itemOnClick = itemOnClick,
            longClicker = longClicker,
            surpriseMeLoading = surpriseMeLoading,
            // No sceneFilter: the home button surprises across the whole library. A filtered
            // scene list can call viewModel.surpriseMe(server, nav, sceneFilter) to respect its
            // active filter.
            onSurpriseMe = { viewModel.surpriseMe(server, navigationManager) },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomePage(
    server: StashServer,
    serverStats: StatisticsQuery.Stats?,
    uiConfig: ComposeUiConfig,
    rows: List<FrontPageParser.FrontPageRow.Success>,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    modifier: Modifier = Modifier,
    surpriseMeLoading: Boolean = false,
    onSurpriseMe: () -> Unit = {},
) {
    var focusedItem by remember { mutableStateOf<Any?>(null) }
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by rememberSaveable { mutableStateOf(RowColumn(0, 0)) }
    var focusedRow by rememberSaveable { mutableIntStateOf(0) }

    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedRow)
    }

    Box(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        focusedItem?.let { item ->
            val imageUrl =
                when (item) {
                    is SlimSceneData -> item.paths.screenshot
                    is ImageData -> item.paths.image
                    is PerformerData -> item.image_path
                    is StudioData -> item.image_path
                    is TagData -> item.image_path
                    is MarkerData -> item.screenshot
                    is GroupData -> item.front_image_path
                    is GalleryData -> item.paths.cover
                    else -> null
                }
            if (imageUrl.isNotNullOrBlank()) {
                val gradientColor = MaterialTheme.colorScheme.background
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(imageUrl)
                            .transitionFactory(CrossFadeFactory(250.milliseconds))
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopEnd,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight(.85f)
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.verticalGradient(
                                        colorStops =
                                            arrayOf(
                                                0f to Color.Transparent,
                                                .9f to gradientColor,
                                            ),
                                        startY = 0f,
                                    ),
                                )
                                drawRect(
                                    Brush.horizontalGradient(
                                        colorStops =
                                            arrayOf(
                                                0f to Color.Transparent,
                                                .8f to gradientColor,
                                            ),
                                        startX = size.width * .33f,
                                        endX = 0f,
                                    ),
                                )
//                                drawLine(
//                                    color = Color.Red,
//                                    start = Offset(x = 0f, y = size.height * .5f),
//                                    end = Offset(x = size.width, y = size.height),
//                                )
//                                drawLine(
//                                    color = Color.Red,
//                                    start = Offset.Zero,
//                                    end = Offset(x = size.width, y = size.height),
//                                )
                            },
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
        ) {
            focusedItem?.let { item ->
                MainPageHeader(
                    item = item,
                    uiConfig = uiConfig,
                    modifier = Modifier.fillMaxWidth(.7f),
                )
            }

            SurpriseMeButton(
                loading = surpriseMeLoading,
                onClick = onSurpriseMe,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 72.dp),
                modifier =
                    Modifier
                        .focusGroup()
                        .focusRestorer { focusRequester },
            ) {
                itemsIndexed(rows) { index, row ->
                    HomePageRow(
                        uiConfig,
                        row,
                        itemOnClick,
                        longClicker,
                        onFocus = { idx, item ->
                            focusedIndex = RowColumn(index, idx)
                            focusedItem = item
                            focusedRow = index
                        },
                        rowFocusRequester = if (index == focusedIndex.row) focusRequester else null,
                        modifier = Modifier,
                    )
                }
                item {
                    ServerStatsRow(
                        server = server,
                        serverStats = serverStats,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

/**
 * The home-screen "Surprise Me" control: one press fetches a random scene and starts it. While a
 * pick is in flight the button is disabled and shows an inline spinner so a couch user gets
 * immediate feedback and can't fire overlapping fetches.
 */
@Composable
fun SurpriseMeButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.home_surprise_me))
    }
}

@Composable
fun HomePageRow(
    uiConfig: ComposeUiConfig,
    row: FrontPageParser.FrontPageRow.Success,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    onFocus: (Int, Any) -> Unit,
    rowFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val navigationManager = LocalGlobalContext.current.navigationManager
    Column(modifier = modifier) {
        ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onBackground)) {
            Text(
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 16.dp),
                text = row.name,
            )
        }
        val firstFocus = remember { FocusRequester() }
        var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
        val rowModifier =
            if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier
        val server = LocalGlobalContext.current.server
        LazyRow(
            modifier =
                rowModifier
                    .onFocusChanged {
                        if (it.isFocused) {
                            firstFocus.tryRequestFocus()
                        }
                    }.focusGroup()
                    .focusRestorer(firstFocus)
                    .fillMaxWidth()
                    .onKeyEvent {
                        if (isPlayKeyUp(it)) {
                            val destination =
                                getPlayDestinationForItem(
                                    server,
                                    row.data[focusedIndex],
                                    FilterAndPosition(row.filter, focusedIndex),
                                )
                            return@onKeyEvent if (destination != null) {
                                navigationManager.navigate(destination)
                                true
                            } else {
                                false
                            }
                        }
                        return@onKeyEvent false
                    },
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(row.data) { index, item ->
                if (item != null) {
                    val cardModifier =
                        if (index == focusedIndex) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }.onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                focusedIndex = index
                                onFocus(index, item)
                            }
                        }
                    StashCard(
                        modifier = cardModifier,
                        uiConfig = uiConfig,
                        item = item,
                        itemOnClick = {
                            itemOnClick.onClick(
                                item,
                                FilterAndPosition(row.filter, index),
                            )
                        },
                        longClicker = longClicker,
                        getFilterAndPosition = { FilterAndPosition(row.filter, index) },
                    )
                }
            }
            if (row.data.isNotEmpty()) {
                item {
                    ViewAllCard(
                        modifier =
                            Modifier
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        focusedIndex = row.data.size
                                        onFocus(row.data.size, row.filter)
                                    }
                                }.ifElse(
                                    focusedIndex == row.data.size,
                                    Modifier.focusRequester(firstFocus),
                                ),
                        filter = row.filter,
                        itemOnClick = {
                            itemOnClick.onClick(
                                row.filter,
                                FilterAndPosition(row.filter, row.data.size),
                            )
                        },
                        longClicker = longClicker,
                        uiConfig = uiConfig,
                        getFilterAndPosition = { FilterAndPosition(row.filter, row.data.size) },
                    )
                }
            }
        }
    }
}

@Composable
fun ServerStatsRow(
    server: StashServer,
    serverStats: StatisticsQuery.Stats?,
    modifier: Modifier = Modifier,
) {
    serverStats?.let { stats ->
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TitleValueText(
                stringResource(R.string.stashapp_scenes),
                formatNumber(stats.scene_count, server.serverPreferences.abbreviateCounters),
            )
            TitleValueText(
                stringResource(R.string.stashapp_stats_scenes_size),
                formatBytes(stats.scenes_size.toLong()),
            )
            TitleValueText(
                stringResource(R.string.stashapp_images),
                formatNumber(stats.image_count, server.serverPreferences.abbreviateCounters),
            )
            TitleValueText(
                stringResource(R.string.stashapp_stats_image_size),
                formatBytes(stats.images_size.toLong()),
            )
            TitleValueText(
                stringResource(R.string.stashapp_stats_total_play_count),
                formatNumber(stats.total_play_count, server.serverPreferences.abbreviateCounters),
            )
            TitleValueText(
                stringResource(R.string.stashapp_stats_total_play_duration),
                stats.total_play_duration
                    .toLong()
                    .seconds
                    .toString(),
            )
            TitleValueText(
                stringResource(R.string.stashapp_stats_total_o_count),
                formatNumber(stats.total_o_count, server.serverPreferences.abbreviateCounters),
            )
        }
    }
}
