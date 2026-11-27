package com.krishna.tiledeck.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.krishna.tiledeck.data.TileCatalog
import com.krishna.tiledeck.data.TilePrefs

class TileHubProvider : ContentProvider() {
    private val columns = arrayOf("number", "id", "name", "category", "description", "permission_tier", "enabled", "active", "subtitle")
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = context ?: return MatrixCursor(columns)
        val catFilter = uri.getQueryParameter("category")
        val cursor = MatrixCursor(columns)
        TileCatalog.allTiles
            .filter { catFilter.isNullOrBlank() || it.category.equals(catFilter, ignoreCase = true) }
            .forEach { spec ->
                val enabled = TilePrefs.isTileEnabledInPanel(ctx, spec)
                val active = TilePrefs.isTileActive(ctx, spec.id)
                val sub = TilePrefs.getSubtitle(ctx, spec.id, if (active) "Active" else "Off")
                cursor.addRow(arrayOf(spec.number, spec.id, spec.name, spec.category, spec.description, spec.permissionTier, if (enabled) 1 else 0, if (active) 1 else 0, sub))
            }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.com.krishna.tiledeck.tiles"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
