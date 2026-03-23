package com.example.averageprice

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class TransactionType { BUY, SELL }

data class Transaction(
    val id: Long = System.currentTimeMillis(),
    val price: Double,
    val quantity: Double,
    val type: TransactionType
)

data class PositionProfile(
    val name: String,
    val isShortMode: Boolean = false,
    val transactions: List<Transaction> = emptyList()
)

class PositionViewModel(application: Application) : AndroidViewModel(application) {
    private val dataFile = File(application.getExternalFilesDir(null), "position_data.json")
    
    var profiles = mutableStateMapOf<String, PositionProfile>()
    var currentProfileName by mutableStateOf("")

    // 当前活动状态
    val transactions = mutableStateListOf<Transaction>()
    var isShortMode by mutableStateOf(false)
    var currentPrice by mutableStateOf("")

    var totalQuantity by mutableStateOf(0.0)
    var averagePrice by mutableStateOf(0.0)
    var totalCost by mutableStateOf(0.0)

    init {
        loadAllDataFromDisk()
        
        // 恢复上一次的仓位，如果没有则创建默认
        if (profiles.isNotEmpty()) {
            loadProfileState(profiles.keys.first())
        } else {
            createNewProfile("默认仓位")
        }
    }

    // 仅加载状态到 UI，不保存（用于初始化和切换）
    private fun loadProfileState(name: String) {
        val target = profiles[name] ?: return
        currentProfileName = name
        isShortMode = target.isShortMode
        transactions.clear()
        transactions.addAll(target.transactions)
        currentPrice = ""
        calculatePosition()
    }

    fun createNewProfile(name: String) {
        if (name.isBlank() || profiles.containsKey(name)) return
        
        // 保存当前仓位
        saveCurrentToProfileMap()
        
        val newProfile = PositionProfile(name)
        profiles[name] = newProfile
        saveAllDataToDisk()
        
        loadProfileState(name)
    }

    fun switchProfile(name: String) {
        if (name == currentProfileName) return
        
        // 1. 先保存旧仓位数据
        saveCurrentToProfileMap()
        saveAllDataToDisk()
        
        // 2. 加载新仓位数据
        loadProfileState(name)
    }

    fun deleteCurrentProfile() {
        if (profiles.size <= 1) return
        val nameToDelete = currentProfileName
        profiles.remove(nameToDelete)
        
        val nextProfile = profiles.keys.first()
        loadProfileState(nextProfile)
        saveAllDataToDisk()
    }

    // 将当前 UI 上的状态同步到内存中的 profiles Map
    private fun saveCurrentToProfileMap() {
        if (currentProfileName.isNotEmpty()) {
            profiles[currentProfileName] = PositionProfile(
                name = currentProfileName,
                isShortMode = isShortMode,
                transactions = transactions.toList()
            )
        }
    }

    fun addTransaction(price: Double, quantity: Double, type: TransactionType) {
        val isReducing = if (isShortMode) type == TransactionType.BUY else type == TransactionType.SELL
        if (isReducing && quantity > totalQuantity) return
        
        transactions.add(Transaction(price = price, quantity = quantity, type = type))
        calculatePosition()
        
        // 变更后立即持久化
        saveCurrentToProfileMap()
        saveAllDataToDisk()
    }

    fun removeTransaction(transaction: Transaction) {
        transactions.remove(transaction)
        calculatePosition()
        saveCurrentToProfileMap()
        saveAllDataToDisk()
    }

    fun clearAllTransactions() {
        transactions.clear()
        calculatePosition()
        saveCurrentToProfileMap()
        saveAllDataToDisk()
    }

    fun toggleShortMode(enabled: Boolean) {
        isShortMode = enabled
        calculatePosition()
        
        saveCurrentToProfileMap()
        saveAllDataToDisk()
    }

    fun updateTransaction(transaction: Transaction, newPrice: Double, newQuantity: Double) {
        val index = transactions.indexOfFirst { it.id == transaction.id }
        if (index != -1) {
            val isReducing = if (isShortMode) transaction.type == TransactionType.BUY else transaction.type == TransactionType.SELL
            if (isReducing && newQuantity > totalQuantity) return
            
            transactions[index] = transaction.copy(price = newPrice, quantity = newQuantity)
            calculatePosition()
            saveCurrentToProfileMap()
            saveAllDataToDisk()
        }
    }

    private fun calculatePosition() {
        var runningQty = 0.0
        var runningCost = 0.0
        for (tx in transactions) {
            if (!isShortMode) {
                if (tx.type == TransactionType.BUY) {
                    runningCost += tx.price * tx.quantity
                    runningQty += tx.quantity
                } else {
                    runningCost -= tx.price * tx.quantity
                    runningQty -= tx.quantity
                }
            } else {
                if (tx.type == TransactionType.SELL) {
                    runningCost += tx.price * tx.quantity
                    runningQty += tx.quantity
                } else {
                    runningCost -= tx.price * tx.quantity
                    runningQty -= tx.quantity
                }
            }
        }
        totalQuantity = runningQty
        totalCost = runningCost
        averagePrice = if (runningQty > 0) runningCost / runningQty else 0.0
    }

    fun getCurrentProfit(): Double {
        val curPrice = currentPrice.toDoubleOrNull() ?: 0.0
        if (totalQuantity == 0.0) return 0.0
        return if (!isShortMode) (curPrice - averagePrice) * totalQuantity else (averagePrice - curPrice) * totalQuantity
    }

    fun getCurrentProfitPercent(): Double {
        if (averagePrice == 0.0) return 0.0
        val curPrice = currentPrice.toDoubleOrNull() ?: 0.0
        return if (!isShortMode) (curPrice - averagePrice) / averagePrice * 100.0 else (averagePrice - curPrice) / averagePrice * 100.0
    }

    // --- 磁盘持久化 ---
    private fun saveAllDataToDisk() {
        val jsonRoot = JSONObject()
        val profilesArray = JSONArray()
        profiles.values.forEach { profile ->
            val pObj = JSONObject()
            pObj.put("name", profile.name)
            pObj.put("isShort", profile.isShortMode)
            val txArray = JSONArray()
            profile.transactions.forEach { tx ->
                val tObj = JSONObject()
                tObj.put("p", tx.price)
                tObj.put("q", tx.quantity)
                tObj.put("t", tx.type.name)
                tObj.put("id", tx.id)
                txArray.put(tObj)
            }
            pObj.put("txs", txArray)
            profilesArray.put(pObj)
        }
        jsonRoot.put("profiles", profilesArray)
        
        try {
            // 确保目录存在
            dataFile.parentFile?.mkdirs()
            // 写入文件
            dataFile.writeText(jsonRoot.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAllDataFromDisk() {
        if (!dataFile.exists()) return
        try {
            val dataStr = dataFile.readText()
            val jsonRoot = JSONObject(dataStr)
            val profilesArray = jsonRoot.getJSONArray("profiles")
            for (i in 0 until profilesArray.length()) {
                val pObj = profilesArray.getJSONObject(i)
                val name = pObj.getString("name")
                val isShort = pObj.getBoolean("isShort")
                val txs = mutableListOf<Transaction>()
                val txArray = pObj.getJSONArray("txs")
                for (j in 0 until txArray.length()) {
                    val tObj = txArray.getJSONObject(j)
                    txs.add(Transaction(
                        id = tObj.getLong("id"),
                        price = tObj.getDouble("p"),
                        quantity = tObj.getDouble("q"),
                        type = TransactionType.valueOf(tObj.getString("t"))
                    ))
                }
                profiles[name] = PositionProfile(name, isShort, txs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
