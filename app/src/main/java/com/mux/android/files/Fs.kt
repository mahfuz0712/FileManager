package com.mux.android.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class Kind { DIR, IMAGE, VIDEO, AUDIO, DOC, ARCHIVE, APK, CODE, TEXT, OTHER }

class Cancel {
    @Volatile var on = false
}

class Stat(val files: Int, val dirs: Int, val bytes: Long)
class StorageInfo(val label: String, val dir: File, val total: Long, val free: Long)
class TrashItem(val id: String, val original: String, val time: Long, val file: File)

object Fs {
    val ROOT: File get() = Environment.getExternalStorageDirectory()

    private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val VID = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v", "ts")
    private val AUD = setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma", "amr")
    private val DOCS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "epub", "rtf", "csv")
    private val ARC = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "jar")
    private val CODE = setOf("kt", "java", "js", "ts", "tsx", "jsx", "py", "c", "h", "cpp", "cs", "go", "rs", "rb", "php", "sh", "html", "htm", "css", "xml", "json", "yml", "yaml", "gradle", "sql", "swift", "lua", "dart")
    private val TXT = setOf("txt", "md", "log", "ini", "conf", "cfg", "properties", "toml", "env", "gitignore", "srt", "vtt", "tsv")

    fun ext(f: File): String = f.name.substringAfterLast('.', "").lowercase()

    fun kind(f: File, isDir: Boolean = f.isDirectory): Kind {
        if (isDir) return Kind.DIR
        val e = ext(f)
        return when {
            e in IMG -> Kind.IMAGE
            e in VID -> Kind.VIDEO
            e in AUD -> Kind.AUDIO
            e in DOCS -> Kind.DOC
            e == "apk" || e == "apks" || e == "xapk" -> Kind.APK
            e in ARC -> Kind.ARCHIVE
            e in CODE -> Kind.CODE
            e in TXT -> Kind.TEXT
            else -> Kind.OTHER
        }
    }

    fun isTextLike(f: File): Boolean = kind(f).let { it == Kind.CODE || it == Kind.TEXT }

    fun mime(f: File): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(f)) ?: "*/*"

    fun human(b: Long): String {
        if (b < 1024) return "$b B"
        val u = arrayOf("KB", "MB", "GB", "TB")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return "%.1f %s".format(Locale.US, v, u[i])
    }

    fun date(t: Long): String = SimpleDateFormat("d MMM yyyy HH:mm", Locale.US).format(Date(t))
    fun dateShort(t: Long): String = SimpleDateFormat("d MMM yy", Locale.US).format(Date(t))

    fun uniqueTarget(dir: File, name: String): File {
        var t = File(dir, name)
        if (!t.exists()) return t
        val base = if (name.contains('.') && !name.startsWith(".")) name.substringBeforeLast('.') else name
        val ext = if (name.contains('.') && !name.startsWith(".")) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (t.exists()) { t = File(dir, "$base ($i)$ext"); i++ }
        return t
    }

    fun isInside(child: File, parent: File): Boolean {
        val c = child.canonicalPath
        val p = parent.canonicalPath
        return c == p || c.startsWith("$p/")
    }

    fun totalSize(files: List<File>): Long {
        var t = 0L
        fun walk(f: File) {
            if (f.isDirectory) f.listFiles()?.forEach { walk(it) } else t += f.length()
        }
        files.forEach { walk(it) }
        return t
    }

    fun stat(dir: File, cancel: Cancel? = null): Stat {
        var files = 0
        var dirs = 0
        var bytes = 0L
        fun walk(f: File) {
            if (cancel?.on == true) return
            val kids = f.listFiles() ?: return
            for (k in kids) {
                if (k.isDirectory) { dirs++; walk(k) } else { files++; bytes += k.length() }
            }
        }
        if (dir.isDirectory) walk(dir) else { files = 1; bytes = dir.length() }
        return Stat(files, dirs, bytes)
    }

    // ---------- copy / move / delete ----------

    private fun copyFile(src: File, dst: File, prog: (Long) -> Unit, c: Cancel) {
        src.inputStream().use { i ->
            dst.outputStream().use { o ->
                val b = ByteArray(64 * 1024)
                while (true) {
                    if (c.on) throw InterruptedException("cancelled")
                    val n = i.read(b)
                    if (n < 0) break
                    o.write(b, 0, n)
                    prog(n.toLong())
                }
            }
        }
        dst.setLastModified(src.lastModified())
    }

    fun copyTree(src: File, dstDir: File, prog: (Long) -> Unit, c: Cancel): File {
        val t = uniqueTarget(dstDir, src.name)
        if (src.isDirectory) {
            if (isInside(dstDir, src)) throw IllegalStateException("cannot copy a folder into itself")
            t.mkdirs()
            src.listFiles()?.forEach { copyTree(it, t, prog, c) }
        } else copyFile(src, t, prog, c)
        return t
    }

    fun moveTree(src: File, dstDir: File, prog: (Long) -> Unit, c: Cancel): File? {
        if (src.parentFile?.canonicalPath == dstDir.canonicalPath) return null
        if (src.isDirectory && isInside(dstDir, src)) throw IllegalStateException("cannot move a folder into itself")
        val t = uniqueTarget(dstDir, src.name)
        if (src.renameTo(t)) { prog(src.length()); return t }
        val r = copyTree(src, dstDir, prog, c)
        deleteTree(src)
        return r
    }

    fun deleteTree(f: File): Boolean = f.deleteRecursively()

    // ---------- zip ----------

    fun zip(files: List<File>, out: File, prog: (Long) -> Unit, c: Cancel) {
        ZipOutputStream(BufferedOutputStream(out.outputStream())).use { z ->
            fun add(f: File, path: String) {
                if (c.on) throw InterruptedException("cancelled")
                if (f.isDirectory) {
                    z.putNextEntry(ZipEntry("$path/").also { it.time = f.lastModified() })
                    z.closeEntry()
                    f.listFiles()?.forEach { add(it, "$path/${it.name}") }
                } else {
                    z.putNextEntry(ZipEntry(path).also { it.time = f.lastModified() })
                    f.inputStream().use { i ->
                        val b = ByteArray(64 * 1024)
                        while (true) {
                            if (c.on) throw InterruptedException("cancelled")
                            val n = i.read(b)
                            if (n < 0) break
                            z.write(b, 0, n)
                            prog(n.toLong())
                        }
                    }
                    z.closeEntry()
                }
            }
            for (f in files) if (f.canonicalPath != out.canonicalPath) add(f, f.name)
        }
    }

    fun unzip(zip: File, dest: File, prog: (Long) -> Unit, c: Cancel) {
        dest.mkdirs()
        ZipFile(zip).use { z ->
            val base = dest.canonicalPath
            for (e in z.entries()) {
                if (c.on) throw InterruptedException("cancelled")
                val t = File(dest, e.name).canonicalFile
                if (t.path != base && !t.path.startsWith("$base/")) continue // zip-slip guard
                if (e.isDirectory) { t.mkdirs(); continue }
                t.parentFile?.mkdirs()
                z.getInputStream(e).use { i ->
                    t.outputStream().use { o ->
                        val b = ByteArray(64 * 1024)
                        while (true) {
                            if (c.on) throw InterruptedException("cancelled")
                            val n = i.read(b)
                            if (n < 0) break
                            o.write(b, 0, n)
                            prog(n.toLong())
                        }
                    }
                }
                if (e.time > 0) t.setLastModified(e.time)
            }
        }
    }

    fun zipEntries(zip: File, max: Int = 300): Pair<List<Pair<String, Long>>, Int> {
        val out = ArrayList<Pair<String, Long>>()
        var total = 0
        ZipFile(zip).use { z ->
            for (e in z.entries()) {
                total++
                if (out.size < max) out.add(e.name to e.size)
            }
        }
        return out to total
    }

    fun checksum(f: File, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        f.inputStream().use { i ->
            val b = ByteArray(64 * 1024)
            while (true) {
                val n = i.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------- storage ----------

    fun volumes(ctx: Context): List<StorageInfo> {
        val out = ArrayList<StorageInfo>()
        try {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            for (v in sm.storageVolumes) {
                val d = if (android.os.Build.VERSION.SDK_INT >= 30) v.directory else null
                if (d == null || !d.exists()) continue
                val st = StatFs(d.path)
                out.add(StorageInfo(v.getDescription(ctx) ?: d.name, d, st.totalBytes, st.availableBytes))
            }
        } catch (e: Exception) {
        }
        if (out.isEmpty()) {
            val d = ROOT
            try {
                val st = StatFs(d.path)
                out.add(StorageInfo("Internal storage", d, st.totalBytes, st.availableBytes))
            } catch (e: Exception) {
            }
        }
        return out
    }

    // ---------- media store (categories, recent) ----------

    fun query(ctx: Context, where: String, args: Array<String>?, limit: Int = 500): List<File> {
        val out = ArrayList<File>()
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val dataCol = MediaStore.Files.FileColumns.DATA
            val sel = "($where) AND ${MediaStore.Files.FileColumns.SIZE} > 0 AND $dataCol NOT LIKE '%/.%'"
            ctx.contentResolver.query(
                uri, arrayOf(dataCol), sel, args,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val p = c.getString(0) ?: continue
                    val f = File(p)
                    if (f.isFile) out.add(f)
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun category(ctx: Context, key: String): List<File> {
        val mt = MediaStore.Files.FileColumns.MEDIA_TYPE
        val d = MediaStore.Files.FileColumns.DATA
        return when (key) {
            "IMAGES" -> query(ctx, "$mt = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}", null)
            "VIDEOS" -> query(ctx, "$mt = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}", null)
            "AUDIO" -> query(ctx, "$mt = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}", null)
            "DOCUMENTS" -> query(ctx, DOCS.joinToString(" OR ") { "$d LIKE '%.$it'" }, null)
            "APKS" -> query(ctx, "$d LIKE '%.apk'", null)
            "ARCHIVES" -> query(ctx, ARC.joinToString(" OR ") { "$d LIKE '%.$it'" }, null)
            "DOWNLOADS" -> query(ctx, "$d LIKE ?", arrayOf(File(ROOT, "Download").path + "/%"))
            else -> emptyList()
        }
    }

    fun recent(ctx: Context, n: Int = 30): List<File> = query(ctx, "${MediaStore.Files.FileColumns.MEDIA_TYPE} != 0 OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.%'", null, n)

    // ---------- intents ----------

    fun uri(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, "com.mux.android.files.provider", f)

    fun openExternal(ctx: Context, f: File) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri(ctx, f), mime(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Util.toast(ctx, "NO APP CAN OPEN THIS FILE")
        } catch (e: Exception) {
            Util.toast(ctx, "CANNOT OPEN: ${e.message}")
        }
    }

    fun share(ctx: Context, files: List<File>) {
        val fl = files.filter { it.isFile }
        if (fl.isEmpty()) { Util.toast(ctx, "SELECT FILES (FOLDERS CANNOT BE SHARED, ZIP THEM FIRST)"); return }
        try {
            val i = if (fl.size == 1) Intent(Intent.ACTION_SEND).setType(mime(fl[0])).putExtra(Intent.EXTRA_STREAM, uri(ctx, fl[0]))
            else Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fl.map { uri(ctx, it) }))
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Util.toast(ctx, "SHARE FAILED")
        }
    }
}

/** Recycle bin: deleted items are moved to a hidden folder on the main storage and can be restored. */
object Trash {
    val dir: File get() = File(Fs.ROOT, ".mux_trash")
    private val index: File get() = File(dir, ".index")

    fun items(): List<TrashItem> {
        val out = ArrayList<TrashItem>()
        try {
            if (!index.exists()) return out
            for (l in index.readLines()) {
                val p = l.split('\t')
                if (p.size < 3) continue
                val f = File(dir, p[0])
                if (f.exists()) out.add(TrashItem(p[0], p[1], p[2].toLongOrNull() ?: 0L, f))
            }
        } catch (e: Exception) {
        }
        return out.sortedByDescending { it.time }
    }

    private fun save(list: List<TrashItem>) {
        dir.mkdirs()
        index.writeText(list.joinToString("\n") { "${it.id}\t${it.original}\t${it.time}" })
    }

    fun put(f: File, c: Cancel): Boolean {
        return try {
            dir.mkdirs()
            val id = "${System.currentTimeMillis()}_${f.name}"
            val t = File(dir, id)
            if (!f.renameTo(t)) {
                Fs.copyTree(f, dir, { }, c).renameTo(t)
                Fs.deleteTree(f)
            }
            save(items() + TrashItem(id, f.absolutePath, System.currentTimeMillis(), t))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun restore(t: TrashItem): String {
        return try {
            val orig = File(t.original)
            val parent = orig.parentFile ?: Fs.ROOT
            parent.mkdirs()
            val target = Fs.uniqueTarget(parent, orig.name)
            if (!t.file.renameTo(target)) {
                Fs.copyTree(t.file, parent, { }, Cancel())
                Fs.deleteTree(t.file)
            }
            save(items().filter { it.id != t.id })
            "restored to ${target.absolutePath}"
        } catch (e: Exception) {
            "restore failed: ${e.message}"
        }
    }

    fun remove(t: TrashItem) {
        Fs.deleteTree(t.file)
        save(items().filter { it.id != t.id })
    }

    fun empty() {
        dir.listFiles()?.forEach { Fs.deleteTree(it) }
        dir.mkdirs()
    }
}
