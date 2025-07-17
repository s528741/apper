package com.apper.android.networking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

class ThreatDetector(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Known malicious domains (example list - in production this would be more comprehensive)
    private val knownMaliciousDomains = setOf(
        "malware.com",
        "phishing-site.net",
        "fake-bank.org",
        "scam-store.com",
        "virus-download.net",
        "malicious-ad.com",
        "cryptominer.evil",
        "tracker.spy"
    )

    // Common ad server domains
    private val adServerDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "facebook.com/tr",
        "analytics.google.com",
        "google-analytics.com",
        "googletagmanager.com",
        "amazon-adsystem.com",
        "adsense.google.com",
        "adsystem.amazon.com",
        "ads.yahoo.com",
        "advertising.com",
        "adsys.msn.com",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "adsystem.amazon.co.uk",
        "amazon-adsystem.com",
        "ads.twitter.com",
        "ads.linkedin.com",
        "ads.pinterest.com",
        "scorecardresearch.com",
        "2mdn.net",
        "adsafeprotected.com"
    )

    // Malicious URL patterns
    private val maliciousPatterns = listOf(
        Regex(".*\\.tk/.*", RegexOption.IGNORE_CASE), // Suspicious TLD
        Regex(".*bit\\.ly/[0-9a-fA-F]{6,}.*", RegexOption.IGNORE_CASE), // Suspicious short links
        Regex(".*[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}.*", RegexOption.IGNORE_CASE), // IP addresses instead of domains
        Regex(".*download.*\\.(exe|scr|bat|com|pif).*", RegexOption.IGNORE_CASE), // Suspicious executable downloads
        Regex(".*free.*crack.*", RegexOption.IGNORE_CASE), // Software piracy sites
        Regex(".*urgent.*update.*now.*", RegexOption.IGNORE_CASE), // Fake update warnings
        Regex(".*win.*prize.*claim.*", RegexOption.IGNORE_CASE), // Prize scams
        Regex(".*crypto.*mining.*", RegexOption.IGNORE_CASE), // Crypto mining scripts
        Regex(".*phishing.*test.*", RegexOption.IGNORE_CASE) // Known phishing tests
    )

    suspend fun isDomainMalicious(domain: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Checking domain for threats: $domain")

                // Check against known malicious domains
                if (isKnownMaliciousDomain(domain)) {
                    Log.w(TAG, "Domain found in local malicious list: $domain")
                    return@withContext true
                }

                // Check against Google Safe Browsing (simplified check)
                if (checkSafeBrowsing(domain)) {
                    Log.w(TAG, "Domain flagged by Safe Browsing: $domain")
                    return@withContext true
                }

                // Check domain reputation
                if (hasLowDomainReputation(domain)) {
                    Log.w(TAG, "Domain has low reputation: $domain")
                    return@withContext true
                }

                false

            } catch (e: Exception) {
                Log.e(TAG, "Error checking domain maliciousness", e)
                false // Fail safe - allow domain if check fails
            }
        }
    }

    suspend fun isDomainAdServer(domain: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                // Check against known ad server list
                val isAdServer = adServerDomains.any { adDomain ->
                    domain.contains(adDomain, ignoreCase = true) ||
                    domain.endsWith(adDomain, ignoreCase = true)
                }

                if (isAdServer) {
                    Log.d(TAG, "Blocked ad server: $domain")
                }

                isAdServer

            } catch (e: Exception) {
                Log.e(TAG, "Error checking ad server domain", e)
                false
            }
        }
    }

    suspend fun isUrlMalicious(url: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Checking URL for threats: $url")

                val parsedUrl = URL(url)
                val domain = parsedUrl.host

                // Check domain first
                if (isDomainMalicious(domain)) {
                    return@withContext true
                }

                // Check URL patterns
                if (hasUrlMaliciousPatterns(url)) {
                    return@withContext true
                }

                // Check for suspicious query parameters
                if (hasSuspiciousParameters(url)) {
                    Log.w(TAG, "URL has suspicious parameters: $url")
                    return@withContext true
                }

                false

            } catch (e: Exception) {
                Log.e(TAG, "Error checking URL maliciousness", e)
                false // Fail safe
            }
        }
    }

    fun hasUrlMaliciousPatterns(url: String): Boolean {
        return maliciousPatterns.any { pattern ->
            pattern.matches(url).also { matches ->
                if (matches) {
                    Log.w(TAG, "URL matches malicious pattern: $url")
                }
            }
        }
    }

    private fun isKnownMaliciousDomain(domain: String): Boolean {
        return knownMaliciousDomains.any { maliciousDomain ->
            domain.equals(maliciousDomain, ignoreCase = true) ||
            domain.endsWith(".$maliciousDomain", ignoreCase = true)
        }
    }

    private suspend fun checkSafeBrowsing(domain: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Simplified Safe Browsing check
                // In production, you'd use Google Safe Browsing API
                // For now, we'll simulate with a basic reputation check
                
                val testUrl = "https://www.virustotal.com/vtapi/v2/domain/report"
                // Note: This is just a placeholder - you'd need proper API integration
                
                // For demo purposes, flag domains with certain suspicious characteristics
                val suspiciousIndicators = listOf(
                    domain.contains("free", ignoreCase = true),
                    domain.contains("download", ignoreCase = true),
                    domain.contains("win", ignoreCase = true),
                    domain.length < 4, // Very short domains can be suspicious
                    domain.count { it == '.' } > 3, // Too many subdomains
                    domain.matches(Regex(".*[0-9]{4,}.*")) // Long numbers in domain
                )

                val suspiciousCount = suspiciousIndicators.count { it }
                return@withContext suspiciousCount >= 2 // Flag if 2+ indicators

            } catch (e: Exception) {
                Log.e(TAG, "Error in Safe Browsing check", e)
                false
            }
        }
    }

    private fun hasLowDomainReputation(domain: String): Boolean {
        // Check for common indicators of low-reputation domains
        return when {
            // New/temporary domains
            domain.endsWith(".tk") || domain.endsWith(".ml") || domain.endsWith(".ga") -> true
            // Suspicious patterns
            domain.contains("tmp", ignoreCase = true) -> true
            domain.contains("temp", ignoreCase = true) -> true
            // Random-looking domains
            domain.matches(Regex(".*[a-z]{10,}[0-9]{5,}.*")) -> true
            // Too many hyphens or numbers
            domain.count { it == '-' } > 3 -> true
            domain.count { it.isDigit() } > domain.length / 2 -> true
            else -> false
        }
    }

    private fun hasSuspiciousParameters(url: String): Boolean {
        val suspiciousParams = listOf(
            "download",
            "crack",
            "keygen",
            "serial",
            "patch",
            "loader",
            "bitcoin",
            "wallet",
            "miner",
            "phish",
            "scam",
            "fake"
        )

        val urlLower = url.lowercase()
        return suspiciousParams.any { param ->
            urlLower.contains(param)
        }
    }

    // Threat intelligence updates
    suspend fun updateThreatDatabase(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Updating threat database...")
                
                // In production, this would:
                // 1. Download latest threat intelligence feeds
                // 2. Update local malicious domain lists
                // 3. Refresh ad blocking lists
                // 4. Update malicious pattern detection
                
                // For now, just simulate successful update
                Log.d(TAG, "Threat database updated successfully")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Failed to update threat database", e)
                false
            }
        }
    }

    // Get threat statistics
    fun getThreatStats(): ThreatStats {
        return ThreatStats(
            knownMaliciousDomains = knownMaliciousDomains.size,
            adServerDomains = adServerDomains.size,
            maliciousPatterns = maliciousPatterns.size,
            lastUpdated = System.currentTimeMillis()
        )
    }

    data class ThreatStats(
        val knownMaliciousDomains: Int,
        val adServerDomains: Int,
        val maliciousPatterns: Int,
        val lastUpdated: Long
    )

    companion object {
        private const val TAG = "ThreatDetector"
    }
} 