package com.krishna.tiledeck

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.TileCatalog
import com.krishna.tiledeck.data.TilePrefs
import com.krishna.tiledeck.data.TileSpec
import com.krishna.tiledeck.system.MotoTools
import com.krishna.tiledeck.tiles.TileActionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppItem(val label: String, val packageName: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTab = when (intent?.data?.host) {
            "applock" -> 1
            "permissions" -> 2
            else -> 0
        }

        setContent {
            var darkTheme by remember { mutableStateOf(TilePrefs.isDarkTheme(this)) }
            val colors = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFF2DD4BF),
                    background = Color(0xFF0B0E14),
                    surface = Color(0xFF131822),
                    surfaceVariant = Color(0xFF1C2331)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF0F766E),
                    background = Color(0xFFF4F7FB),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFE9EFF6)
                )
            }

            MaterialTheme(colorScheme = colors) {
                TileDeckRoot(
                    initialTab = initialTab,
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
private fun TileDeckRoot(initialTab: Int, darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var hasRoot by remember { mutableStateOf(TilePrefs.isRootGranted(context)) }
    var checkingRoot by remember { mutableStateOf(false) }
    val tabs = listOf("Tile Hub", "Apps & Lock", "Permissions")

    fun triggerSuRequest() {
        if (checkingRoot) return
        checkingRoot = true
        scope.launch {
            val granted = withContext(Dispatchers.IO) { MotoTools.requestRootAndSync(context) }
            hasRoot = granted
            checkingRoot = false
        }
    }

    // Ask SU automatically on app launch and auto-hide root tiles if denied
    LaunchedEffect(Unit) {
        triggerSuRequest()
    }

    val visibleCount = TileCatalog.visibleTiles(hasRoot).size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { idx, label ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Text("${idx + 1}", fontWeight = FontWeight.Bold) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TileDeck v4.0.0", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (checkingRoot) "Checking SU access..."
                        else if (hasRoot) "$visibleCount Direct Tiles | SU Granted"
                        else "$visibleCount Non-Root Tiles | ${TileCatalog.hiddenRootCount()} Root Tiles Hidden",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
                OutlinedButton(onClick = onToggleTheme) {
                    Text(if (darkTheme) "Light" else "Dark")
                }
            }
            Spacer(Modifier.height(10.dp))
            when (selectedTab) {
                0 -> TileHubTab(hasRoot = hasRoot, checkingRoot = checkingRoot, onRequestSu = { triggerSuRequest() })
                1 -> AppsAndLockTab(hasRoot = hasRoot)
                else -> PermissionsTab(hasRoot = hasRoot, checkingRoot = checkingRoot, onRequestSu = { triggerSuRequest() })
            }
        }
    }
}

@Composable
private fun TileHubTab(hasRoot: Boolean, checkingRoot: Boolean, onRequestSu: () -> Unit) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var enabledOnly by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    val availableTiles = remember(hasRoot) { TileCatalog.visibleTiles(hasRoot) }
    val categories = remember(hasRoot) { TileCatalog.visibleCategories(hasRoot) }
    LaunchedEffect(hasRoot) {
        if (selectedCategory !in categories) selectedCategory = "All"
    }

    val enabledCount = remember(tick, hasRoot) { availableTiles.count { TilePrefs.isTileEnabledInPanel(context, it) } }
    val filtered = remember(search, selectedCategory, enabledOnly, tick, hasRoot) {
        availableTiles.filter { spec ->
            (selectedCategory == "All" || spec.category == selectedCategory) &&
                (!enabledOnly || TilePrefs.isTileEnabledInPanel(context, spec)) &&
                (search.isBlank() || spec.name.contains(search, true) || spec.description.contains(search, true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!hasRoot) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Root (SU) Not Granted", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${TileCatalog.hiddenRootCount()} root tiles are auto-hidden from Hub and Quick Settings.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onRequestSu, enabled = !checkingRoot) {
                        Text(if (checkingRoot) "Asking..." else "Request SU")
                    }
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search $enabledCount enabled / ${availableTiles.size} available tiles...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = enabledOnly, onClick = { enabledOnly = !enabledOnly }, label = { Text("Enabled ($enabledCount)") })
            categories.forEach { cat ->
                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { spec ->
                TileCard(
                    spec = spec,
                    enabled = TilePrefs.isTileEnabledInPanel(context, spec),
                    subtitle = TilePrefs.getSubtitle(context, spec.id, "Off"),
                    onToggle = {
                        TilePrefs.setTileEnabledInPanel(context, spec, it)
                        tick++
                    },
                    onRun = {
                        TileActionEngine.execute(context, spec.id)
                        tick++
                    }
                )
            }
        }
    }
}

@Composable
private fun TileCard(
    spec: TileSpec,
    enabled: Boolean,
    subtitle: String,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("#${spec.number}  ${spec.name}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("${spec.category}  |  ${spec.permissionTier}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(6.dp))
            Text(spec.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("State: $subtitle", fontSize = 11.sp)
                }
                TextButton(onClick = onRun) { Text("Run Now") }
            }
        }
    }
}

@Composable
private fun AppsAndLockTab(hasRoot: Boolean) {
    val context = LocalContext.current
    var appSearch by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf(TilePrefs.getAppLockPin(context)) }
    var armed by remember { mutableStateOf(TilePrefs.isAppLockArmed(context)) }
    var tick by remember { mutableIntStateOf(0) }

    val installedApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { InstalledAppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    val filteredApps = remember(appSearch, installedApps) {
        installedApps.filter { appSearch.isBlank() || it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (hasRoot) "App Lock & Hidden Tray" else "App Lock", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            if (hasRoot) "Lock apps with PIN or mark for Hidden App Tray toggle"
                            else "Lock apps with PIN via Accessibility (Grant SU for Hidden Tray)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(checked = armed, onCheckedChange = {
                        armed = it
                        TilePrefs.setAppLockArmed(context, it)
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("App Lock PIN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { TilePrefs.setAppLockPin(context, pinInput) }) { Text("Save PIN") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = appSearch,
            onValueChange = { appSearch = it },
            placeholder = { Text("Filter installed apps...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filteredApps, key = { it.packageName }) { app ->
                val isLocked = remember(tick) { app.packageName in TilePrefs.getLockedPackages(context) }
                val isHidden = remember(tick) { app.packageName in TilePrefs.getHiddenTrayPackages(context) }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = isLocked,
                                onClick = { TilePrefs.setPackageLocked(context, app.packageName, !isLocked); tick++ },
                                label = { Text("Lock") }
                            )
                            if (hasRoot) {
                                FilterChip(
                                    selected = isHidden,
                                    onClick = { TilePrefs.setPackageInHiddenTray(context, app.packageName, !isHidden); tick++ },
                                    label = { Text("Hide Tray") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsTab(hasRoot: Boolean, checkingRoot: Boolean, onRequestSu: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val a11yGranted = remember(tick) { TileDeckAccessibilityService.instance != null }
    val overlayGranted = remember(tick) { Settings.canDrawOverlays(context) }
    val writeSettingsGranted = remember(tick) { Settings.System.canWrite(context) }
    val dndGranted = remember(tick) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
    }
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
        Triple("Display Over Other Apps", "Blackout Pocket, Extra Dim, Amber Filter, Quick Overlays", overlayGranted to Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))),
        Triple("Modify System Settings", "Direct Brightness, Timeout, Rotation, Haptics", writeSettingsGranted to Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))),
        Triple("Do Not Disturb Policy", "Direct Sound Profile, Mute Except Alarms, Study Mode", dndGranted to Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)),
        Triple("Usage Access", "App Lock & Foreground App Detection", usageGranted to Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Permission Center", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { tick++ }) { Text("Refresh") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { (title, desc, stateAndIntent) ->
                val granted = stateAndIntent.first
                val intent = stateAndIntent.second
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.width(8.dp))
                        if (granted) {
                            Text("GRANTED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else if (intent != null) {
                            Button(onClick = {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            }) { Text("Grant") }
                        } else {
                            Button(onClick = onRequestSu, enabled = !checkingRoot) {
                                Text(if (checkingRoot) "Asking..." else "Ask SU")
                            }
                        }
                    }
                }
            }
        }
    }
}
