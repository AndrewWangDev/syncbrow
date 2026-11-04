package com.syncbrow.tool.util

import com.syncbrow.tool.data.PasswordEntity
import java.util.Random

object DecoyCredentialFactory {
    private val DEFAULT_SITES = listOf(
        "google.com",
        "github.com",
        "microsoft.com",
        "amazon.com",
        "netflix.com"
    )

    private val FIRST_NAMES = listOf("alex", "jordan", "taylor", "casey", "morgan", "riley", "sam")
    private val LAST_INITIALS = listOf("m", "d", "k", "t", "w", "b", "c")
    private val DOMAINS = listOf("example.com", "example.net", "example.org", "mail.test")

    // Stable seed so decoy credentials remain consistent across screen recompositions
    private const val BASE_SEED = 0x594E4342L // "SYNCB" in hex

    fun generateDecoyAccounts(seed: Long = BASE_SEED): List<PasswordEntity> {
        return DEFAULT_SITES.mapIndexed { index, site ->
            createEntity(site, seed + index * 997L, -101L - index)
        }
    }

    fun generateDecoyForSite(siteName: String, seed: Long = BASE_SEED): PasswordEntity {
        val siteHash = siteName.lowercase().hashCode().toLong()
        return createEntity(siteName, seed xor siteHash, -200L - (Math.abs(siteHash) % 1000L))
    }

    private fun createEntity(site: String, seed: Long, id: Long): PasswordEntity {
        val rng = Random(seed)
        val firstName = FIRST_NAMES[Math.abs(rng.nextInt()) % FIRST_NAMES.size]
        val initial = LAST_INITIALS[Math.abs(rng.nextInt()) % LAST_INITIALS.size]
        val num = 100 + (Math.abs(rng.nextInt()) % 900)
        val domain = DOMAINS[Math.abs(rng.nextInt()) % DOMAINS.size]

        val username = if (site.contains("github")) {
            "${firstName}_${initial}$num"
        } else {
            "${firstName}.${initial}$num@$domain"
        }

        val password = buildComplexPassword(rng)
        val daysAgo = 10 + (Math.abs(rng.nextInt()) % 60)
        val timestamp = System.currentTimeMillis() - daysAgo * 86_400_000L

        return PasswordEntity(
            id = id,
            siteName = site,
            username = username,
            password = password,
            timestamp = timestamp
        )
    }

    private fun buildComplexPassword(rng: Random, length: Int = 16): String {
        val uppers = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lowers = "abcdefghijkmnopqrstuvwxyz"
        val digits = "23456789"
        val symbols = "!@#$%^&*()-_=+"
        val all = uppers + lowers + digits + symbols

        val chars = CharArray(length)
        chars[0] = uppers[Math.abs(rng.nextInt()) % uppers.length]
        chars[1] = lowers[Math.abs(rng.nextInt()) % lowers.length]
        chars[2] = digits[Math.abs(rng.nextInt()) % digits.length]
        chars[3] = symbols[Math.abs(rng.nextInt()) % symbols.length]
        for (i in 4 until length) {
            chars[i] = all[Math.abs(rng.nextInt()) % all.length]
        }
        for (i in chars.indices) {
            val j = Math.abs(rng.nextInt()) % chars.size
            val temp = chars[i]
            chars[i] = chars[j]
            chars[j] = temp
        }
        return String(chars)
    }
}
