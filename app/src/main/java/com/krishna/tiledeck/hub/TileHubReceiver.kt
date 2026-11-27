package com.krishna.tiledeck.hub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.krishna.tiledeck.data.TileCatalog
import com.krishna.tiledeck.data.TilePrefs
import com.krishna.tiledeck.tiles.TileActionEngine

class TileHubReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tileId = intent.getStringExtra("tile_id") ?: return
        val spec = TileCatalog.findById(tileId) ?: return
        when (intent.action) {
            "com.krishna.tiledeck.ACTION_TRIGGER_TILE" ->
                TileActionEngine.execute(context.applicationContext, tileId)
            "com.krishna.tiledeck.ACTION_TOGGLE_TILE_ENABLED" ->
                TilePrefs.setTileEnabledInPanel(context.applicationContext, spec, intent.getBooleanExtra("enabled", true))
        }
    }
}
