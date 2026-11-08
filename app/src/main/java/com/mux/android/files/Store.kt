package com.mux.android.files

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

object Prefs {
    private var sp: SharedPreferences? = null
    var amber by mutableStateOf(false)
    var grid by mutableStateOf(false)
    var sortBy by mutableIntStateOf(0)          // 0 name, 1 date, 2 size, 3 type
    var asc by mutableStateOf(true)
    var hidden by mutableStateOf(false)
    var useTrash by mutableStateOf(true)
    var confirmDelete by mutableStateOf(true)
    var bookmarks by mutableStateOf<List<String>>(emptyList())

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("mux_files", Context.MODE_PRIVATE)
        sp = p
        amber = p.getBoolean("amber", false)
        grid = p.getBoolean("grid", false)
        sortBy = p.getInt("sortBy", 0)
        asc = p.getBoolean("asc", true)
        hidden = p.getBoolean("hidden", false)
        useTrash = p.getBoolean("trash", true)
        confirmDelete = p.getBoolean("confirm", true)
        val l = ArrayList<String>()
        try {
            val a = JSONArray(p.getString("bookmarks", "[]"))
            for (i in 0 until a.length()) l.add(a.getString(i))
        } catch (e: Exception) {
        }
        bookmarks = l
    }

    fun commit() {
        sp?.edit()
            ?.putBoolean("amber", amber)?.putBoolean("grid", grid)?.putInt("sortBy", sortBy)?.putBoolean("asc", asc)
            ?.putBoolean("hidden", hidden)?.putBoolean("trash", useTrash)?.putBoolean("confirm", confirmDelete)
            ?.putString("bookmarks", JSONArray(bookmarks).toString())
            ?.apply()
    }
}
