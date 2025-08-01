package com.apper.android.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.apper.android.ApperApplication
import com.apper.android.core.AppConstants
import com.apper.android.networking.ThreatDetector
import com.apper.android.networking.VpnTunnel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class ApperVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private lateinit var preferencesManager: com.apper.android.core.PreferencesManager
    private lateinit var threatDetector: ThreatDetector
    private lateinit var vpnTunnel: VpnTunnel
    
    private var isRunning = false
    private var blockedRequests = 0
    private var totalRequests = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VpnService created")
        
        preferencesManager = (application as ApperApplication).preferencesManager
        threatDetector = ThreatDetector(this)
        vpnTunnel = VpnTunnel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VpnService start command received")
        
        when (intent?.action) {
            ACTION_START_VPN -> startVpn()
            ACTION_STOP_VPN -> stopVpn()
            else -> startVpn()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VpnService destroyed")
        stopVpn()
        serviceJob.cancel()
    }

    private fun startVpn() {
        if (isRunning) {
            Log.d(TAG, "VPN already running")
            return
        }

        try {
            Log.d(TAG, "Starting VPN service")
            
            // Validate prerequisites
            if (!validateVpnPrerequisites()) {
                Log.e(TAG, "VPN prerequisites not met")
                notifyVpnFailure("VPN prerequisites not met")
                return
            }
            
            // Create VPN interface with retry logic
            var attempts = 0
            val maxAttempts = 3
            
            while (attempts < maxAttempts && vpnInterface == null) {
                attempts++
                Log.d(TAG, "VPN creation attempt $attempts/$maxAttempts")
                
                vpnInterface = createVpnInterface()
                
                if (vpnInterface == null && attempts < maxAttempts) {
                    Thread.sleep(1000 * attempts) // Exponential backoff
                }
            }
            
            if (vpnInterface != null) {
                isRunning = true
                
                serviceScope.launch {
                    try {
                        preferencesManager.setVpnEnabled(true)
                        
                        // Start packet processing with error recovery
                        processVpnPacketsWithRecovery()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in VPN coroutine", e)
                        handleVpnError(e)
                    }
                }
                
                Log.d(TAG, "VPN service started successfully")
            } else {
                Log.e(TAG, "Failed to create VPN interface after $maxAttempts attempts")
                notifyVpnFailure("Failed to establish VPN connection")
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "VPN permission denied", e)
            notifyVpnFailure("VPN permission required")
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error starting VPN service", e)
            notifyVpnFailure("VPN service error: ${e.message}")
            stopVpn()
        }
    }

    private fun validateVpnPrerequisites(): Boolean {
        return try {
            // Check if VPN service is prepared
            val intent = VpnService.prepare(this)
            if (intent != null) {
                Log.w(TAG, "VPN permission not granted")
                return false
            }
            
            // Check network connectivity
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            if (networkInfo == null || !networkInfo.isConnected) {
                Log.w(TAG, "No active network connection")
                return false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating VPN prerequisites", e)
            false
        }
    }

    private suspend fun processVpnPacketsWithRecovery() {
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 5
        
        while (isRunning && !serviceJob.isCancelled) {
            try {
                processVpnPackets()
                consecutiveErrors = 0 // Reset on successful processing
            } catch (e: Exception) {
                consecutiveErrors++
                Log.e(TAG, "VPN packet processing error (attempt $consecutiveErrors)", e)
                
                if (consecutiveErrors >= maxConsecutiveErrors) {
                    Log.e(TAG, "Too many consecutive VPN errors, restarting...")
                    handleVpnError(e)
                    break
                } else {
                    // Brief delay before retry
                    delay(1000 * consecutiveErrors)
                }
            }
        }
    }

    private fun handleVpnError(error: Exception) {
        Log.e(TAG, "Handling VPN error", error)
        
        serviceScope.launch {
            try {
                // Stop current VPN
                stopVpn()
                
                // Wait before restart attempt
                delay(5000)
                
                // Attempt restart if service is still supposed to be running
                if (preferencesManager.isVpnEnabled.first()) {
                    Log.i(TAG, "Attempting VPN restart after error")
                    startVpn()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during VPN recovery", e)
                notifyVpnFailure("VPN recovery failed")
            }
        }
    }

    private fun notifyVpnFailure(message: String) {
        // Send broadcast to notify other components of VPN failure
        val intent = Intent(ACTION_VPN_FAILED).apply {
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun stopVpn() {
        if (!isRunning) {
            Log.d(TAG, "VPN already stopped")
            return
        }

        Log.d(TAG, "Stopping VPN service")
        isRunning = false
        
        serviceScope.launch {
            preferencesManager.setVpnEnabled(false)
        }
        
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "VPN service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN service", e)
        }
    }

    private fun createVpnInterface(): ParcelFileDescriptor? {
        return try {
            Builder()
                .setSession("Apper VPN")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(AppConstants.VPN_DNS_PRIMARY)
                .addDnsServer(AppConstants.VPN_DNS_SECONDARY)
                .setMtu(AppConstants.VPN_MTU)
                .addAllowedApplication(getBrowserApps())
                .setBlocking(false)
                .establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface", e)
            null
        }
    }

    private fun getBrowserApps(): List<String> {
        val browserApps = mutableListOf<String>()
        val packageManager = packageManager
        
        for (packageName in AppConstants.SUPPORTED_BROWSERS) {
            try {
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                browserApps.add(packageName)
                Log.d(TAG, "Added browser app to VPN: $packageName")
            } catch (e: PackageManager.NameNotFoundException) {
                // App not installed, skip
            }
        }
        
        return browserApps
    }

    private suspend fun processVpnPackets() {
        val vpnFileDescriptor = vpnInterface ?: return
        
        withContext(Dispatchers.IO) {
            val inputStream = FileInputStream(vpnFileDescriptor.fileDescriptor)
            val outputStream = FileOutputStream(vpnFileDescriptor.fileDescriptor)
            val inputChannel = inputStream.channel
            val outputChannel = outputStream.channel
            
            val packetBuffer = ByteBuffer.allocate(32767)
            
            try {
                while (isRunning && !serviceJob.isCancelled) {
                    packetBuffer.clear()
                    
                    val bytesRead = inputChannel.read(packetBuffer)
                    if (bytesRead > 0) {
                        totalRequests++
                        
                        packetBuffer.flip()
                        val packetData = ByteArray(bytesRead)
                        packetBuffer.get(packetData)
                        
                        // Process the packet
                        val processedPacket = processPacket(packetData)
                        
                        if (processedPacket != null) {
                            // Forward the packet
                            val outputBuffer = ByteBuffer.wrap(processedPacket)
                            outputChannel.write(outputBuffer)
                        } else {
                            // Packet was blocked
                            blockedRequests++
                            Log.d(TAG, "Blocked malicious packet. Total blocked: $blockedRequests")
                        }
                    }
                    
                    // Yield to prevent blocking
                    yield()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing VPN packets", e)
            } finally {
                try {
                    inputChannel.close()
                    outputChannel.close()
                    inputStream.close()
                    outputStream.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing VPN streams", e)
                }
            }
        }
    }

    private suspend fun processPacket(packetData: ByteArray): ByteArray? {
        return withContext(Dispatchers.Default) {
            try {
                // Parse IP packet
                val ipPacket = vpnTunnel.parseIpPacket(packetData)
                
                // Check if it's a DNS request or HTTP/HTTPS traffic
                when {
                    ipPacket.isDnsRequest() -> {
                        processDnsRequest(ipPacket)
                    }
                    ipPacket.isHttpTraffic() -> {
                        processHttpTraffic(ipPacket)
                    }
                    else -> {
                        // Pass through other traffic
                        packetData
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing packet", e)
                packetData // Return original packet on error
            }
        }
    }

    private suspend fun processDnsRequest(ipPacket: VpnTunnel.IpPacket): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val dnsQuery = ipPacket.getDnsQuery()
                val domain = dnsQuery?.domain
                
                if (domain != null) {
                    Log.d(TAG, "Processing DNS request for: $domain")
                    
                    // Check if domain is malicious
                    if (threatDetector.isDomainMalicious(domain)) {
                        Log.w(TAG, "Blocking malicious domain: $domain")
                        return@withContext null // Block the request
                    }
                    
                    // Check if domain is an ad server
                    if (threatDetector.isDomainAdServer(domain)) {
                        Log.d(TAG, "Blocking ad domain: $domain")
                        return@withContext null // Block the request
                    }
                }
                
                // Forward to secure DNS (AdGuard)
                vpnTunnel.forwardDnsRequest(ipPacket, AppConstants.VPN_DNS_PRIMARY)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing DNS request", e)
                ipPacket.toByteArray() // Return original on error
            }
        }
    }

    private suspend fun processHttpTraffic(ipPacket: VpnTunnel.IpPacket): ByteArray? {
        return withContext(Dispatchers.Default) {
            try {
                val url = ipPacket.extractUrl()
                
                if (url != null) {
                    Log.d(TAG, "Processing HTTP request for: $url")
                    
                    // Check URL against threat database
                    if (threatDetector.isUrlMalicious(url)) {
                        Log.w(TAG, "Blocking malicious URL: $url")
                        
                        // Notify accessibility service about blocked threat
                        notifyThreatBlocked(url)
                        
                        return@withContext null // Block the request
                    }
                    
                    // Check for known malicious patterns
                    if (threatDetector.hasUrlMaliciousPatterns(url)) {
                        Log.w(TAG, "Blocking URL with malicious patterns: $url")
                        return@withContext null
                    }
                }
                
                // Allow traffic to proceed
                ipPacket.toByteArray()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing HTTP traffic", e)
                ipPacket.toByteArray() // Return original on error
            }
        }
    }

    private fun notifyThreatBlocked(url: String) {
        // Send broadcast to notify other components
        val intent = Intent(ACTION_THREAT_BLOCKED).apply {
            putExtra(EXTRA_BLOCKED_URL, url)
            putExtra(EXTRA_THREAT_TYPE, "malicious_url")
        }
        sendBroadcast(intent)
    }

    // Public methods for status
    fun getVpnStats(): Pair<Int, Int> {
        return Pair(totalRequests, blockedRequests)
    }

    fun isVpnRunning(): Boolean {
        return isRunning && vpnInterface != null
    }

    companion object {
        private const val TAG = "ApperVpnService"
        
        const val ACTION_START_VPN = "com.apper.android.START_VPN"
        const val ACTION_STOP_VPN = "com.apper.android.STOP_VPN"
        const val ACTION_THREAT_BLOCKED = "com.apper.android.THREAT_BLOCKED"
        const val ACTION_VPN_FAILED = "com.apper.android.VPN_FAILED"
        
        const val EXTRA_BLOCKED_URL = "blocked_url"
        const val EXTRA_THREAT_TYPE = "threat_type"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }
} 