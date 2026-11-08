package com.mux.android.files

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class Item(val f: File, val dir: Boolean, val size: Long, val mod: Long) {
    val name: String get() = f.name
    val path: String get() = f.absolutePath
}

/** The file browser state: current folder, listing, selection and clipboard. */
object Br {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var path by mutableStateOf(File("/"))
    var items by mutableStateOf<List<Item>>(emptyList())
    var title by mutableStateOf<String?>(null)      // not null: showing a category / search result
    var loading by mutableStateOf(false)
    var sel by mutableStateOf<Set<String>>(emptySet())
    var clip by mutableStateOf<List<File>>(emptyList())
    var clipCut by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    private val back = ArrayList<File>()
    private var job: Job? = null

    fun init() {
        path = Fs.ROOT
    }

    fun toItem(f: File): Item = Item(f, f.isDirectory, if (f.isDirectory) 0L else f.length(), f.lastModified())

    private fun sorted(list: List<Item>): List<Item> {
        val cmp: Comparator<Item> = when (Prefs.sortBy) {
            1 -> compareBy<Item> { it.mod }
            2 -> compareBy<Item> { it.size }
            3 -> compareBy<Item>({ Fs.ext(it.f) }, { it.name.lowercase() })
            else -> compareBy<Item> { it.name.lowercase() }
        }
        val c = if (Prefs.asc) cmp else cmp.reversed()
        return list.sortedWith(compareBy<Item> { !it.dir }.then(c))
    }

    fun reload() {
        val t = title
        if (t != null) return
        val dir = path
        job?.cancel()
        loading = true
        job = scope.launch {
            val list = withContext(Dispatchers.IO) {
                val kids = dir.listFiles()?.toList() ?: emptyList()
                sorted(kids.filter { Prefs.hidden || !it.name.startsWith(".") }.map { toItem(it) })
            }
            items = list
            loading = false
            tick++
        }
    }

    fun open(dir: File, push: Boolean = true) {
        if (!dir.isDirectory) return
        if (push && title == null) back.add(path)
        title = null
        sel = emptySet()
        path = dir
        reload()
    }

    /** Go one step back. Returns false when there is nowhere to go. */
    fun goBack(): Boolean {
        if (sel.isNotEmpty()) { sel = emptySet(); return true }
        if (title != null) { title = null; reload(); return true }
        if (back.isNotEmpty()) { val p = back.removeAt(back.size - 1); path = p; reload(); return true }
        return up()
    }

    fun up(): Boolean {
        val p = path.parentFile ?: return false
        if (path.absolutePath == Fs.ROOT.absolutePath || !p.canRead()) return false
        sel = emptySet()
        path = p
        reload()
        return true
    }

    fun showList(name: String, files: List<File>) {
        title = name
        sel = emptySet()
        scope.launch {
            items = withContext(Dispatchers.IO) { sorted(files.map { toItem(it) }) }
        }
    }

    fun search(q: String) {
        val base = path
        title = "SEARCH: $q"
        sel = emptySet()
        loading = true
        items = emptyList()
        job?.cancel()
        job = scope.launch {
            val res = withContext(Dispatchers.IO) {
                val out = ArrayList<File>()
                val low = q.lowercase()
                fun walk(d: File) {
                    if (out.size >= 500) return
                    val kids = d.listFiles() ?: return
                    for (k in kids) {
                        if (out.size >= 500) return
                        if (k.name.lowercase().contains(low)) out.add(k)
                        if (k.isDirectory && k.absolutePath != Trash.dir.absolutePath) walk(k)
                    }
                }
                walk(base)
                sorted(out.map { toItem(it) })
            }
            items = res
            loading = false
        }
    }

    fun toggle(it: Item) {
        sel = if (it.path in sel) sel - it.path else sel + it.path
    }

    fun selectedFiles(): List<File> = items.filter { it.path in sel }.map { it.f }
}

/** One background job at a time (copy, move, delete, zip...). Shows progress in the browser. */
object Tasks {
    var label by mutableStateOf("")
    var done by mutableLongStateOf(0L)
    var total by mutableLongStateOf(0L)
    var busy by mutableStateOf(false)
    private var cancel = Cancel()

    fun cancel() { cancel.on = true }

    /** [work] runs on a background thread; call the lambda it gets to report bytes done. */
    fun run(ctx: Context, name: String, totalBytes: Long, work: (prog: (Long) -> Unit, c: Cancel) -> String) {
        if (busy) { Util.toast(ctx, "WAIT: ANOTHER TASK IS RUNNING"); return }
        busy = true
        label = name
        done = 0
        total = totalBytes
        cancel = Cancel()
        val c = cancel
        Br.scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    work({ n -> done += n }, c)
                } catch (e: InterruptedException) {
                    "CANCELLED"
                } catch (e: Exception) {
                    "FAILED: ${e.message}"
                }
            }
            busy = false
            Util.toast(ctx, msg)
            Br.sel = emptySet()
            Br.reload()
        }
    }
}
