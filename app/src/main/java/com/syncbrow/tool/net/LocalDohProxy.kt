package com.syncbrow.tool.net

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.*
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object LocalDohProxy {
    private const val TAG = "LocalDohProxy"
    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val proxyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val singleThreadExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var currentProvider: String = "opendns"

    @Volatile
    private var currentDohClient: DnsOverHttps? = null

    fun getActivePort(): Int = serverSocket?.localPort ?: -1

    @Synchronized
    fun start(context: Context, provider: String = "opendns") {
        currentProvider = provider
        setupDohClient(provider)

        if (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
            Log.d(TAG, "Proxy already running on port ${serverSocket!!.localPort}")
            return
        }

        try {
            val sSocket = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
            serverSocket = sSocket
            val port = sSocket.localPort
            isRunning.set(true)

            proxyJob = proxyScope.launch {
                while (isRunning.get() && !sSocket.isClosed) {
                    try {
                        val client = sSocket.accept()
                        launch { handleClientSocket(client) }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                        Log.w(TAG, "Error accepting connection: ${e.message}")
                    }
                }
            }

            // Hook into WebView ProxyController
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("127.0.0.1:$port")
                    .addBypassRule("<-loopback>")
                    .build()
                ProxyController.getInstance().setProxyOverride(proxyConfig, singleThreadExecutor) {
                    Log.i(TAG, "ProxyController override applied on port $port")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LocalDohProxy", e)
        }
    }

    @Synchronized
    fun updateProvider(provider: String) {
        currentProvider = provider
        setupDohClient(provider)
    }

    private fun setupDohClient(provider: String) {
        try {
            val baseClient = OkHttpClient.Builder().build()
            val builder = DnsOverHttps.Builder().client(baseClient)

            when (provider.lowercase()) {
                "cloudflare" -> {
                    builder.url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(
                            InetAddress.getByName("1.1.1.1"),
                            InetAddress.getByName("1.0.0.1")
                        )
                }
                "google" -> {
                    builder.url("https://dns.google/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(
                            InetAddress.getByName("8.8.8.8"),
                            InetAddress.getByName("8.8.4.4")
                        )
                }
                else -> { // Default OpenDNS
                    builder.url("https://doh.opendns.com/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(
                            InetAddress.getByName("208.67.222.222"),
                            InetAddress.getByName("208.67.220.220"),
                            InetAddress.getByName("146.112.41.2")
                        )
                }
            }
            currentDohClient = builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup DoH client for $provider", e)
        }
    }

    private fun resolveHost(host: String): List<InetAddress> {
        val doh = currentDohClient
        if (doh != null) {
            try {
                val results = doh.lookup(host)
                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                Log.w(TAG, "DoH lookup failed for $host, fallback to default: ${e.message}")
            }
        }
        return InetAddress.getAllByName(host).toList()
    }

    private suspend fun handleClientSocket(client: Socket) = withContext(Dispatchers.IO) {
        try {
            val inStream = client.getInputStream()
            val outStream = client.getOutputStream()

            // Read the HTTP request line
            val firstLine = readLine(inStream) ?: run {
                client.close()
                return@withContext
            }

            if (firstLine.startsWith("CONNECT ", ignoreCase = true)) {
                // HTTPS CONNECT Tunnel
                val parts = firstLine.split(" ")
                val hostPort = parts[1]
                val (host, port) = if (hostPort.contains(":")) {
                    val idx = hostPort.lastIndexOf(":")
                    Pair(hostPort.substring(0, idx), hostPort.substring(idx + 1).toIntOrNull() ?: 443)
                } else {
                    Pair(hostPort, 443)
                }

                // Consume remaining headers
                while (true) {
                    val line = readLine(inStream)
                    if (line.isNullOrEmpty()) break
                }

                // Resolve target using DoH
                val targetAddresses = try {
                    resolveHost(host)
                } catch (e: Exception) {
                    outStream.write("HTTP/1.1 504 Gateway Timeout\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    outStream.flush()
                    client.close()
                    return@withContext
                }

                val targetSocket = Socket()
                try {
                    targetSocket.connect(InetSocketAddress(targetAddresses.first(), port), 10_000)
                    outStream.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    outStream.flush()
                    pipeSockets(client, targetSocket)
                } catch (e: Exception) {
                    outStream.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    outStream.flush()
                    client.close()
                    targetSocket.close()
                }
            } else {
                // Regular HTTP GET/POST proxying
                val parts = firstLine.split(" ")
                if (parts.size >= 2) {
                    val uriStr = parts[1]
                    val uri = try { java.net.URI(uriStr) } catch (_: Exception) { null }
                    val host = uri?.host ?: ""
                    val port = if (uri?.port != null && uri.port != -1) uri.port else 80
                    if (host.isNotEmpty()) {
                        val targetAddresses = resolveHost(host)
                        val targetSocket = Socket()
                        targetSocket.connect(InetSocketAddress(targetAddresses.first(), port), 10_000)
                        val targetOut = targetSocket.getOutputStream()
                        val path = (uri?.rawPath ?: "/") + if (uri?.rawQuery != null) "?${uri.rawQuery}" else ""
                        val rewrittenLine = "${parts[0]} $path ${if (parts.size > 2) parts[2] else "HTTP/1.1"}\r\n"
                        targetOut.write(rewrittenLine.toByteArray(Charsets.US_ASCII))
                        pipeSockets(client, targetSocket)
                        return@withContext
                    }
                }
                client.close()
            }
        } catch (e: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var b: Int
        while (input.read().also { b = it } != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return if (sb.isEmpty() && b == -1) null else sb.toString()
    }

    private suspend fun pipeSockets(s1: Socket, s2: Socket) = coroutineScope {
        val j1 = launch(Dispatchers.IO) {
            try {
                val buf = ByteArray(16384)
                val in1 = s1.getInputStream()
                val out2 = s2.getOutputStream()
                var len: Int
                while (in1.read(buf).also { len = it } != -1) {
                    out2.write(buf, 0, len)
                    out2.flush()
                }
            } catch (_: Exception) {}
            finally {
                try { s2.shutdownOutput() } catch (_: Exception) {}
            }
        }
        val j2 = launch(Dispatchers.IO) {
            try {
                val buf = ByteArray(16384)
                val in2 = s2.getInputStream()
                val out1 = s1.getOutputStream()
                var len: Int
                while (in2.read(buf).also { len = it } != -1) {
                    out1.write(buf, 0, len)
                    out1.flush()
                }
            } catch (_: Exception) {}
            finally {
                try { s1.shutdownOutput() } catch (_: Exception) {}
            }
        }
        j1.join()
        j2.join()
        try { s1.close() } catch (_: Exception) {}
        try { s2.close() } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(singleThreadExecutor) {
                    Log.i(TAG, "ProxyController override cleared")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing proxy override", e)
        }
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        proxyJob?.cancel()
        proxyJob = null
    }
}
