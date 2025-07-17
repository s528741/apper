package com.apper.android.networking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VpnTunnel(private val context: Context) {

    companion object {
        private const val TAG = "VpnTunnel"
        
        // IP Protocol numbers
        private const val PROTOCOL_TCP = 6
        private const val PROTOCOL_UDP = 17
        
        // Port numbers
        private const val DNS_PORT = 53
        private const val HTTP_PORT = 80
        private const val HTTPS_PORT = 443
    }

    fun parseIpPacket(packetData: ByteArray): IpPacket {
        return try {
            val buffer = ByteBuffer.wrap(packetData).order(ByteOrder.BIG_ENDIAN)
            
            // Parse IP header
            val versionAndHeaderLength = buffer.get().toInt() and 0xFF
            val version = (versionAndHeaderLength shr 4) and 0xF
            val headerLength = (versionAndHeaderLength and 0xF) * 4
            
            if (version != 4) {
                throw IllegalArgumentException("Only IPv4 is supported")
            }
            
            val typeOfService = buffer.get().toInt() and 0xFF
            val totalLength = buffer.short.toInt() and 0xFFFF
            val identification = buffer.short.toInt() and 0xFFFF
            val flagsAndFragmentOffset = buffer.short.toInt() and 0xFFFF
            val timeToLive = buffer.get().toInt() and 0xFF
            val protocol = buffer.get().toInt() and 0xFF
            val headerChecksum = buffer.short.toInt() and 0xFFFF
            
            val sourceAddress = ByteArray(4)
            buffer.get(sourceAddress)
            
            val destinationAddress = ByteArray(4)
            buffer.get(destinationAddress)
            
            // Skip options if present
            if (headerLength > 20) {
                buffer.position(headerLength)
            }
            
            // Extract payload
            val payloadLength = totalLength - headerLength
            val payload = ByteArray(payloadLength)
            buffer.get(payload, 0, minOf(payloadLength, buffer.remaining()))
            
            IpPacket(
                version = version,
                headerLength = headerLength,
                typeOfService = typeOfService,
                totalLength = totalLength,
                identification = identification,
                flagsAndFragmentOffset = flagsAndFragmentOffset,
                timeToLive = timeToLive,
                protocol = protocol,
                headerChecksum = headerChecksum,
                sourceAddress = sourceAddress,
                destinationAddress = destinationAddress,
                payload = payload,
                originalData = packetData
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing IP packet", e)
            // Return a default packet that will be passed through
            IpPacket(originalData = packetData)
        }
    }

    suspend fun forwardDnsRequest(ipPacket: IpPacket, dnsServer: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Forwarding DNS request to $dnsServer")
                
                val dnsQuery = ipPacket.getDnsQuery()
                if (dnsQuery == null) {
                    Log.w(TAG, "Could not extract DNS query from packet")
                    return@withContext ipPacket.originalData
                }
                
                // Create DNS socket and forward request
                val socket = DatagramSocket()
                socket.soTimeout = 5000 // 5 second timeout
                
                val dnsAddress = InetAddress.getByName(dnsServer)
                val requestPacket = DatagramPacket(
                    dnsQuery.queryBytes,
                    dnsQuery.queryBytes.size,
                    dnsAddress,
                    DNS_PORT
                )
                
                socket.send(requestPacket)
                
                // Receive response
                val responseBuffer = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                socket.close()
                
                // Build response IP packet
                val responseData = responsePacket.data.copyOfRange(0, responsePacket.length)
                return@withContext buildDnsResponsePacket(ipPacket, responseData)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error forwarding DNS request", e)
                ipPacket.originalData // Return original on error
            }
        }
    }

    private fun buildDnsResponsePacket(originalPacket: IpPacket, dnsResponse: ByteArray): ByteArray {
        try {
            // Swap source and destination addresses
            val responsePacket = originalPacket.copy(
                sourceAddress = originalPacket.destinationAddress,
                destinationAddress = originalPacket.sourceAddress,
                payload = buildUdpResponse(originalPacket, dnsResponse)
            )
            
            return responsePacket.toByteArray()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error building DNS response packet", e)
            return originalPacket.originalData
        }
    }

    private fun buildUdpResponse(originalPacket: IpPacket, dnsResponse: ByteArray): ByteArray {
        // Simple UDP header construction
        val udpHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        
        // Extract original ports from UDP header
        val originalUdpHeader = ByteBuffer.wrap(originalPacket.payload).order(ByteOrder.BIG_ENDIAN)
        val sourcePort = originalUdpHeader.short.toInt() and 0xFFFF
        val destPort = originalUdpHeader.short.toInt() and 0xFFFF
        
        // Build response UDP header (swap ports)
        udpHeader.putShort(destPort.toShort()) // Source port (DNS)
        udpHeader.putShort(sourcePort.toShort()) // Destination port
        udpHeader.putShort((8 + dnsResponse.size).toShort()) // Length
        udpHeader.putShort(0) // Checksum (simplified - would calculate in production)
        
        // Combine header and DNS response
        val result = ByteArray(8 + dnsResponse.size)
        System.arraycopy(udpHeader.array(), 0, result, 0, 8)
        System.arraycopy(dnsResponse, 0, result, 8, dnsResponse.size)
        
        return result
    }

    data class IpPacket(
        val version: Int = 4,
        val headerLength: Int = 20,
        val typeOfService: Int = 0,
        val totalLength: Int = 0,
        val identification: Int = 0,
        val flagsAndFragmentOffset: Int = 0,
        val timeToLive: Int = 64,
        val protocol: Int = 0,
        val headerChecksum: Int = 0,
        val sourceAddress: ByteArray = byteArrayOf(),
        val destinationAddress: ByteArray = byteArrayOf(),
        val payload: ByteArray = byteArrayOf(),
        val originalData: ByteArray
    ) {
        
        fun isDnsRequest(): Boolean {
            return protocol == PROTOCOL_UDP && getDestinationPort() == DNS_PORT
        }
        
        fun isHttpTraffic(): Boolean {
            return protocol == PROTOCOL_TCP && 
                   (getDestinationPort() == HTTP_PORT || getDestinationPort() == HTTPS_PORT)
        }
        
        fun getDestinationPort(): Int {
            return if (payload.size >= 4) {
                val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                buffer.position(2) // Skip source port
                buffer.short.toInt() and 0xFFFF
            } else {
                0
            }
        }
        
        fun getSourcePort(): Int {
            return if (payload.size >= 2) {
                val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                buffer.short.toInt() and 0xFFFF
            } else {
                0
            }
        }
        
        fun getDnsQuery(): DnsQuery? {
            return if (isDnsRequest() && payload.size >= 8) {
                try {
                    // Skip UDP header (8 bytes) to get DNS data
                    val dnsData = payload.copyOfRange(8, payload.size)
                    parseDnsQuery(dnsData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing DNS query", e)
                    null
                }
            } else {
                null
            }
        }
        
        fun extractUrl(): String? {
            return if (isHttpTraffic()) {
                try {
                    // Skip TCP header and look for HTTP request
                    val tcpHeaderLength = ((payload[12].toInt() and 0xF0) shr 4) * 4
                    val httpData = payload.copyOfRange(tcpHeaderLength, payload.size)
                    
                    val httpString = String(httpData, Charsets.UTF_8)
                    extractUrlFromHttp(httpString)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting URL from HTTP traffic", e)
                    null
                }
            } else {
                null
            }
        }
        
        fun toByteArray(): ByteArray {
            try {
                val buffer = ByteBuffer.allocate(headerLength + payload.size).order(ByteOrder.BIG_ENDIAN)
                
                // Build IP header
                buffer.put(((version shl 4) or (headerLength / 4)).toByte())
                buffer.put(typeOfService.toByte())
                buffer.putShort((headerLength + payload.size).toShort())
                buffer.putShort(identification.toShort())
                buffer.putShort(flagsAndFragmentOffset.toShort())
                buffer.put(timeToLive.toByte())
                buffer.put(protocol.toByte())
                buffer.putShort(0) // Checksum will be calculated by system
                buffer.put(sourceAddress)
                buffer.put(destinationAddress)
                
                // Add padding if header length > 20
                while (buffer.position() < headerLength) {
                    buffer.put(0)
                }
                
                // Add payload
                buffer.put(payload)
                
                return buffer.array()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error converting IP packet to byte array", e)
                return originalData
            }
        }
        
        private fun parseDnsQuery(dnsData: ByteArray): DnsQuery? {
            return try {
                val buffer = ByteBuffer.wrap(dnsData).order(ByteOrder.BIG_ENDIAN)
                
                // Skip DNS header (12 bytes)
                buffer.position(12)
                
                // Parse question section
                val domain = parseDomainName(buffer, dnsData)
                
                DnsQuery(
                    domain = domain,
                    queryBytes = dnsData
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing DNS query", e)
                null
            }
        }
        
        private fun parseDomainName(buffer: ByteBuffer, fullData: ByteArray): String {
            val domainParts = mutableListOf<String>()
            
            while (buffer.hasRemaining()) {
                val length = buffer.get().toInt() and 0xFF
                
                if (length == 0) {
                    break // End of domain name
                }
                
                if ((length and 0xC0) == 0xC0) {
                    // Compression pointer - skip for simplicity
                    buffer.get() // Skip second byte
                    break
                }
                
                if (buffer.remaining() >= length) {
                    val labelBytes = ByteArray(length)
                    buffer.get(labelBytes)
                    domainParts.add(String(labelBytes, Charsets.UTF_8))
                } else {
                    break
                }
            }
            
            return domainParts.joinToString(".")
        }
        
        private fun extractUrlFromHttp(httpData: String): String? {
            return try {
                val lines = httpData.split("\r\n")
                val requestLine = lines.firstOrNull() ?: return null
                
                // Parse "GET /path HTTP/1.1"
                val parts = requestLine.split(" ")
                if (parts.size < 2) return null
                
                val path = parts[1]
                
                // Find Host header
                val hostLine = lines.find { it.startsWith("Host:", ignoreCase = true) }
                val host = hostLine?.substringAfter(":")?.trim() ?: return null
                
                // Determine protocol
                val protocol = if (getDestinationPort() == HTTPS_PORT) "https" else "http"
                
                "$protocol://$host$path"
                
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting URL from HTTP data", e)
                null
            }
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as IpPacket
            
            if (!originalData.contentEquals(other.originalData)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            return originalData.contentHashCode()
        }
    }
    
    data class DnsQuery(
        val domain: String,
        val queryBytes: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as DnsQuery
            
            if (domain != other.domain) return false
            if (!queryBytes.contentEquals(other.queryBytes)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = domain.hashCode()
            result = 31 * result + queryBytes.contentHashCode()
            return result
        }
    }
} 