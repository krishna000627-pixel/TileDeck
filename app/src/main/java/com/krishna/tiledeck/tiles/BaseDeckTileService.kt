package com.krishna.tiledeck.tiles

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.krishna.tiledeck.data.TileCatalog
import com.krishna.tiledeck.data.TilePrefs

abstract class BaseDeckTileService(private val tileId: String) : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val res = TileActionEngine.execute(applicationContext, tileId, dryRunTest = false)
        val intent = res.launchIntent
        if (intent != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pi = PendingIntent.getActivity(
                        applicationContext,
                        tileId.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val spec = TileCatalog.findById(tileId)
        val isMomentary = tileId == "live_sync"
        val active = if (isMomentary) false else TilePrefs.isTileActive(applicationContext, tileId)
        val sub = if (isMomentary) "Idle" else TilePrefs.getSubtitle(applicationContext, tileId, if (active) "Active" else "Off")
        tile.label = spec?.name ?: tileId
        if (spec != null) {
            tile.icon = Icon.createWithResource(applicationContext, spec.iconRes)
        }
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = sub
        }
        tile.updateTile()
    }
}
