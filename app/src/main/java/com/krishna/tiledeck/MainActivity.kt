package com.krishna.tiledeck

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.AppLockFeatures
import com.krishna.tiledeck.data.TileCatalog
import com.krishna.tiledeck.ui.AppLockActivity
import com.krishna.tiledeck.data.TilePrefs
import com.krishna.tiledeck.data.TileSpec
import com.krishna.tiledeck.tiles.TileActionEngine
import com.krishna.tiledeck.tiles.TileExecutionResult
import kotlinx.coroutines.launch

sealed interface PageRoute {
    data object Deck : PageRoute
    data class Category(val name: String) : PageRoute
    data class TileDetail(val id: String) : PageRoute
    data object AppLock : PageRoute
    data object SelfTester : PageRoute
    data object Permissions : PageRoute
}

data class InstalledAppRow(val label: String, val pkg: String, val icon: ImageBitmap?)
data class TestRecord(val spec: TileSpec, val result: TileExecutionResult)

private fun toImgBitmap(d: Drawable, px: Int = 72): ImageBitmap? = runCatching {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    d.setBounds(0, 0, px, px)
    d.draw(c)
    b.asImageBitmap()
}.getOrNull()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qsComponent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
        }
        val qsSpec = qsComponent?.className?.let { TileCatalog.findByServiceClass(it) }
        val startRoute: PageRoute = when {
            qsSpec != null -> PageRoute.TileDetail(qsSpec.id)
            intent?.data?.host == "applock" -> PageRoute.AppLock
            intent?.data?.host == "permissions" -> PageRoute.Permissions
            intent?.data?.host == "tester" -> PageRoute.SelfTester
            else -> PageRoute.Deck
        }

        setContent {
            var dark by remember { mutableStateOf(TilePrefs.isDarkTheme(this)) }
            val scheme = if (dark) {
                darkColorScheme(
                    primary = Color(0xFF10B981),
                    onPrimary = Color(0xFF041A12),
                    background = Color(0xFF09090B),
                    surface = Color(0xFF111114),
                    surfaceVariant = Color(0xFF18181B),
                    onBackground = Color(0xFFFAFAFA),
                    onSurface = Color(0xFFFAFAFA),
                    outline = Color(0xFF27272A)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF059669),
                    onPrimary = Color.White,
                    background = Color(0xFFFAFAFA),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF4F4F5),
                    onBackground = Color(0xFF09090B),
                    onSurface = Color(0xFF09090B),
                    outline = Color(0xFFE4E4E7)
                )
            }

            MaterialTheme(colorScheme = scheme) {
                TileDeckV8Shell(
                    startRoute = startRoute,
                    dark = dark,
                    onToggleTheme = {
                        dark = !dark
                        TilePrefs.setDarkTheme(this, dark)
                    }
                )
            }
        }
    }
}

@Composable
private fun TileDeckV8Shell(startRoute: PageRoute, dark: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val stack = remember {
        mutableStateListOf<PageRoute>().apply {
            add(PageRoute.Deck)
            if (startRoute != PageRoute.Deck) add(startRoute)
        }
    }
    var forward by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }

    fun push(r: PageRoute) { if (stack.lastOrNull() != r) { forward = true; stack.add(r) } }
    fun pop() { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex) } }
    fun tab(r: PageRoute) {
        if (stack.lastOrNull() == r) return
        forward = r != PageRoute.Deck
        stack.clear(); stack.add(PageRoute.Deck)
        if (r != PageRoute.Deck) stack.add(r)
    }

    BackHandler(enabled = stack.size > 1) { pop() }
    val cur = stack.last()

    val nav = listOf(
        Triple("Deck", Icons.Rounded.GridView, PageRoute.Deck),
        Triple("Lock", Icons.Rounded.Lock, PageRoute.AppLock),
        Triple("Tester", Icons.Rounded.BugReport, PageRoute.SelfTester),
        Triple("Perms", Icons.Rounded.Tune, PageRoute.Permissions)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                    nav.forEach { (label, icon, r) ->
                        val sel = (r == PageRoute.Deck && (cur is PageRoute.Deck || cur is PageRoute.Category || cur is PageRoute.TileDetail)) || cur == r
                        NavigationBarItem(
                            selected = sel,
                            onClick = { tab(r) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        AnimatedContent(
            targetState = cur,
            modifier = Modifier.fillMaxSize().padding(pad),
            transitionSpec = {
                if (forward) {
                    (slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(180)) { -it / 6 } + fadeOut(tween(130)))
                } else {
                    (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(180)) { it / 6 } + fadeOut(tween(130)))
                }
            },
            label = "v8_nav"
        ) { route ->
            when (route) {
                is PageRoute.Deck -> DeckHomePage(
                    dark = dark, tick = tick, onToggleTheme = onToggleTheme,
                    onOpenCat = { push(PageRoute.Category(it)) },
                    onOpenTester = { tab(PageRoute.SelfTester) },
                    onQuickRun = { TileActionEngine.execute(context, it); tick++ },
                    onRemoveFromShade = { spec ->
                        TilePrefs.setTileEnabledInPanel(context, spec, false)
                        Toast.makeText(context, "Removed ${spec.name} from shade", Toast.LENGTH_SHORT).show()
                        tick++
                    }
                )
                is PageRoute.Category -> CategoryPage(
                    catName = route.name, tick = tick, onBack = { pop() },
                    onOpenTile = { push(PageRoute.TileDetail(it)) },
                    onChanged = { tick++ }
                )
                is PageRoute.TileDetail -> TileDetailPage(
                    tileId = route.id, tick = tick, onBack = { pop() }, onChanged = { tick++ }
                )
                is PageRoute.AppLock -> AppLockPage(onBack = { pop() })
                is PageRoute.SelfTester -> SelfTesterPage(onBack = { pop() })
                is PageRoute.Permissions -> PermissionsPage(onBack = { pop() })
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, meta: String, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (onBack != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(38.dp).clickable { onBack() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                Text(meta, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun DeckHomePage(
    dark: Boolean,
    tick: Int,
    onToggleTheme: () -> Unit,
    onOpenCat: (String) -> Unit,
    onOpenTester: () -> Unit,
    onQuickRun: (String) -> Unit,
    onRemoveFromShade: (TileSpec) -> Unit
) {
    val context = LocalContext.current
    val enabledTiles = remember(tick) { TileCatalog.allTiles.filter { TilePrefs.isTileEnabledInPanel(context, it) } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        PageHeader(
            title = "TILEDECK",
            meta = "v11.0 | 102 DIRECT NON-ROOT TILES",
            trailing = {
                OutlinedButton(onClick = onToggleTheme, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(if (dark) "LIGHT" else "DARK", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        )

        // 3 Crisp Metric Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricBox("IN SHADE", "${enabledTiles.size}", Modifier.weight(1f))
            MetricBox("CATALOG", "${TileCatalog.allTiles.size}", Modifier.weight(1f))
            MetricBox("ROOT", "NONE", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        // Quick Self-Tester Callout
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().clickable { onOpenTester() }
        ) {
            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Self-Tester & Copy Broken Report", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Run diagnostics on all 102 tiles and copy any failures in 1 tap.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("ACTIVE IN SHADE (HOLD 3S TO REMOVE)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            enabledTiles.forEach { spec ->
                val active = TilePrefs.isTileActive(context, spec.id)
                val sub = TilePrefs.getSubtitle(context, spec.id, "Ready")
                Hold3sShadeTile(
                    spec = spec,
                    active = active,
                    sub = sub,
                    onTap = { onQuickRun(spec.id) },
                    onHold3sRemove = { onRemoveFromShade(spec) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("CATEGORIES", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(8.dp))

        TileCatalog.categories.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { cat ->
                    val list = TileCatalog.allTiles.filter { it.category == cat }
                    val onCount = list.count { TilePrefs.isTileEnabledInPanel(context, it) }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f).clickable { onOpenCat(cat) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = painterResource(list.first().iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("${list.size}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(cat, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$onCount active in panel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CategoryPage(catName: String, tick: Int, onBack: () -> Unit, onOpenTile: (String) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var q by remember { mutableStateOf("") }
    val tiles = remember(catName, q, tick) {
        TileCatalog.allTiles.filter { it.category == catName && (q.isBlank() || it.name.contains(q, true) || it.description.contains(q, true)) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(title = catName, meta = "${tiles.size} TILES | DEDICATED SVG ICONS", onBack = onBack)
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            placeholder = { Text("Filter $catName...") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(tiles, key = { it.id }) { spec ->
                val enabled = TilePrefs.isTileEnabledInPanel(context, spec)
                val sub = TilePrefs.getSubtitle(context, spec.id, "Off")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenTile(spec.id) }
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(painter = painterResource(spec.iconRes), contentDescription = spec.name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(spec.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${spec.permissionTier} | $sub", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                Text(spec.description, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), maxLines = 2)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = enabled, onCheckedChange = { TilePrefs.setTileEnabledInPanel(context, spec, it); onChanged() })
                    }
                }
            }
        }
    }
}

@Composable
private fun TileDetailPage(tileId: String, tick: Int, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val spec = TileCatalog.findById(tileId) ?: return
    val enabled = remember(tick) { TilePrefs.isTileEnabledInPanel(context, spec) }
    val sub = remember(tick) { TilePrefs.getSubtitle(context, spec.id, "Off") }
    var lastTest by remember { mutableStateOf<TileExecutionResult?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(title = spec.name, meta = "${spec.category} | ${spec.permissionTier}", onBack = onBack)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Icon(painter = painterResource(spec.iconRes), contentDescription = spec.name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(spec.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { lastTest = TileActionEngine.execute(context, spec.id, dryRunTest = false); onChanged() }) { Text("Run Now") }
                    if (!enabled) {
                        OutlinedButton(onClick = { TilePrefs.setTileEnabledInPanel(context, spec, true); onChanged() }) { Text("Add to Shade") }
                    }
                }
                if (enabled) {
                    Spacer(Modifier.height(12.dp))
                    Hold3sRemoveButton(
                        label = "Hold 3s to Remove from Shade",
                        onRemoved = {
                            TilePrefs.setTileEnabledInPanel(context, spec, false)
                            Toast.makeText(context, "Removed ${spec.name} from shade", Toast.LENGTH_SHORT).show()
                            onChanged()
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("State: $sub", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                lastTest?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Diagnostic: ${if (it.ok) "PASS" else "FAIL"} - ${it.diagnostic}", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = if (it.ok) MaterialTheme.colorScheme.primary else Color(0xFFEF4444))
                }
            }
        }
    }
}

@Composable
private fun SelfTesterPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<TestRecord>>(emptyList()) }
    var failedOnly by remember { mutableStateOf(false) }

    fun runAll() {
        results = TileCatalog.allTiles.map { spec ->
            TestRecord(spec, TileActionEngine.execute(context, spec.id, dryRunTest = true))
        }
    }

    LaunchedEffect(Unit) { if (results.isEmpty()) runAll() }

    val failed = results.filter { !it.result.ok }
    val passed = results.size - failed.size
    val shown = if (failedOnly) failed else results

    fun copyFailedReport() {
        val text = if (failed.isEmpty()) {
            "TileDeck v11.0 Self-Test: ALL ${results.size} tiles PASSED (0 failures)."
        } else {
            buildString {
                appendLine("TileDeck v11.0 Failed Tiles (${failed.size}/${results.size}):")
                failed.forEach { rec ->
                    appendLine("- ${rec.spec.id} (${rec.spec.name} | ${rec.spec.permissionTier}): ${rec.result.diagnostic}")
                }
            }.trim()
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TileDeck Broken Tiles", text))
        Toast.makeText(context, if (failed.isEmpty()) "All passed report copied" else "Copied ${failed.size} non-working tiles", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(title = "Self-Tester", meta = "PASS: $passed | FAIL: ${failed.size} / ${results.size}", onBack = onBack)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { runAll() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                Text("Test All 102", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { copyFailedReport() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                Text("Copy Broken (${failed.size})", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !failedOnly, onClick = { failedOnly = false }, label = { Text("All (${results.size})") })
            FilterChip(selected = failedOnly, onClick = { failedOnly = true }, label = { Text("Not Working (${failed.size})") })
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.spec.id }) { rec ->
                val ok = rec.result.ok
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (ok) MaterialTheme.colorScheme.outline else Color(0xFFEF4444).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${rec.spec.name} (${rec.spec.id})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(rec.result.diagnostic, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (ok) MaterialTheme.colorScheme.primary else Color(0xFFEF4444))
                        }
                        if (!ok) {
                            TextButton(onClick = {
                                val line = "${rec.spec.id} (${rec.spec.name}): ${rec.result.diagnostic}"
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Tile Error", line))
                                Toast.makeText(context, "Copied failure", Toast.LENGTH_SHORT).show()
                            }) { Text("Copy", fontSize = 11.sp) }
                        } else {
                            Text("PASS", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0=Apps, 1=100 Features, 2=Intruder Log
    var q by remember { mutableStateOf("") }
    var featCat by remember { mutableStateOf("All") }
    var pin by remember { mutableStateOf(TilePrefs.getAppLockPin(context)) }
    var armed by remember { mutableStateOf(TilePrefs.isAppLockArmed(context)) }
    var tick by remember { mutableIntStateOf(0) }

    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledAppRow(it.loadLabel(pm).toString(), it.activityInfo.packageName, toImgBitmap(it.loadIcon(pm))) }
            .distinctBy { it.pkg }
            .filter { it.pkg != context.packageName }
            .sortedBy { it.label.lowercase() }
    }
    val filteredApps = remember(q, apps) { apps.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) } }
    val enabledFeatCount = remember(tick) { AppLockFeatures.enabledCount(context) }
    val logs = remember(tick) { AppLockFeatures.getIntruderLogs(context) }

    fun launchPreview(cover: String? = null) {
        val i = Intent(context, AppLockActivity::class.java).apply {
            putExtra("target_pkg", context.packageName)
            putExtra("preview_mode", true)
            if (cover != null) putExtra("force_cover", cover)
        }
        context.startActivity(i)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(
            title = "App Lock Vault",
            meta = "$enabledFeatCount / 100 POWER FEATURES | ${AppLockFeatures.activeThemeName(context)}",
            onBack = onBack
        )

        // Sub-navigation pills
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("Apps", fontSize = 11.5.sp) })
            FilterChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("100 Features", fontSize = 11.5.sp) })
            FilterChip(selected = subTab == 2, onClick = { subTab = 2 }, label = { Text("Intruders (${logs.size})", fontSize = 11.5.sp) })
        }
        Spacer(Modifier.height(8.dp))

        when (subTab) {
            0 -> {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Arm App Lock Shield", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Switch(checked = armed, onCheckedChange = { armed = it; TilePrefs.setAppLockArmed(context, it) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, label = { Text("PIN") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                            Button(onClick = { TilePrefs.setAppLockPin(context, pin); Toast.makeText(context, "PIN saved", Toast.LENGTH_SHORT).show() }) { Text("Save") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            OutlinedButton(onClick = { launchPreview(null) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Test Lock UI", fontSize = 11.sp) }
                            OutlinedButton(onClick = { launchPreview("crash") }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Test Crash Decoy", fontSize = 11.sp) }
                            OutlinedButton(onClick = { launchPreview("calc") }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Test Calc Disguise", fontSize = 11.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = q, onValueChange = { q = it }, placeholder = { Text("Search apps...") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    items(filteredApps, key = { it.pkg }) { app ->
                        val locked = remember(tick) { app.pkg in TilePrefs.getLockedPackages(context) }
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    if (app.icon != null) Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                                    else Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) { Icon(Icons.Rounded.Android, contentDescription = null) }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(app.label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(app.pkg, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Switch(checked = locked, onCheckedChange = { TilePrefs.setPackageLocked(context, app.pkg, it); tick++ })
                            }
                        }
                    }
                }
            }
            1 -> {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("All") + AppLockFeatures.categories).forEach { cat ->
                        FilterChip(selected = featCat == cat, onClick = { featCat = cat }, label = { Text(cat, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                val shownFeats = remember(featCat, tick) {
                    AppLockFeatures.all.filter { featCat == "All" || it.category == featCat }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    items(shownFeats, key = { it.key }) { feat ->
                        val on = remember(tick) { AppLockFeatures.isEnabled(context, feat.key) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("#${feat.number}  ${feat.title}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(feat.category, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                    Text(feat.description, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                                }
                                Spacer(Modifier.width(8.dp))
                                Switch(checked = on, onCheckedChange = { AppLockFeatures.setEnabled(context, feat.key, it); tick++ })
                            }
                        }
                    }
                }
            }
            else -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val txt = if (logs.isEmpty()) "No intruder attempts logged." else logs.joinToString("\n")
                        cm.setPrimaryClip(ClipData.newPlainText("Intruder Log", txt))
                        Toast.makeText(context, "Intruder log copied", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f)) { Text("Copy Log", fontSize = 12.sp) }
                    OutlinedButton(onClick = { AppLockFeatures.clearIntruderLogs(context); tick++ }, modifier = Modifier.weight(1f)) { Text("Clear Log", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(10.dp))
                if (logs.isEmpty()) {
                    Text("No failed unlock attempts recorded yet.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                        items(logs) { line ->
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                                Text(line, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val a11y = remember(tick) { TileDeckAccessibilityService.instance != null }
    val write = remember(tick) { Settings.System.canWrite(context) }
    val dnd = remember(tick) { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted }
    val usage = remember(tick) {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val m = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        m == AppOpsManager.MODE_ALLOWED
    }

    val list = listOf(
        Triple("Accessibility Service", "App Lock, Instant Lock, Screenshot, Split, Clear Recents", a11y to Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        Triple("Modify System Settings", "Direct Brightness, Timeout, Rotation, Haptics", write to Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))),
        Triple("Do Not Disturb Policy", "Sound Profile, Study Mode, Meeting, Exam, Total Silence", dnd to Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)),
        Triple("Usage Access", "Foreground App Detection for App Lock", usage to Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(title = "Permissions", meta = "${list.count { it.third.first }} / ${list.size} GRANTED (ZERO ROOT, ZERO OVERLAY)", onBack = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { (t, d, pair) ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, if (pair.first) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(d, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                        Spacer(Modifier.width(8.dp))
                        if (pair.first) Text("READY", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        else Button(onClick = { pair.second.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); runCatching { context.startActivity(pair.second) }; tick++ }) { Text("Grant") }
                    }
                }
            }
        }
    }
}


@Composable
private fun Hold3sShadeTile(
    spec: TileSpec,
    active: Boolean,
    sub: String,
    onTap: () -> Unit,
    onHold3sRemove: () -> Unit
) {
    val progress = remember(spec.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (progress.value > 0.02f) Color(0xFFEF4444)
            else if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .width(140.dp)
            .pointerInput(spec.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onPress = {
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(durationMillis = 3000, easing = LinearEasing))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onHold3sRemove()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (job.isActive) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(150)) }
                        }
                    }
                )
            }
    ) {
        Column {
            Column(modifier = Modifier.padding(12.dp)) {
                Icon(
                    painter = painterResource(spec.iconRes),
                    contentDescription = spec.name,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    spec.name,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (progress.value > 0.05f) "Removing ${(3.0f * (1f - progress.value)).toInt() + 1}s..." else sub,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    color = if (progress.value > 0.05f) Color(0xFFEF4444)
                    else if (active) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Transparent)
            ) {
                if (progress.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.value)
                            .background(Color(0xFFEF4444))
                    )
                }
            }
        }
    }
}

@Composable
private fun Hold3sRemoveButton(label: String, onRemoved: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x1AEF4444),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(durationMillis = 3000, easing = LinearEasing))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemoved()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (job.isActive) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(150)) }
                        }
                    }
                )
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (progress.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .background(Color(0x55EF4444))
                )
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (progress.value > 0.05f) "Keep holding (${(3.0f * (1f - progress.value)).toInt() + 1}s)..." else label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFEF4444)
                )
            }
        }
    }
}
