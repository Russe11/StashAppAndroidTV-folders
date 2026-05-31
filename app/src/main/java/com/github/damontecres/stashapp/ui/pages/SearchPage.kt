package com.github.damontecres.stashapp.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.StashApplication
import com.github.damontecres.stashapp.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.data.SortAndDirection
import com.github.damontecres.stashapp.data.SortOption
import com.github.damontecres.stashapp.data.StashFindFilter
import com.github.damontecres.stashapp.navigation.FilterAndPosition
import com.github.damontecres.stashapp.navigation.NavigationManager
import com.github.damontecres.stashapp.suppliers.FilterArgs
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.cards.StashCard
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.components.LongClicker
import com.github.damontecres.stashapp.ui.compat.isNotTvDevice
import com.github.damontecres.stashapp.ui.components.RowColumn
import com.github.damontecres.stashapp.ui.components.SearchEditTextBox
import com.github.damontecres.stashapp.ui.components.SearchHistory
import com.github.damontecres.stashapp.ui.theme.Spacing
import com.github.damontecres.stashapp.ui.tryRequestFocus
import com.github.damontecres.stashapp.ui.util.OneTimeLaunchedEffect
import com.github.damontecres.stashapp.ui.util.ifElse
import com.github.damontecres.stashapp.util.FrontPageParser
import com.github.damontecres.stashapp.util.LoggingCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private lateinit var server: StashServer
    private var currentQuery = ""

    val scenes = MutableLiveData<List<Any>>(listOf())
    val groups = MutableLiveData<List<Any>>(listOf())
    val markers = MutableLiveData<List<Any>>(listOf())
    val performers = MutableLiveData<List<Any>>(listOf())
    val studios = MutableLiveData<List<Any>>(listOf())
    val tags = MutableLiveData<List<Any>>(listOf())
    val images = MutableLiveData<List<Any>>(listOf())
    val galleries = MutableLiveData<List<Any>>(listOf())

    val mapping =
        mapOf(
            DataType.SCENE to scenes,
            DataType.GROUP to groups,
            DataType.MARKER to markers,
            DataType.PERFORMER to performers,
            DataType.STUDIO to studios,
            DataType.TAG to tags,
            DataType.IMAGE to images,
            DataType.GALLERY to galleries,
        )

    fun init(
        server: StashServer,
        initialQuery: String,
        perPage: Int,
    ) {
        this.server = server
        search(initialQuery, perPage)
    }

    fun search(
        query: String,
        perPage: Int,
    ) {
        if (query.isNotBlank() && query != this.currentQuery) {
            this.currentQuery = query
            val queryEngine = QueryEngine(server)
            DataType.entries.forEach {
                val data = mapping[it]!!
                data.value = listOf()

                val stashFindFilter =
                    StashFindFilter(
                        q = query,
                        sortAndDirection =
                            SortAndDirection(
                                SortOption.sortByName(it),
                                SortDirectionEnum.ASC,
                            ),
                    )
                val findFilter =
                    stashFindFilter.toFindFilterType(
                        perPage = perPage,
                        page = 1,
                    )

                viewModelScope.launch(
                    LoggingCoroutineExceptionHandler(
                        server,
                        viewModelScope,
                        toastMessage = "Search for ${
                            StashApplication.getApplication().getString(it.pluralStringId)
                        } failed",
                    ),
                ) {
                    val results = queryEngine.find(it, findFilter)
                    if (results.isNotEmpty()) {
                        data.value = results
                    }
                }
            }
        } else if (query != this.currentQuery) {
            mapping.values.forEach { it.value = listOf() }
        }
    }
}

@Composable
fun SearchPage(
    server: StashServer,
    navigationManager: NavigationManager,
    uiConfig: ComposeUiConfig,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    viewModel: SearchViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val touch = isNotTvDevice

    var searchQuery by rememberSaveable { mutableStateOf(initialQuery) }
    val perPage = uiConfig.preferences.searchPreferences.maxResults

    // Touch-only: filter the rendered result sections to a single DataType (null = "All").
    var selectedType by rememberSaveable { mutableStateOf<DataType?>(null) }

    // Touch-only: recent submitted queries, loaded from SharedPreferences and kept in sync.
    var recentSearches by remember { mutableStateOf(emptyList<String>()) }
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(touch) {
        if (touch) {
            recentSearches = SearchHistory.get(context)
        }
    }

    val scenes by viewModel.scenes.observeAsState(listOf())
    val groups by viewModel.groups.observeAsState(listOf())
    val markers by viewModel.markers.observeAsState(listOf())
    val performers by viewModel.performers.observeAsState(listOf())
    val studios by viewModel.studios.observeAsState(listOf())
    val tags by viewModel.tags.observeAsState(listOf())
    val images by viewModel.images.observeAsState(listOf())
    val galleries by viewModel.galleries.observeAsState(listOf())

    val itemLists =
        mapOf(
            DataType.SCENE to scenes,
            DataType.GROUP to groups,
            DataType.MARKER to markers,
            DataType.PERFORMER to performers,
            DataType.STUDIO to studios,
            DataType.TAG to tags,
            DataType.IMAGE to images,
            DataType.GALLERY to galleries,
        )

    // Touch-only: drop the type filter if the selected type no longer has results (e.g. the
    // query changed), so the user is never stuck viewing an empty, unselectable section.
    val selectedTypeHasResults = selectedType?.let { itemLists[it]!!.isNotEmpty() } ?: true
    LaunchedEffect(selectedTypeHasResults) {
        if (touch && !selectedTypeHasResults) {
            selectedType = null
        }
    }

    OneTimeLaunchedEffect {
        viewModel.init(server, initialQuery, perPage)
//        focusRequester.tryRequestFocus()
    }

    LaunchedEffect(Unit) {
        if (!touch) {
            focusRequester.tryRequestFocus()
        }
    }

    val listState = rememberLazyListState()
    var focusedIndex by rememberSaveable { mutableStateOf(RowColumn(0, 0)) }
    var focusedRow by rememberSaveable { mutableIntStateOf(-1) }

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .focusGroup()
                .focusRestorer(focusRequester),
        contentPadding = PaddingValues(16.dp),
    ) {
        stickyHeader {
            var job: Job? = null
            val searchDelay = uiConfig.preferences.searchPreferences.searchDelayMs
            if (touch) {
                // Touch/phone: native Material 3 SearchBar with recent-search suggestions.
                SearchPageSearchBar(
                    query = searchQuery,
                    expanded = searchBarExpanded,
                    recentSearches = recentSearches,
                    onExpandedChange = { searchBarExpanded = it },
                    onQueryChange = { newQuery ->
                        searchQuery = newQuery
                        job?.cancel()
                        job =
                            scope.launch(StashCoroutineExceptionHandler()) {
                                delay(searchDelay)
                                viewModel.search(searchQuery, perPage)
                            }
                    },
                    onSearch = { submitted ->
                        job?.cancel()
                        searchQuery = submitted
                        viewModel.search(submitted, perPage)
                        SearchHistory.add(context, submitted)
                        recentSearches = SearchHistory.get(context)
                        searchBarExpanded = false
                    },
                    onClear = {
                        searchQuery = ""
                        job?.cancel()
                        viewModel.search("", perPage)
                    },
                    onRecentClick = { recent ->
                        searchQuery = recent
                        job?.cancel()
                        viewModel.search(recent, perPage)
                        SearchHistory.add(context, recent)
                        recentSearches = SearchHistory.get(context)
                        searchBarExpanded = false
                    },
                    onClearHistory = {
                        SearchHistory.clear(context)
                        recentSearches = emptyList()
                    },
                )
            } else {
                SearchEditTextBox(
                    modifier = Modifier.ifElse(focusedRow < 0, Modifier.focusRequester(focusRequester)),
                    value = searchQuery,
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        job?.cancel()
                        job =
                            scope.launch(StashCoroutineExceptionHandler()) {
                                delay(searchDelay)
                                viewModel.search(searchQuery, perPage)
                            }
                    },
                    onSearchClick = {
                        job?.cancel()
                        viewModel.search(searchQuery, perPage)
                    },
                )
            }
        }

        // Touch-only: per-type filter chips. Only show types that currently have results;
        // hide the row entirely unless at least two types have results.
        if (touch) {
            val typesWithResults = DataType.entries.filter { itemLists[it]!!.isNotEmpty() }
            if (typesWithResults.size > 1) {
                item {
                    SearchTypeFilterChips(
                        typesWithResults = typesWithResults,
                        selectedType = selectedType,
                        onTypeSelected = { selectedType = it },
                    )
                }
            }
        }

        DataType.entries.forEachIndexed { index, dataType ->
            val data = itemLists[dataType]!!
            // Touch-only: when a type is selected, render only that type's section.
            val visible = !touch || selectedType == null || selectedType == dataType
            if (visible && data.isNotEmpty()) {
                item {
                    HomePageRow(
                        uiConfig = uiConfig,
                        row =
                            FrontPageParser.FrontPageRow.Success(
                                name = stringResource(dataType.pluralStringId),
                                filter =
                                    FilterArgs(
                                        dataType = dataType,
                                        findFilter =
                                            StashFindFilter(
                                                q = searchQuery,
                                                sortAndDirection =
                                                    SortAndDirection(
                                                        SortOption.sortByName(dataType),
                                                        SortDirectionEnum.ASC,
                                                    ),
                                            ),
                                    ),
                                data = data,
                            ),
                        itemOnClick = itemOnClick,
                        longClicker = longClicker,
                        onFocus = { idx, item ->
                            focusedIndex = RowColumn(index, idx)
//                            focusedItem = item
                            focusedRow = index
                        },
                        rowFocusRequester = if (index == focusedIndex.row) focusRequester else null,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
fun SearchItemsRow(
    title: String,
    items: List<Any>,
    uiConfig: ComposeUiConfig,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    filterArgs: FilterArgs,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    Column(
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .focusGroup()
                    .focusRestorer(firstFocus),
            state = listState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items) { index, item ->
                StashCard(
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                    uiConfig = uiConfig,
                    item = item,
                    itemOnClick = {
                        itemOnClick.onClick(
                            item,
                            FilterAndPosition(filterArgs, index),
                        )
                    },
                    longClicker = longClicker,
                    getFilterAndPosition = null,
                )
            }
        }
    }
}

/**
 * Touch-only horizontally-scrollable row of [FilterChip]s for narrowing the search results to a
 * single [DataType]. A leading "All" chip (selected when [selectedType] is null) shows every
 * non-empty section. Only [typesWithResults] get a chip; the caller hides the whole row when fewer
 * than two types have results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTypeFilterChips(
    typesWithResults: List<DataType>,
    selectedType: DataType?,
    onTypeSelected: (DataType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = selectedType == null,
            onClick = { onTypeSelected(null) },
            label = {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.stashapp_all),
                )
            },
            leadingIcon =
                if (selectedType == null) {
                    {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
        )
        typesWithResults.forEach { dataType ->
            val selected = selectedType == dataType
            FilterChip(
                selected = selected,
                onClick = { onTypeSelected(dataType) },
                label = {
                    androidx.compose.material3.Text(
                        text = stringResource(dataType.pluralStringId),
                    )
                },
            )
        }
    }
}

/**
 * Touch-only Material 3 [SearchBar] used by [SearchPage]. Wraps the existing debounce/query
 * callbacks (passed in via [onQueryChange]/[onSearch]) and surfaces the recent-search history as
 * tappable suggestions when expanded with a blank query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPageSearchBar(
    query: String,
    expanded: Boolean,
    recentSearches: List<String>,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onRecentClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBar(
        modifier = modifier.fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                placeholder = {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.stashapp_actions_search),
                    )
                },
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.stashapp_actions_search),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = onClear) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.stashapp_actions_clear),
                            )
                        }
                    }
                },
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        if (query.isBlank() && recentSearches.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.md,
                            end = Spacing.sm,
                            top = Spacing.sm,
                            bottom = Spacing.xs,
                        ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.format_recently_used, "").trim(),
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.TextButton(onClick = onClearHistory) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.stashapp_actions_clear),
                    )
                }
            }
            recentSearches.forEach { recent ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onRecentClick(recent) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Text(
                        text = recent,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
