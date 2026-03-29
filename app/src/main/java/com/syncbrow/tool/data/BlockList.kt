package com.syncbrow.tool.data

/**
 * Common ad and tracker domain blocklists.
 * Covers major international and Chinese ad/tracking networks.
 */
object BlockList {

    private val AD_DOMAINS = setOf(
        "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net",
        "ad.doubleclick.net",
        "adservice.google.com",
        "tpc.googlesyndication.com",
        "an.facebook.com",
        "ads.facebook.com",
        "aax.amazon-adsystem.com",
        "adnxs.com",
        "ads.pubmatic.com",
        "adsrvr.org",
        "rubiconproject.com",
        "openx.net",
        "criteo.com",
        "outbrain.com",
        "taboola.com",
        "moatads.com",
        "pos.baidu.com",
        "cpro.baidu.com",
        "dsp.baidu.com",
        "e.qq.com",
        "gdt.qq.com",
        "mi.gdt.qq.com",
        "admaster.com.cn",
        "tanx.com",
        "alimama.com",
        "mmstat.com",
        "union.bytedance.com",
        "ad.toutiao.com",
        "pangolin-sdk-toutiao.com",
        "pglstatp-toutiao.com"
    )

    private val TRACKER_DOMAINS = setOf(
        "google-analytics.com",
        "www.google-analytics.com",
        "ssl.google-analytics.com",
        "analytics.google.com",
        "googletagmanager.com",
        "www.googletagmanager.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "bat.bing.com",
        "t.co",
        "analytics.twitter.com",
        "cdn.segment.com",
        "api.segment.io",
        "hotjar.com",
        "static.hotjar.com",
        "mouseflow.com",
        "fullstory.com",
        "mixpanel.com",
        "api.mixpanel.com",
        "sentry.io",
        "cdn.ravenjs.com",
        "hm.baidu.com",
        "tongji.baidu.com",
        "cnzz.com",
        "umeng.com",
        "log.umsns.com",
        "growth.umeng.com",
        "sdk.e.qq.com",
        "report.url.cn",
        "tr.jiandan100.cn",
        "beacon.qq.com",
        "pingtcss.qq.com",
        "report.idqqimg.com"
    )

    /**
     * Optimized check: splits host and checks suffixes to avoid O(N) linear search through the list.
     */
    fun shouldBlockAd(host: String): Boolean {
        if (host.isEmpty()) return false
        return isDomainInSet(host, AD_DOMAINS)
    }

    fun shouldBlockTracker(host: String): Boolean {
        if (host.isEmpty()) return false
        return isDomainInSet(host, TRACKER_DOMAINS)
    }

    private fun isDomainInSet(host: String, domainSet: Set<String>): Boolean {
        if (domainSet.contains(host)) return true
        
        // Check subdomains by splitting from right to left
        val parts = host.split('.')
        if (parts.size <= 1) return false
        
        var currentSuffix = parts.last()
        for (i in parts.size - 2 downTo 0) {
            currentSuffix = "${parts[i]}.$currentSuffix"
            if (domainSet.contains(currentSuffix)) return true
        }
        return false
    }
}
