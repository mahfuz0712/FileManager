package com.mux.android.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Tiny web server: browse, download and upload files from a computer's browser on the same Wi-Fi.
 * The address contains a random PIN. Only use it on a network you trust (traffic is not encrypted).
 */
object WebShare {
    var running by mutableStateOf(false)
    var url by mutableStateOf("")
    var note by mutableStateOf("")
    private var server: ServerSocket? = null
    private var pin = ""
    private var root: File = File("/")

    fun ip(): String? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) if (a is Inet4Address && a.isSiteLocalAddress) return a.hostAddress
            }
        } catch (e: Exception) {
        }
        return null
    }

    fun start(dir: File): Boolean {
        if (running) return true
        val addr = ip()
        if (addr == null) { note = "No Wi-Fi / hotspot address found. Connect to Wi-Fi first."; return false }
        return try {
            val ss = try { ServerSocket(8080) } catch (e: Exception) { ServerSocket(0) }
            server = ss
            root = dir
            pin = (1000 + java.security.SecureRandom().nextInt(9000)).toString()
            url = "http://$addr:${ss.localPort}/$pin/"
            running = true
            note = ""
            thread(isDaemon = true) {
                while (!ss.isClosed) {
                    try {
                        val s = ss.accept()
                        thread(isDaemon = true) { try { handle(s) } catch (e: Exception) { } finally { try { s.close() } catch (x: Exception) { } } }
                    } catch (e: Exception) {
                        break
                    }
                }
            }
            true
        } catch (e: Exception) {
            note = "Cannot start: ${e.message}"
            false
        }
    }

    fun stop() {
        try { server?.close() } catch (e: Exception) { }
        server = null
        running = false
        url = ""
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
        return sb.toString()
    }

    private fun send(o: OutputStream, code: Int, msg: String, type: String, body: ByteArray, extra: String = "") {
        o.write("HTTP/1.1 $code $msg\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n$extra\r\n".toByteArray())
        o.write(body)
        o.flush()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun enc(rel: String) = rel.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun handle(s: Socket) {
        s.soTimeout = 60000
        val ins = BufferedInputStream(s.getInputStream())
        val out = BufferedOutputStream(s.getOutputStream())
        val req = readLine(ins) ?: return
        val p = req.split(' ')
        if (p.size < 2) return
        val method = p[0]
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(ins) ?: break
            if (l.isEmpty()) break
            val k = l.substringBefore(':').trim().lowercase()
            headers[k] = l.substringAfter(':', "").trim()
        }
        val path = try { URLDecoder.decode(p[1].substringBefore('?').replace("+", "%2B"), "UTF-8") } catch (e: Exception) { "/" }
        val prefix = "/$pin"
        if (path != prefix && !path.startsWith("$prefix/")) {
            send(out, 403, "Forbidden", "text/plain", "Forbidden".toByteArray())
            return
        }
        val rel = path.removePrefix(prefix).trim('/')
        val f = File(root, rel).canonicalFile
        if (!Fs.isInside(f, root)) { send(out, 403, "Forbidden", "text/plain", "Forbidden".toByteArray()); return }
        if (method == "POST") {
            if (!f.isDirectory) { send(out, 400, "Bad Request", "text/plain", "not a folder".toByteArray()); return }
            upload(ins, headers, f)
            send(out, 303, "See Other", "text/plain", ByteArray(0), "Location: /$pin/${enc(rel)}\r\n")
            return
        }
        if (!f.exists()) { send(out, 404, "Not Found", "text/plain", "Not found".toByteArray()); return }
        if (f.isDirectory) {
            val kids = (f.listFiles() ?: emptyArray()).filter { !it.name.startsWith(".") }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            val sb = StringBuilder()
            sb.append("<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>MUX Files</title>")
            sb.append("<style>body{background:#040a12;color:#d6f8ff;font:15px monospace;margin:0;padding:16px}a{color:#19e3ff;text-decoration:none}h2{color:#19e3ff}")
            sb.append("td{padding:6px 12px 6px 0;border-bottom:1px solid #19e3ff30}.d{color:#ffb000}.s{color:#3c8a99}form{margin:12px 0;padding:10px;border:1px solid #19e3ff60}button,input{font:inherit;color:#d6f8ff}button{background:#ffb00030;border:1px solid #ffb000;padding:6px 14px}</style></head><body>")
            sb.append("<h2>&#9672; MUX FILES</h2><div class=s>/${esc(rel)}</div>")
            sb.append("<form method=post enctype=multipart/form-data><input type=file name=file> <button>UPLOAD HERE</button></form><table>")
            if (rel.isNotEmpty()) {
                val up = rel.substringBeforeLast('/', "")
                sb.append("<tr><td colspan=3><a href='/$pin/${enc(up)}'>&#8593; ..</a></td></tr>")
            }
            for (k in kids) {
                val r = if (rel.isEmpty()) k.name else "$rel/${k.name}"
                sb.append("<tr><td><a class=${if (k.isDirectory) "d" else "f"} href='/$pin/${enc(r)}'>${if (k.isDirectory) "&#9635; " else ""}${esc(k.name)}</a></td><td class=s>${if (k.isDirectory) "" else Fs.human(k.length())}</td></tr>")
            }
            sb.append("</table></body></html>")
            send(out, 200, "OK", "text/html; charset=utf-8", sb.toString().toByteArray())
            return
        }
        val name = URLEncoder.encode(f.name, "UTF-8").replace("+", "%20")
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: ${Fs.mime(f)}\r\nContent-Length: ${f.length()}\r\n" +
                "Content-Disposition: ${if (p[1].contains("dl=1")) "attachment" else "inline"}; filename*=UTF-8''$name\r\nConnection: close\r\n\r\n").toByteArray()
        )
        f.inputStream().use { i ->
            val b = ByteArray(64 * 1024)
            while (true) {
                val n = i.read(b)
                if (n < 0) break
                out.write(b, 0, n)
            }
        }
        out.flush()
    }

    private fun upload(ins: InputStream, headers: Map<String, String>, dir: File) {
        val len = headers["content-length"]?.toLongOrNull() ?: return
        val ct = headers["content-type"] ?: return
        val boundary = ct.substringAfter("boundary=", "").trim('"')
        if (boundary.isEmpty()) return
        val cache = Util.appCtx?.cacheDir ?: return
        val tmp = File(cache, "upload.tmp")
        tmp.outputStream().use { o ->
            var left = len
            val b = ByteArray(64 * 1024)
            while (left > 0) {
                val n = ins.read(b, 0, minOf(b.size.toLong(), left).toInt())
                if (n < 0) break
                o.write(b, 0, n)
                left -= n
            }
        }
        try {
            RandomAccessFile(tmp, "r").use { r ->
                val total = r.length()
                val head = ByteArray(minOf(16384L, total).toInt())
                r.readFully(head)
                val hs = String(head, Charsets.ISO_8859_1)
                val hEnd = hs.indexOf("\r\n\r\n")
                if (hEnd < 0) return
                val fn = Regex("filename=\"([^\"]*)\"").find(hs.substring(0, hEnd))?.groupValues?.get(1) ?: return
                val name = String(fn.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8).substringAfterLast('/').substringAfterLast('\\')
                if (name.isBlank() || name == "." || name == "..") return
                val start = (hEnd + 4).toLong()
                val tailLen = minOf(total, (boundary.length + 16).toLong()).toInt()
                val tail = ByteArray(tailLen)
                r.seek(total - tailLen)
                r.readFully(tail)
                val ts = String(tail, Charsets.ISO_8859_1)
                val mk = ts.lastIndexOf("\r\n--$boundary")
                val end = if (mk >= 0) total - tailLen + mk else total
                if (end < start) return
                val target = Fs.uniqueTarget(dir, name)
                r.seek(start)
                target.outputStream().use { o ->
                    var left = end - start
                    val b = ByteArray(64 * 1024)
                    while (left > 0) {
                        val n = r.read(b, 0, minOf(b.size.toLong(), left).toInt())
                        if (n < 0) break
                        o.write(b, 0, n)
                        left -= n
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }
}
