package com.example.averageprice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.averageprice.ui.theme.DivinationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PositionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DivinationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        PositionCalculatorScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PositionCalculatorScreen(viewModel: PositionViewModel, modifier: Modifier = Modifier) {
    var priceInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editPriceInput by remember { mutableStateOf("") }
    var editQuantityInput by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteTransactionConfirm by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var newProfileName by remember { mutableStateOf("") }
    var expandedMenu by remember { mutableStateOf(false) }
    var isInputSectionExpanded by remember { mutableStateOf(false) }
    
    val isDark = isSystemInDarkTheme()

    // 盈亏计算逻辑
    val profitInfo by remember(viewModel.currentPrice, viewModel.averagePrice, viewModel.totalQuantity, viewModel.isShortMode) {
        derivedStateOf {
            val p = viewModel.getCurrentProfit()
            val pct = viewModel.getCurrentProfitPercent()
            Pair(p, pct)
        }
    }

    val softGreen = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val softRed = if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
    val cardBg = if (isDark) Color(0xFF2C2C2C) else Color(0xFFF8F9FA)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // --- 顶部状态栏 ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "持仓助手", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { expandedMenu = true }.padding(vertical = 4.dp)
                        ) {
                            Text(text = viewModel.currentProfileName.ifEmpty { "选择仓位" }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                            viewModel.profiles.keys.forEach { name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { viewModel.switchProfile(name); expandedMenu = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("新建仓位", color = MaterialTheme.colorScheme.primary) },
                                leadingIcon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { showAddDialog = true; expandedMenu = false }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp)
                ) {
                    Text(if (viewModel.isShortMode) "做空" else "做多", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Switch(checked = viewModel.isShortMode, onCheckedChange = { viewModel.toggleShortMode(it) }, modifier = Modifier.scale(0.5f))
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // --- 数据仪表盘卡片 ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).shadow(2.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val (profit, profitPercent) = profitInfo
                    val profitColor = if (profit >= 0) softGreen else softRed
                    
                    Text("当前估值盈亏", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = (if (profit >= 0) "+" else "") + "%.2f".format(profit), fontSize = 32.sp, fontWeight = FontWeight.Black, color = profitColor)
                        Text(text = "%.2f%%".format(profitPercent), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = profitColor, modifier = Modifier.padding(bottom = 4.dp))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                    Row(modifier = Modifier.fillMaxWidth()) {
                        DashboardItem("持仓均价", "%.4f".format(viewModel.averagePrice), Modifier.weight(1f))
                        DashboardItem("持有数量", "%.2f".format(viewModel.totalQuantity), Modifier.weight(1f))
                        DashboardItem("投入成本", "%.2f".format(viewModel.totalCost), Modifier.weight(1f))
                    }


                }
            }
        }

        // --- 历史流水标题 ---
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(text = " 交易历史流水记录", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Row {
                    TextButton(onClick = { isInputSectionExpanded = !isInputSectionExpanded }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(
                            if (isInputSectionExpanded) "收起操作" else "展开操作",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { showClearConfirm = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("清空列表", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        // --- 操作面板 ---
        if (isInputSectionExpanded) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("交易操作", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        
                        // 输入区域和按钮
                        Column {
                            // 输入市价计算即时盈亏
                            TextField(
                                value = viewModel.currentPrice,
                                onValueChange = { viewModel.currentPrice = it },
                                placeholder = { Text("输入市价计算即时盈亏", fontSize = 13.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = priceInput, 
                                    onValueChange = { priceInput = it }, 
                                    label = { Text("单价") }, 
                                    modifier = Modifier.weight(1f), 
                                    singleLine = true, 
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = TextStyle(fontSize = 15.sp)
                                )
                                OutlinedTextField(
                                    value = quantityInput, 
                                    onValueChange = { quantityInput = it }, 
                                    label = { Text("数量") }, 
                                    modifier = Modifier.weight(1f), 
                                    singleLine = true, 
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = TextStyle(fontSize = 15.sp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val isShort = viewModel.isShortMode
                                ActionButton(label = if (isShort) "买入平仓" else "加仓", color = if (isShort) softRed else softGreen, modifier = Modifier.weight(1f)) {
                                    val p = priceInput.toDoubleOrNull(); val q = quantityInput.toDoubleOrNull()
                                    if (p != null && q != null) { viewModel.addTransaction(p, q, TransactionType.BUY); priceInput = ""; quantityInput = "" }
                                }
                                ActionButton(label = if (isShort) "卖出开仓" else "减仓", color = if (isShort) softGreen else softRed, modifier = Modifier.weight(1f)) {
                                    val p = priceInput.toDoubleOrNull(); val q = quantityInput.toDoubleOrNull()
                                    if (p != null && q != null) { viewModel.addTransaction(p, q, TransactionType.SELL); priceInput = ""; quantityInput = "" }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.transactions.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text("暂无流水记录", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                }
            }
        } else {
            items(items = viewModel.transactions.asReversed(), key = { it.id }) { tx ->
                TransactionEntry(
                    tx = tx, 
                    isDark = isDark, 
                    isShortMode = viewModel.isShortMode, 
                    onDelete = {
                        transactionToDelete = tx
                        showDeleteTransactionConfirm = true
                    },
                    onEdit = {
                        editingTransaction = tx
                        editPriceInput = tx.price.toString()
                        editQuantityInput = tx.quantity.toString()
                        showEditDialog = true
                    }
                )
            }
        }
    }

    // --- 弹窗 ---
    if (showAddDialog) {
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("新建档案") },
            text = { OutlinedTextField(value = newProfileName, onValueChange = { newProfileName = it }, label = { Text("仓位名称") }, singleLine = true) },
            confirmButton = { Button(onClick = { if (newProfileName.isNotBlank()) { viewModel.createNewProfile(newProfileName); newProfileName = ""; showAddDialog = false } }) { Text("创建") } })
    }
    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = { Text("删除确认") },
            text = { Text("确定要删除仓位 \"${viewModel.currentProfileName}\" 及其所有数据吗？") },
            confirmButton = { Button(onClick = { viewModel.deleteCurrentProfile(); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } })
    }
    
    if (showEditDialog) {
        val tx = editingTransaction
        if (tx != null) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("编辑交易记录") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editPriceInput, 
                            onValueChange = { editPriceInput = it }, 
                            label = { Text("单价") }, 
                            modifier = Modifier.fillMaxWidth(), 
                            singleLine = true, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editQuantityInput, 
                            onValueChange = { editQuantityInput = it }, 
                            label = { Text("数量") }, 
                            modifier = Modifier.fillMaxWidth(), 
                            singleLine = true, 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                },
                confirmButton = { 
                    Button(onClick = {
                        val newPrice = editPriceInput.toDoubleOrNull()
                        val newQuantity = editQuantityInput.toDoubleOrNull()
                        if (newPrice != null && newQuantity != null) {
                            viewModel.updateTransaction(tx, newPrice, newQuantity)
                            showEditDialog = false
                        }
                    }) { 
                        Text("保存") 
                    } 
                },
                dismissButton = { 
                    TextButton(onClick = { showEditDialog = false }) { 
                        Text("取消") 
                    } 
                }
            )
        }
    }
    
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空确认") },
            text = { Text("确定要清空所有交易记录吗？此操作不可恢复。") },
            confirmButton = { 
                Button(
                    onClick = {
                        viewModel.clearAllTransactions()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text("确认清空") 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { showClearConfirm = false }) { 
                    Text("取消") 
                } 
            }
        )
    }
    
    if (showDeleteTransactionConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteTransactionConfirm = false },
            title = { Text("删除确认") },
            text = { Text("确定要删除这条交易记录吗？") },
            confirmButton = { 
                Button(
                    onClick = {
                        transactionToDelete?.let { viewModel.removeTransaction(it) }
                        showDeleteTransactionConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text("确认删除") 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { showDeleteTransactionConfirm = false }) { 
                    Text("取消") 
                } 
            }
        )
    }
}

@Composable
fun DashboardItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ActionButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun TransactionEntry(tx: Transaction, isDark: Boolean, isShortMode: Boolean, onDelete: () -> Unit, onEdit: () -> Unit) {
    val isBuy = tx.type == TransactionType.BUY
    val isOpening = if (isShortMode) !isBuy else isBuy
    val accentColor = if (isOpening) (if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)) 
                      else (if (isDark) Color(0xFFE57373) else Color(0xFFC62828))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onEdit() }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(4.dp, 24.dp).clip(RoundedCornerShape(2.dp)).background(accentColor))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val label = when {
                isShortMode && isBuy -> "买入平仓"
                isShortMode && !isBuy -> "卖出开仓"
                !isShortMode && isBuy -> "买入加仓"
                else -> "卖出减仓"
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text("单价: ${tx.price}  |  数量: ${tx.quantity}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
    }
}
