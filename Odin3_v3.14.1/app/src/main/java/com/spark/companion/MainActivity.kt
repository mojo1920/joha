package com.spark.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SparkCompanionApp()
                }
            }
        }
    }
}

@Composable
fun SparkCompanionApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var paired by remember { mutableStateOf(SessionStore.isPaired(context)) }

    if (!paired) {
        PairingScreen(onPaired = { paired = true })
    } else {
        DashboardHost(onUnpair = {
            SessionStore.clear(context)
            paired = false
        })
    }
}

/* ---------------------------------- شاشة الاقتران ---------------------------------- */
@Composable
fun PairingScreen(onPaired: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("4489") }
    var pin by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(android.os.Build.MODEL ?: "هاتف أندرويد") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ربط بنظام سبارك", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "افتح الإعدادات ← تطبيق الأندرويد على شاشة الكمبيوتر، وأدخل عنوان الشبكة ورمز PIN الظاهر هناك. تأكد إن الهاتف على نفس شبكة الواي فاي.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("عنوان IP (مثال: 192.168.1.10)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("المنفذ (Port)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("رمز PIN المكوَّن من 6 أرقام") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = deviceName, onValueChange = { deviceName = it }, label = { Text("اسم هذا الجهاز (يظهر عندك بالكمبيوتر)") }, modifier = Modifier.fillMaxWidth())

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                error = null
                val portInt = port.toIntOrNull()
                if (ip.isBlank() || portInt == null || pin.length < 4) {
                    error = "تأكد من إدخال كل الحقول بشكل صحيح"
                    return@Button
                }
                loading = true
                scope.launch {
                    val result = SparkApiClient(context).pair(ip.trim(), portInt, pin.trim(), deviceName.trim())
                    loading = false
                    when (result) {
                        is ApiResult.Success -> onPaired()
                        is ApiResult.Failure -> error = result.message
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "جارٍ الاتصال..." else "اقتران")
        }
    }
}

/* ---------------------------------- الشاشة الرئيسية بعد الاقتران ---------------------------------- */
enum class CompanionTab { DASHBOARD, INVENTORY, INVOICES }

@Composable
fun DashboardHost(onUnpair: () -> Unit) {
    var tab by remember { mutableStateOf(CompanionTab.DASHBOARD) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سبارك - متابعة") },
                actions = {
                    IconButton(onClick = onUnpair) {
                        Icon(Icons.Default.Logout, contentDescription = "إلغاء الاقتران")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == CompanionTab.DASHBOARD,
                    onClick = { tab = CompanionTab.DASHBOARD },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("الرئيسية") }
                )
                NavigationBarItem(
                    selected = tab == CompanionTab.INVENTORY,
                    onClick = { tab = CompanionTab.INVENTORY },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("المخزون") }
                )
                NavigationBarItem(
                    selected = tab == CompanionTab.INVOICES,
                    onClick = { tab = CompanionTab.INVOICES },
                    icon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                    label = { Text("الفواتير") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                CompanionTab.DASHBOARD -> DashboardScreen()
                CompanionTab.INVENTORY -> InventoryScreen()
                CompanionTab.INVOICES -> InvoicesScreen()
            }
        }
    }
}

@Composable
fun DashboardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var summary by remember { mutableStateOf<TodaySummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = SparkApiClient(context).getSummary()) {
            is ApiResult.Success -> summary = r.data
            is ApiResult.Failure -> error = r.message
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("ملخص اليوم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        summary?.let { s ->
            SummaryCard("عدد الفواتير اليوم", s.invoiceCount.toString())
            Spacer(Modifier.height(12.dp))
            SummaryCard("إجمالي المبيعات اليوم", "%.2f".format(s.salesTotal))
            Spacer(Modifier.height(12.dp))
            SummaryCard("أصناف تحت حد النواقص", s.lowStockCount.toString())
        }
        if (summary == null && error == null) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InventoryScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = SparkApiClient(context).getInventory()) {
            is ApiResult.Success -> items = r.data
            is ApiResult.Failure -> error = r.message
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
            items(items) { p ->
                val low = p.quantity <= p.minQty
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text("سعر البيع: ${"%.2f".format(p.sellPrice)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${p.quantity} ${p.unit}",
                            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InvoicesScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var invoices by remember { mutableStateOf<List<InvoiceSummaryItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = SparkApiClient(context).getInvoices()) {
            is ApiResult.Success -> invoices = r.data
            is ApiResult.Failure -> error = r.message
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
            items(invoices) { inv ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("فاتورة رقم ${inv.number}", fontWeight = FontWeight.Bold)
                            Text(inv.customerName, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("%.2f".format(inv.total), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
