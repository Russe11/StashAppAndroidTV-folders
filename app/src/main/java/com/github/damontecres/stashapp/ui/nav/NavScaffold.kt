package com.github.damontecres.stashapp.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.navigation.NavigationManagerCompose
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.FontAwesome
import com.github.damontecres.stashapp.ui.components.ItemOnClicker
import com.github.damontecres.stashapp.ui.components.LongClicker
import com.github.damontecres.stashapp.ui.components.states.LocalSnackbarHostState
import com.github.damontecres.stashapp.ui.theme.Spacing
import com.github.damontecres.stashapp.ui.theme.TouchTarget
import com.github.damontecres.stashapp.ui.util.ScreenSize
import com.github.damontecres.stashapp.ui.util.screenSize
import com.github.damontecres.stashapp.util.StashServer

/**
 * Touch/phone navigation shell (used only when !isTvDevice).
 *
 * Provides a native, adaptive Material3 navigation surface that scales across window sizes:
 * - COMPACT: bottom [NavigationBar] of primary destinations + a "More" sheet for the rest.
 * - MEDIUM: a left [NavigationRail] of primary destinations + a "More" sheet for the rest.
 * - EXPANDED: a [PermanentNavigationDrawer] listing every page.
 *
 * A top app bar (center-aligned for top-level pages, left-aligned otherwise) and a
 * [SnackbarHost] are present in every configuration.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NavScaffold(
    server: StashServer,
    navigationManager: NavigationManagerCompose,
    composeUiConfig: ComposeUiConfig,
    destination: Destination,
    selectedScreen: DrawerPage?,
    pages: List<DrawerPage>,
    itemOnClick: ItemOnClicker<Any>,
    longClicker: LongClicker<Any>,
    onSelectScreen: (DrawerPage) -> Unit,
    onChangeTheme: (String?) -> Unit,
    onSwitchServer: (StashServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf<AnnotatedString?>(null) }

    val size = screenSize()

    // Primary destinations: Home, Scene list, Performer list, Search (first match of each),
    // padded with the remaining pages and capped at 4. If nothing matches, fall back to the
    // first 4 pages.
    val primaryPages = remember(pages) { primaryDestinations(pages) }
    val secondaryPages = remember(pages, primaryPages) { pages - primaryPages.toSet() }

    val titleStyle =
        when (size) {
            ScreenSize.COMPACT -> androidx.compose.material3.MaterialTheme.typography.headlineSmall
            ScreenSize.MEDIUM -> androidx.compose.material3.MaterialTheme.typography.headlineMedium
            ScreenSize.EXPANDED -> androidx.compose.material3.MaterialTheme.typography.headlineLarge
        }

    var showMoreSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val snackbarHostState = remember { SnackbarHostState() }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val isTopLevel = selectedScreen is DrawerPage.HomePage || selectedScreen is DrawerPage.SearchPage

    val topBar: @Composable () -> Unit = {
        val resolvedTitle =
            when (selectedScreen) {
                is DrawerPage.HomePage,
                is DrawerPage.SearchPage,
                -> AnnotatedString(stringResource(selectedScreen.name))

                else -> title
            }
        val titleContent: @Composable () -> Unit = {
            resolvedTitle?.let {
                Text(
                    text = it,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val navigationIcon: @Composable () -> Unit = {
            IconButton(onClick = {
                if (selectedScreen != DrawerPage.HomePage) {
                    navigationManager.goBack()
                } else {
                    navigationManager.goToMain()
                }
            }) {
                Icon(
                    imageVector =
                        if (selectedScreen != DrawerPage.HomePage) {
                            Icons.AutoMirrored.Filled.ArrowBack
                        } else {
                            Icons.Default.Home
                        },
                    contentDescription = null,
                )
            }
        }
        val actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            IconButton(onClick = {
                navigationManager.navigate(Destination.ManageServers(false))
            }) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = stringResource(R.string.stashapp_settings),
                )
            }
        }
        if (isTopLevel) {
            CenterAlignedTopAppBar(
                title = titleContent,
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        } else {
            TopAppBar(
                title = titleContent,
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        }
    }

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(
            modifier =
                contentModifier
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
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
                    onUpdateTitle = { title = it },
                )
            }
        }
    }

    when (size) {
        ScreenSize.EXPANDED -> {
            PermanentNavigationDrawer(
                modifier = modifier,
                drawerContent = {
                    PermanentDrawerSheet {
                        Column(
                            modifier =
                                Modifier
                                    .width(280.dp)
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            pages.forEach { page ->
                                NavRow(
                                    page = page,
                                    selected = selectedScreen == page,
                                    onClick = { onSelectScreen(page) },
                                )
                            }
                        }
                    }
                },
            ) {
                Scaffold(
                    topBar = topBar,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                ) { innerPadding ->
                    content(Modifier.padding(innerPadding))
                }
            }
        }

        ScreenSize.MEDIUM -> {
            Row(modifier = modifier.fillMaxSize()) {
                NavigationRail {
                    primaryPages.forEach { page ->
                        NavigationRailItem(
                            selected = selectedScreen == page,
                            onClick = { onSelectScreen(page) },
                            icon = { PageIcon(page, selectedScreen == page) },
                            label = { Text(stringResource(page.name)) },
                        )
                    }
                    if (secondaryPages.isNotEmpty()) {
                        NavigationRailItem(
                            selected = false,
                            onClick = { showMoreSheet = true },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(R.string.more)) },
                        )
                    }
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = topBar,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                ) { innerPadding ->
                    content(Modifier.padding(innerPadding))
                }
            }
        }

        ScreenSize.COMPACT -> {
            Scaffold(
                modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = topBar,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar {
                        primaryPages.forEach { page ->
                            NavigationBarItem(
                                selected = selectedScreen == page,
                                onClick = { onSelectScreen(page) },
                                icon = { PageIcon(page, selectedScreen == page) },
                                label = {
                                    Text(
                                        text = stringResource(page.name),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                        if (secondaryPages.isNotEmpty()) {
                            NavigationBarItem(
                                selected = false,
                                onClick = { showMoreSheet = true },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(R.string.more)) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                content(Modifier.padding(innerPadding))
            }
        }
    }

    if (showMoreSheet && secondaryPages.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.md),
            ) {
                secondaryPages.forEach { page ->
                    NavRow(
                        page = page,
                        selected = selectedScreen == page,
                        onClick = {
                            showMoreSheet = false
                            onSelectScreen(page)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Picks the primary navigation destinations from [pages]: the first matching of Home, the SCENE
 * data type, the PERFORMER data type, and Search. Pads the result with the remaining pages and
 * caps it at 4. Falls back to the first 4 pages when nothing matches.
 */
internal fun primaryDestinations(pages: List<DrawerPage>): List<DrawerPage> {
    val preferred =
        listOfNotNull(
            pages.firstOrNull { it is DrawerPage.HomePage },
            pages.firstOrNull { it is DrawerPage.DataTypePage && it.dataType == DataType.SCENE },
            pages.firstOrNull { it is DrawerPage.DataTypePage && it.dataType == DataType.PERFORMER },
            pages.firstOrNull { it is DrawerPage.SearchPage },
        ).distinct()
    val padded = (preferred + pages.filterNot { it in preferred }).take(4)
    return padded.ifEmpty { pages.take(4) }
}

/** Renders a page's icon: its FontAwesome glyph, falling back to the settings vector. */
@Composable
private fun PageIcon(
    page: DrawerPage,
    selected: Boolean,
) {
    if (page is DrawerPage.SettingPage) {
        Icon(
            painter = painterResource(id = R.drawable.vector_settings),
            contentDescription = null,
        )
    } else {
        val color =
            if (selected) {
                androidx.compose.material3.MaterialTheme.colorScheme.primary
            } else {
                Color.Unspecified
            }
        Text(
            text = stringResource(page.iconString),
            fontFamily = FontAwesome,
            fontSize = 20.sp,
            color = color,
        )
    }
}

/**
 * A single tappable navigation row (icon + name), styled like [NavDropdownMenu]'s rows. Used in
 * the EXPANDED drawer and the "More" bottom sheet.
 */
@Composable
private fun NavRow(
    page: DrawerPage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (page is DrawerPage.SettingPage) {
                Icon(
                    painter = painterResource(id = R.drawable.vector_settings),
                    contentDescription = null,
                )
            } else {
                val color =
                    if (selected) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        Color.Unspecified
                    }
                Text(
                    text = stringResource(page.iconString),
                    fontFamily = FontAwesome,
                    fontSize = 24.sp,
                    color = color,
                )
            }
        }
        Text(
            text = stringResource(page.name),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color =
                if (selected) {
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                } else {
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
fun NavDropdownMenu(
    selectedScreen: DrawerPage?,
    expanded: Boolean,
    pages: List<DrawerPage>,
    onDismissRequest: () -> Unit,
    onClick: (DrawerPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        pages.forEach { page ->
            DropdownMenuItem(
                text = { Text(stringResource(page.name)) },
                leadingIcon = {
                    if (page !is DrawerPage.SettingPage) {
                        val color =
                            if (selectedScreen == page) {
                                MaterialTheme.colorScheme.border
                            } else {
                                Color.Unspecified
                            }
                        Text(
                            text = stringResource(page.iconString),
                            fontFamily = FontAwesome,
                            fontSize = 24.sp,
                            color = color,
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.vector_settings),
                            contentDescription = null,
                        )
                    }
                },
                onClick = { onClick.invoke(page) },
            )
        }
    }
}
