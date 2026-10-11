package com.kallan.naistudio.platform

data class TaggerShareInfo(val address: String, val pairingCode: String)
interface TaggerSharing {
    suspend fun start(): TaggerShareInfo
    suspend fun stop()
    fun close()
}
