package com.krishna.tiledeck

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.TileCatalog
import com.krishna.tiledeck.data.TilePrefs
import com.krishna.tiledeck.data.TileSpec
import com.krishna.tiledeck.system.MotoTools
import com.krishna.tiledeck.tiles.TileActionEngine
import com.krishna.tiledeck.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DeckRoute {
    data object Home : DeckRoute
    data class Category(val name: String) : DeckRoute
    data class TileDetail(val tileId: String) : DeckRoute
    data object AppsLock : DeckRoute
    data object Permissions : DeckRoute
    data object HubSettings : DeckRoute
}

data class InstalledAppItem(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?
)

private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 72): ImageBitmap? = runCatching {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    bmp.asImageBitmap()
}.getOrNull()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRoute: DeckRoute = when (intent?.data?.host) {
            "applock" -> DeckRoute.AppsLock
            "permissions" -> DeckRoute.Permissions
            "tile" -> {
                val id = intent?.data?.lastPathSegment
                if (id != null && TileCatalog.findById(id) != null) DeckRoute.TileDetail(id) else DeckRoute.Home
            }
            else -> DeckRoute.Home
        }

        setContent {
            var darkTheme by remember { mutableStateOf(TilePrefs.isDarkTheme(this)) }
            val colors = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFF2DD4BF),
                    secondary = Color(0xFF38BDF8),
                    background = Color(0xFF070A10),
                    surface = Color(0xCC111826),
                    surfaceVariant = Color(0xFF1B2436)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF0F766E),
                    secondary = Color(0xFF0284C7),
                    background = Color(0xFFF2F6FB),
                    surface = Color(0xE6FFFFFF),
                    surfaceVariant = Color(0xFFE5EEF8)
                )
            }

            MaterialTheme(colorScheme = colors) {
                TileDeckMultiPageApp(
                    initialRoute = initialRoute,
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        darkTheme = !darkTheme
                        TilePrefs.setDarkTheme(this, darkTheme)
                    }
                )
            }
        }
    }
}

@Composable
private fun TileDeckMultiPageApp(
    initialRoute: DeckRoute,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStack = remember {
        mutableStateListOf<DeckRoute>().apply {
            add(DeckRoute.Home)
            if (initialRoute != DeckRoute.Home) add(initialRoute)
        }
    }
    var navForward by remember { mutableStateOf(true) }
    var hasRoot by remember { mutableStateOf(TilePrefs.isRootGranted(context)) }
    var checkingRoot by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    fun navigate(route: DeckRoute) {
        if (backStack.lastOrNull() == route) return
        navForward = true
        backStack.add(route)
    }

    fun switchTopLevel(route: DeckRoute) {
        if (backStack.lastOrNull() == route) return
        navForward = route != DeckRoute.Home
        backStack.clear()
        backStack.add(DeckRoute.Home)
        if (route != DeckRoute.Home) backStack.add(route)
    }

    fun popBack() {
        if (backStack.size > 1) {
            navForward = false
            backStack.removeAt(backStack.lastIndex)
        }
    }

    BackHandler(enabled = backStack.size > 1) { popBack() }

    fun triggerSuRequest() {
        if (checkingRoot) return
        checkingRoot = true
        scope.launch {
            val granted = withContext(Dispatchers.IO) { MotoTools.requestRootAndSync(context) }
            hasRoot = granted
            checkingRoot = false
            refreshTick++
        }
    }

    LaunchedEffect(Unit) { triggerSuRequest() }

    val currentRoute = backStack.last()
    val navTabs = listOf(
        Triple("Home", Icons.Rounded.Dashboard, DeckRoute.Home),
        Triple("App Lock", Icons.Rounded.Security, DeckRoute.AppsLock),
        Triple("Permissions", Icons.Rounded.Tune, DeckRoute.Permissions),
        Triple("Hub API", Icons.Rounded.Hub, DeckRoute.HubSettings)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LivingDeckBackground(darkTheme = darkTheme)

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    navTabs.forEach { (label, icon, route) ->
                        val selected = when (route) {
                            DeckRoute.Home -> currentRoute is DeckRoute.Home || currentRoute is DeckRoute.Category || currentRoute is DeckRoute.TileDetail
                            else -> currentRoute == route
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { switchTopLevel(route) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                        )
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = currentRoute,
                modifier = Modifier.fillMaxSize().padding(padding),
                transitionSpec = {
                    if (navForward) {
                        (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(160)))
                    }
                },
                label = "page_transition"
            ) { route ->
                when (route) {
                    is DeckRoute.Home -> HomeDashboardPage(
                        hasRoot = hasRoot,
                        checkingRoot = checkingRoot,
                        darkTheme = darkTheme,
                        tick = refreshTick,
                        onToggleTheme = onToggleTheme,
                        onRequestSu = { triggerSuRequest() },
                        onOpenCategory = { navigate(DeckRoute.Category(it)) },
                        onOpenTile = { navigate(DeckRoute.TileDetail(it)) },
                        onQuickRun = {
                            TileActionEngine.execute(context, it)
                            refreshTick++
                        }
                    )
                    is DeckRoute.Category -> CategoryDetailPage(
                        categoryName = route.name,
                        hasRoot = hasRoot,
                        tick = refreshTick,
                        onBack = { popBack() },
                        onOpenTile = { navigate(DeckRoute.TileDetail(it)) },
                        onChanged = { refreshTick++ }
                    )
                    is DeckRoute.TileDetail -> TileInspectorPage(
                        tileId = route.tileId,
                        tick = refreshTick,
                        onBack = { popBack() },
                        onChanged = { refreshTick++ }
                    )
                    is DeckRoute.AppsLock -> AppsAndLockPage(
                        hasRoot = hasRoot,
                        onBack = { popBack() }
                    )
                    is DeckRoute.Permissions -> PermissionsPage(
                        hasRoot = hasRoot,
                        checkingRoot = checkingRoot,
                        onRequestSu = { triggerSuRequest() },
                        onBack = { popBack() }
                    )
                    is DeckRoute.HubSettings -> HubSettingsPage(
                        hasRoot = hasRoot,
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        onBack = { popBack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubPageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HomeDashboardPage(
    hasRoot: Boolean,
    checkingRoot: Boolean,
    darkTheme: Boolean,
    tick: Int,
    onToggleTheme: () -> Unit,
    onRequestSu: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenTile: (String) -> Unit,
    onQuickRun: (String) -> Unit
) {
    val context = LocalContext.current
    val visibleTiles = remember(hasRoot) { TileCatalog.visibleTiles(hasRoot) }
    val enabledTiles = remember(hasRoot, tick) { visibleTiles.filter { TilePrefs.isTileEnabledInPanel(context, it) } }
    val categories = remember(hasRoot) { TileCatalog.visibleCategories(hasRoot).filter { it != "All" } }
    val a11yReady = TileDeckAccessibilityService.instance != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Ambient Video Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0x382DD4BF), RoundedCornerShape(22.dp))
        ) {
            AmbientHeroVideo(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color(0xDC070B12), Color(0x940B1320), Color(0xCC070B12))))
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsingDot(color = if (hasRoot) Color(0xFF2DD4BF) else Color(0xFFF59E0B), active = true)
                        Text(
                            text = if (checkingRoot) "CHECKING SU..." else if (hasRoot) "SU GRANTED | FULL DECK" else "NON-ROOT MODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = if (hasRoot) Color(0xFF2DD4BF) else Color(0xFFFBBF24)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("TileDeck v6.0", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(
                        text = if (hasRoot) "${visibleTiles.size} direct tiles across ${categories.size} pages"
                        else "${visibleTiles.size} tiles (${TileCatalog.hiddenRootCount()} root hidden)",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!hasRoot) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0x332DD4BF),
                                border = BorderStroke(1.dp, Color(0xFF2DD4BF)),
                                modifier = Modifier.clickable(enabled = !checkingRoot) { onRequestSu() }
                            ) {
                                Text(
                                    if (checkingRoot) "Asking..." else "Request SU",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2DD4BF),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0x26FFFFFF),
                            modifier = Modifier.clickable { onToggleTheme() }
                        ) {
                            Text(
                                if (darkTheme) "Light" else "Dark",
                                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedRingGauge(
                        progress = enabledTiles.size.toFloat() / visibleTiles.size.coerceAtLeast(1),
                        label = "Shade", valueText = "${enabledTiles.size}", accent = Color(0xFF2DD4BF)
                    )
                    AnimatedRingGauge(
                        progress = if (a11yReady) 1f else 0.25f,
                        label = "A11y", valueText = if (a11yReady) "ON" else "OFF",
                        accent = if (a11yReady) Color(0xFF38BDF8) else Color(0xFFF59E0B)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Active in Quick Settings (${enabledTiles.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            enabledTiles.forEach { spec ->
                val active = TilePrefs.isTileActive(context, spec.id)
                val sub = TilePrefs.getSubtitle(context, spec.id, "Ready")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    modifier = Modifier.width(138.dp).clickable { onQuickRun(spec.id) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(tileIcon(spec.id, spec.category), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(spec.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Tile Categories", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // 2-Column Category Hub Cards (each opens its own dedicated page)
        categories.chunked(2).forEach { rowCats ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowCats.forEach { cat ->
                    val catTiles = visibleTiles.filter { it.category == cat }
                    val catEnabled = catTiles.count { TilePrefs.isTileEnabledInPanel(context, it) }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                        modifier = Modifier.weight(1f).clickable { onOpenCategory(cat) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                ) {
                                    Icon(categoryIcon(cat), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(cat, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${catTiles.size} tiles | $catEnabled on", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                    }
                }
                if (rowCats.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryDetailPage(
    categoryName: String,
    hasRoot: Boolean,
    tick: Int,
    onBack: () -> Unit,
    onOpenTile: (String) -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val catTiles = remember(categoryName, hasRoot, search, tick) {
        TileCatalog.visibleTiles(hasRoot).filter {
            it.category == categoryName &&
                (search.isBlank() || it.name.contains(search, true) || it.description.contains(search, true))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SubPageHeader(title = categoryName, subtitle = "${catTiles.size} direct-action tiles", onBack = onBack)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search in $categoryName...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(catTiles, key = { it.id }) { spec ->
                AnimatedTileRowCard(
                    spec = spec,
                    enabled = TilePrefs.isTileEnabledInPanel(context, spec),
                    active = TilePrefs.isTileActive(context, spec.id),
                    subtitle = TilePrefs.getSubtitle(context, spec.id, "Off"),
                    onOpenDetail = { onOpenTile(spec.id) },
                    onToggle = { TilePrefs.setTileEnabledInPanel(context, spec, it); onChanged() },
                    onRun = { TileActionEngine.execute(context, spec.id); onChanged() }
                )
            }
        }
    }
}

@Composable
private fun AnimatedTileRowCard(
    spec: TileSpec,
    enabled: Boolean,
    active: Boolean,
    subtitle: String,
    onOpenDetail: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "row_scale"
    )
    LaunchedEffect(pressed) { if (pressed) { delay(120); pressed = false } }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
        modifier = Modifier.fillMaxWidth().scale(scale).clickable { onOpenDetail() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(tileIcon(spec.id, spec.category), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(spec.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(spec.permissionTier, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Text(spec.description, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f))
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    PulsingDot(color = if (active) MaterialTheme.colorScheme.primary else Color.Gray, active = active)
                    Text(subtitle, fontSize = 11.sp)
                }
                FilledTonalButton(onClick = { pressed = true; onRun() }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TileInspectorPage(tileId: String, tick: Int, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val spec = TileCatalog.findById(tileId) ?: return
    val enabled = remember(tick) { TilePrefs.isTileEnabledInPanel(context, spec) }
    val active = remember(tick) { TilePrefs.isTileActive(context, spec.id) }
    val subtitle = remember(tick) { TilePrefs.getSubtitle(context, spec.id, "Off") }
    val broadcastCmd = "am broadcast -a com.krishna.tiledeck.ACTION_TRIGGER_TILE --es tile_id ${spec.id}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SubPageHeader(title = spec.name, subtitle = "${spec.category} | ${spec.permissionTier}", onBack = onBack)

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                ) {
                    Icon(tileIcon(spec.id, spec.category), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(spec.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(spec.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { TileActionEngine.execute(context, spec.id); onChanged() }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Run Tile Now")
                    }
                    OutlinedButton(onClick = { TilePrefs.setTileEnabledInPanel(context, spec, !enabled); onChanged() }) {
                        Text(if (enabled) "Remove from Shade" else "Add to Shade")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Live State: $subtitle", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App Hub Integration", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Deep Link: tiledeck://tile/${spec.id}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Text(broadcastCmd, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("TileDeck Hub Cmd", broadcastCmd))
                    Toast.makeText(context, "Broadcast command copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy Trigger Command") }
            }
        }
    }
}

@Composable
private fun AppsAndLockPage(hasRoot: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var appSearch by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf(TilePrefs.getAppLockPin(context)) }
    var armed by remember { mutableStateOf(TilePrefs.isAppLockArmed(context)) }
    var tick by remember { mutableIntStateOf(0) }

    val installedApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { InstalledAppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, drawableToImageBitmap(it.loadIcon(pm))) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }
    val filteredApps = remember(appSearch, installedApps) {
        installedApps.filter { appSearch.isBlank() || it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SubPageHeader(
            title = if (hasRoot) "Apps, Lock & Hidden Tray" else "Apps & PIN Lock",
            subtitle = if (hasRoot) "Accessibility PIN Lock + Root Hide Tray" else "Accessibility PIN Lock",
            onBack = onBack
        )
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Arm App Lock Shield", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Switch(checked = armed, onCheckedChange = { armed = it; TilePrefs.setAppLockArmed(context, it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("PIN") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                    Button(onClick = { TilePrefs.setAppLockPin(context, pinInput) }) { Text("Save") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = appSearch, onValueChange = { appSearch = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search installed apps...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isLocked = remember(tick) { app.packageName in TilePrefs.getLockedPackages(context) }
                val isHidden = remember(tick) { app.packageName in TilePrefs.getHiddenTrayPackages(context) }
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (app.icon != null) {
                                Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Icon(Icons.Rounded.Android, contentDescription = null)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = isLocked, onClick = { TilePrefs.setPackageLocked(context, app.packageName, !isLocked); tick++ }, label = { Text("Lock", fontSize = 11.sp) })
                            if (hasRoot) {
                                FilterChip(selected = isHidden, onClick = { TilePrefs.setPackageInHiddenTray(context, app.packageName, !isHidden); tick++ }, label = { Text("Hide", fontSize = 11.sp) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsPage(hasRoot: Boolean, checkingRoot: Boolean, onRequestSu: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val a11yGranted = remember(tick) { TileDeckAccessibilityService.instance != null }
    val overlayGranted = remember(tick) { Settings.canDrawOverlays(context) }
    val writeGranted = remember(tick) { Settings.System.canWrite(context) }
    val dndGranted = remember(tick) { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted }
    val usageGranted = remember(tick) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }

    val items = listOf(
        Triple("Root (SU / KernelSU)", "Unlocks ${TileCatalog.hiddenRootCount()} root tiles (auto-hidden if denied)", hasRoot to null),
        Triple("Accessibility Service", "App Lock, Instant Lock, Screenshot, Split, Clear Recents", a11yGranted to Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        Triple("Display Over Other Apps", "Blackout Pocket, Extra Dim, Amber Filter, Overlays", overlayGranted to Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))),
        Triple("Modify System Settings", "Direct Brightness, Timeout, Rotation, Haptics", writeGranted to Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))),
        Triple("Do Not Disturb Policy", "Direct Sound Profile, Mute Except Alarms, Study Mode", dndGranted to Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)),
        Triple("Usage Access", "App Lock & Foreground App Detection", usageGranted to Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    )
    val readyCount = items.count { it.third.first }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SubPageHeader(title = "Permission Center", subtitle = "$readyCount / ${items.size} permissions active", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { (title, desc, stateAndIntent) ->
                val granted = stateAndIntent.first
                val intent = stateAndIntent.second
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.width(8.dp))
                        if (granted) {
                            Text("READY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else if (intent != null) {
                            Button(onClick = { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); runCatching { context.startActivity(intent) } }) { Text("Grant") }
                        } else {
                            Button(onClick = onRequestSu, enabled = !checkingRoot) { Text(if (checkingRoot) "Asking..." else "Ask SU") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubSettingsPage(hasRoot: Boolean, darkTheme: Boolean, onToggleTheme: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val notes = remember { TilePrefs.getQuickNotes(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubPageHeader(title = "App Hub & Settings", subtitle = "External Hub API, Theme & Quick Notes", onBack = onBack)

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Dark Mode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Switch between Obsidian Dark and Crisp Light", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Switch(checked = darkTheme, onCheckedChange = { onToggleTheme() })
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("App Hub Endpoints", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Provider: content://com.krishna.tiledeck.hub/tiles", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Text("Trigger: com.krishna.tiledeck.ACTION_TRIGGER_TILE", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Text("Deep Links: tiledeck://hub | tiledeck://applock | tiledeck://permissions", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                Text(
                    if (hasRoot) "Exposing all 65 direct tiles to external App Hubs."
                    else "Exposing 47 non-root tiles (18 root tiles hidden until SU granted).",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Saved Quick Notes (${notes.size})", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (notes.isEmpty()) {
                    Text("Tap the Quick Note tile in your notification shade to capture notes here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                } else {
                    notes.take(10).forEach { n ->
                        Text("- $n", fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
