package com.github.damontecres.stashapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.StashApplication
import com.github.damontecres.stashapp.api.fragment.StashData
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.data.StashFindFilter
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.proto.TabType
import com.github.damontecres.stashapp.suppliers.FilterArgs
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.FilterViewModel
import com.github.damontecres.stashapp.ui.LocalGlobalContext
import com.github.damontecres.stashapp.ui.cards.CardContext
import com.github.damontecres.stashapp.ui.compat.isTvDevice
import com.github.damontecres.stashapp.ui.filterArgsSaver
import com.github.damontecres.stashapp.ui.tryRequestFocus
import com.github.damontecres.stashapp.ui.util.OneTimeLaunchedEffect
import com.github.damontecres.stashapp.util.PageFilterKey
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TabPage(
    name: AnnotatedString,
    rememberTab: Boolean,
    tabs: List<TabProvider>,
    dataType: DataType,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val rememberTabKey = context.getString(R.string.pref_key_ui_remember_tab) + ".${dataType.name}"

    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(
            if (rememberTab) {
                preferences.getInt(
                    rememberTabKey,
                    0,
                )
            } else {
                0
            },
        )
    }
    val tabRowFocusRequester = remember { FocusRequester() }
    var showTabRowRaw by rememberSaveable { mutableStateOf(true) }
    val showTabRow by remember { derivedStateOf { showTabRowRaw } }
    val focusRequesters = remember { List(tabs.size) { FocusRequester() } }

    // Capture device type once in composable scope so LaunchedEffect lambdas (coroutine scope) can read it.
    val isTV = isTvDevice

    // TV: debounce resolvedTabIndex so rapid D-pad scrolling skips intermediate renders.
    // Touch: no debounce — the pager handles smooth transitions.
    var resolvedTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    LaunchedEffect(selectedTabIndex) {
        if (isTV) {
            // Add a slight delay so if scrolling quickly through tabs, can skip rendering the skipped tabs
            delay(200.milliseconds)
        }
        resolvedTabIndex = selectedTabIndex
        if (rememberTab) {
            preferences.edit { putInt(rememberTabKey, resolvedTabIndex) }
        }
    }

    // Always create pager state (Compose rules forbid conditional remember calls).
    // On TV it is created but never used; on touch it drives the HorizontalPager.
    val pageCount = if (tabs.isNotEmpty()) tabs.size else 1
    val pagerState = rememberPagerState(initialPage = selectedTabIndex) { pageCount }
    val coroutineScope = rememberCoroutineScope()

    // Touch: keep selected tab index in sync when the user swipes the pager.
    // TV: pagerState exists but is unused; isTV is stable for the lifetime of the composition.
    LaunchedEffect(pagerState) {
        if (!isTV) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                selectedTabIndex = page
            }
        }
    }

    OneTimeLaunchedEffect {
        tabRowFocusRequester.tryRequestFocus()
    }
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        if (showTitle) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally),
            )
        }
        AnimatedVisibility(
            showTabRow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            if (isTvDevice) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier =
                        Modifier
                            .focusRestorer(focusRequesters[selectedTabIndex])
                            .focusRequester(tabRowFocusRequester),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        key(index) {
                            Tab(
                                selected = index == selectedTabIndex,
                                onFocus = { selectedTabIndex = index },
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .focusRequester(focusRequesters[index]),
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                                    Text(
                                        text = tab.name,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = 16.dp,
                                                vertical = 6.dp,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Touch: tap-to-switch tab strip; pager animates to page on tap
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        key(index) {
                            androidx.compose.material3.Tab(
                                selected = index == selectedTabIndex,
                                onClick = {
                                    selectedTabIndex = index
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                text = {
                                    androidx.compose.material3.Text(
                                        text = tab.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = 16.dp,
                                                vertical = 6.dp,
                                            ),
                                    )
                                },
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
            }
        }
        if (tabs.isNotEmpty()) {
            if (isTV) {
                // TV: render single tab content with debounced index (unchanged behavior)
                tabs[resolvedTabIndex].content(this) { columns, position ->
                    showTabRowRaw = position < columns
                }
            } else {
                // Touch: swipeable pager — each page renders one tab's content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        tabs[page].content(this) { columns, position ->
                            showTabRowRaw = position < columns
                        }
                    }
                }
            }
        }
    }
}

data class TabProvider(
    val name: String,
    val type: TabType,
    val content: @Composable ColumnScope.(
        /**
         * Callback when grid position changes, passed to [StashGrid]. None-StashGrid can probably ignore this
         */
        positionCallback: (columns: Int, position: Int) -> Unit,
    ) -> Unit,
)

fun createTabFunc(
    server: StashServer,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    composeUiConfig: ComposeUiConfig,
): (initialFilter: FilterArgs) -> TabProvider =
    { initialFilter ->
        val name =
            StashApplication.getApplication().getString(initialFilter.dataType.pluralStringId)
        val type =
            when (initialFilter.dataType) {
                DataType.SCENE -> TabType.SCENES
                DataType.GROUP -> TabType.GROUPS
                DataType.MARKER -> TabType.MARKERS
                DataType.PERFORMER -> TabType.PERFORMERS
                DataType.STUDIO -> TabType.STUDIOS
                DataType.TAG -> TabType.TAGS
                DataType.IMAGE -> TabType.IMAGES
                DataType.GALLERY -> TabType.GALLERIES
            }
        TabProvider(name, type) { positionCallback ->
            var filter by rememberSaveable(name, saver = filterArgsSaver) {
                mutableStateOf(
                    initialFilter,
                )
            }
            StashGridTab(
                name = name,
                server = server,
                initialFilter = filter,
                itemOnClick = itemOnClick,
                longClicker = longClicker,
                modifier = Modifier,
                positionCallback = positionCallback,
                composeUiConfig = composeUiConfig,
                onFilterChange = {
                    filter = it
                },
            )
        }
    }

@Composable
fun StashGridTab(
    name: String,
    server: StashServer,
    initialFilter: FilterArgs,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    composeUiConfig: ComposeUiConfig,
    onFilterChange: (FilterArgs) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilterViewModel = viewModel(key = name),
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    subToggleLabel: String? = null,
    onSubToggleCheck: ((Boolean) -> Unit)? = null,
    subToggleChecked: Boolean = false,
    subToggleEnabled: Boolean = true,
    cardContext: ((index: Int, item: StashData) -> CardContext)? = null,
) {
    val navigationManager = LocalGlobalContext.current.navigationManager
    LaunchedEffect(server, initialFilter) {
        viewModel.setFilter(server, initialFilter, composeUiConfig.cardSettings.columns)
    }
    val pager by viewModel.pager.observeAsState()
    pager?.let { newPager ->
        StashGridControls(
            server = server,
            pager = newPager,
            initialPosition = -1,
            itemOnClick = itemOnClick,
            longClicker = longClicker,
            filterUiMode = FilterUiMode.CREATE_FILTER,
            createFilter = {
                navigationManager.navigate(
                    Destination.CreateFilter(
                        dataType = newPager.filter.dataType,
                        startingFilter = newPager.filter,
                    ),
                )
            },
            modifier = modifier,
            positionCallback = positionCallback,
            uiConfig = composeUiConfig,
            updateFilter = { onFilterChange?.invoke(it) },
            letterPosition = viewModel::findLetterPosition,
            subToggleLabel = subToggleLabel,
            onSubToggleCheck = onSubToggleCheck,
            subToggleChecked = subToggleChecked,
            subToggleEnabled = subToggleEnabled,
            requestFocus = false,
            cardContext = cardContext,
        )
    }
}

fun tabFindFilter(
    server: StashServer,
    pageFilterKey: PageFilterKey,
): StashFindFilter? =
    server.serverPreferences
        .getDefaultPageFilter(pageFilterKey)
        .findFilter
        ?.withResolvedRandom()
