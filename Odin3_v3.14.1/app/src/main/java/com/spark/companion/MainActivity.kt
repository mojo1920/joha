@file:OptIn(ExperimentalMaterial3Api::class)
package com.spark.companion


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/* ---------------------------------- هوية سبارك البصرية ----------------------------------
   نفس ألوان نظام الكمبيوتر بالظبط (C.accent/#FFB020 الذهبي، C.sidebar/#12161D الداكن)
   عشان تطبيق الموبايل يحس المستخدم إنه امتداد لنفس البرنامج مش تطبيق تاني. */
val SparkAccent = Color(0xFFFFB020)
val SparkAccentDark = Color(0xFFD9940F)
val SparkAccentSoft = Color(0xFFFFF3DC)
val SparkSidebar = Color(0xFF12161D)
val SparkDanger = Color(0xFFDC2626)
val SparkDangerSoft = Color(0xFFFDE9E9)
val SparkCash = Color(0xFF16A34A)
val SparkCredit = Color(0xFF7C3AED)
val SparkCreditSoft = Color(0xFFEFE8FE)
val SparkBg = Color(0xFFF2F3F6)

private val SparkColorScheme = lightColorScheme(
    primary = SparkAccentDark,
    onPrimary = Color.White,
    primaryContainer = SparkAccentSoft,
    onPrimaryContainer = SparkAccentDark,
    secondary = SparkSidebar,
    tertiary = SparkAccentDark,
    tertiaryContainer = SparkAccentSoft,
    onTertiaryContainer = SparkAccentDark,
    background = SparkBg,
    surface = Color.White,
    error = SparkDanger,
    errorContainer = SparkDangerSoft,
    onErrorContainer = SparkDanger,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = SparkColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = SparkColorScheme.background) {
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
                title = { Text("سبارك - متابعة", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SparkSidebar, titleContentColor = Color.White, actionIconContentColor = SparkAccent,
                ),
                actions = {
                    IconButton(onClick = onUnpair) {
                        Icon(Icons.Default.Logout, contentDescription = "إلغاء الاقتران")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = tab == CompanionTab.DASHBOARD,
                    onClick = { tab = CompanionTab.DASHBOARD },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("الرئيسية") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = SparkAccentDark, selectedTextColor = SparkAccentDark, indicatorColor = SparkAccentSoft)
                )
                NavigationBarItem(
                    selected = tab == CompanionTab.INVENTORY,
                    onClick = { tab = CompanionTab.INVENTORY },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("المخزون") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = SparkAccentDark, selectedTextColor = SparkAccentDark, indicatorColor = SparkAccentSoft)
                )
                NavigationBarItem(
                    selected = tab == CompanionTab.INVOICES,
                    onClick = { tab = CompanionTab.INVOICES },
                    icon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                    label = { Text("الفواتير") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = SparkAccentDark, selectedTextColor = SparkAccentDark, indicatorColor = SparkAccentSoft)
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
    var summary by remember { mutableStateOf<DashboardSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = SparkApiClient(context).getSummary()) {
            is ApiResult.Success -> summary = r.data
            is ApiResult.Failure -> error = r.message
        }
    }

    val payLabel = { m: String -> when (m) { "cash" -> "نقدًا"; "card" -> "بطاقة"; "transfer" -> "حوالة"; "credit" -> "آجل"; else -> m } }

    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        item {
            Text("ملخص اليوم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        summary?.let { s ->
            item {
                val changeColor = if (s.salesChangePct >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                val changeText = "${if (s.salesChangePct >= 0) "▲" else "▼"} ${"%.1f".format(kotlin.math.abs(s.salesChangePct))}% عن أمس"
                SummaryCard("إجمالي مبيعات اليوم", "%.2f".format(s.todaySalesTotal), subtitle = changeText, subtitleColor = changeColor)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { SummaryCard("عدد فواتير اليوم", s.todayInvoiceCount.toString()) }
                    Box(Modifier.weight(1f)) { SummaryCard("ربح اليوم", "%.2f".format(s.todayProfit)) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { SummaryCard("مبيعات الشهر", "%.2f".format(s.monthSalesTotal)) }
                    Box(Modifier.weight(1f)) { SummaryCard("ربح الشهر", "%.2f".format(s.monthProfit)) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { SummaryCard("قيمة المخزون", "%.2f".format(s.inventoryValue)) }
                    Box(Modifier.weight(1f)) { SummaryCard("أصناف تحت حد النواقص", s.lowStockCount.toString()) }
                }

                Spacer(Modifier.height(20.dp))
                Text("مبيعات آخر 7 أيام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val maxTotal = (s.last7Days.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(1.0)
                        s.last7Days.forEach { day ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(day.date.takeLast(5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
                                Box(
                                    Modifier
                                        .weight((day.total / maxTotal).toFloat().coerceIn(0.02f, 1f))
                                        .height(14.dp)
                                        .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("%.0f".format(day.total), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("توزيع مبيعات الشهر حسب طريقة الدفع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        listOf("cash" to s.paymentBreakdown.cash, "card" to s.paymentBreakdown.card,
                            "transfer" to s.paymentBreakdown.transfer, "credit" to s.paymentBreakdown.credit).forEach { (m, v) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(payLabel(m))
                                Text("%.2f".format(v), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (s.topLowStock.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text("أهم الأصناف الناقصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    s.topLowStock.forEach { row ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.name)
                                Text("${row.quantity} / حد ${row.minQty}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            if (summary == null && error == null) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String, subtitle: String? = null, subtitleColor: androidx.compose.ui.graphics.Color? = null) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

enum class StockFilter { ALL, LOW, OUT }

@Composable
fun InventoryScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var stockFilter by remember { mutableStateOf(StockFilter.ALL) }
    var category by remember { mutableStateOf("الكل") }

    LaunchedEffect(Unit) {
        when (val r = SparkApiClient(context).getInventory()) {
            is ApiResult.Success -> items = r.data
            is ApiResult.Failure -> error = r.message
        }
    }

    val categories = remember(items) { listOf("الكل") + items.map { it.category }.filter { it.isNotBlank() }.distinct() }

    val filtered = items.filter { p ->
        val matchesQuery = query.isBlank() || p.name.contains(query, ignoreCase = true) || p.barcode.contains(query, ignoreCase = true)
        val matchesCategory = category == "الكل" || p.category == category
        val matchesStock = when (stockFilter) {
            StockFilter.ALL -> true
            StockFilter.LOW -> p.quantity <= p.minQty && p.quantity > 0
            StockFilter.OUT -> p.quantity <= 0
        }
        matchesQuery && matchesCategory && matchesStock
    }

    Column(modifier = Modifier.fillMaxSize()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("بحث بالاسم أو الباركود") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = stockFilter == StockFilter.ALL, onClick = { stockFilter = StockFilter.ALL }, label = { Text("الكل") })
                }
                item {
                    FilterChip(selected = stockFilter == StockFilter.LOW, onClick = { stockFilter = StockFilter.LOW }, label = { Text("نواقص") })
                }
                item {
                    FilterChip(selected = stockFilter == StockFilter.OUT, onClick = { stockFilter = StockFilter.OUT }, label = { Text("نافذ") })
                }
                items(categories) { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                }
            }
        }

        LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
            items(filtered) { p ->
                val out = p.quantity <= 0
                val low = !out && p.quantity <= p.minQty
                val bg = when { out -> MaterialTheme.colorScheme.errorContainer; low -> MaterialTheme.colorScheme.tertiaryContainer; else -> MaterialTheme.colorScheme.surface }
                Card(
                    colors = CardDefaults.cardColors(containerColor = bg),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                if (p.category.isNotBlank()) Text(p.category, style = MaterialTheme.typography.bodySmall)
                                if (p.barcode.isNotBlank()) Text(p.barcode, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "${p.quantity} ${p.unit}",
                                color = if (out) MaterialTheme.colorScheme.error else if (low) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("سعر البيع: ${"%.2f".format(p.sellPrice)}", style = MaterialTheme.typography.bodySmall)
                            if (p.rollLength > 0) Text("اللفة: ${"%.2f".format(p.rollPrice)} (${p.rollLength} م)", style = MaterialTheme.typography.bodySmall)
                        }
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
    var query by remember { mutableStateOf("") }
    var paymentFilter by remember { mutableStateOf("الكل") }
    var expandedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = SparkApiClient(context).getInvoices()) {
            is ApiResult.Success -> invoices = r.data
            is ApiResult.Failure -> error = r.message
        }
    }

    val payLabel = { m: String -> when (m) { "cash" -> "نقدًا"; "card" -> "بطاقة"; "transfer" -> "حوالة"; "credit" -> "آجل"; else -> m } }
    val paymentOptions = listOf("الكل", "cash", "card", "transfer", "credit")

    val filtered = invoices.filter { inv ->
        val matchesQuery = query.isBlank() || inv.number.contains(query, ignoreCase = true) || inv.customerName.contains(query, ignoreCase = true)
        val matchesPayment = paymentFilter == "الكل" || inv.paymentMethod == paymentFilter
        matchesQuery && matchesPayment
    }

    Column(modifier = Modifier.fillMaxSize()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("بحث برقم الفاتورة أو اسم العميل") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(paymentOptions) { opt ->
                    FilterChip(selected = paymentFilter == opt, onClick = { paymentFilter = opt }, label = { Text(if (opt == "الكل") opt else payLabel(opt)) })
                }
            }
        }

        LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
            items(filtered) { inv ->
                val expanded = expandedId == inv.id
                var pdfState by remember(inv.id) { mutableStateOf<String?>(null) } // null=عادي، "loading"، "done"، أو رسالة خطأ
                val scope = rememberCoroutineScope()
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { expandedId = if (expanded) null else inv.id }) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("فاتورة رقم ${inv.number}", fontWeight = FontWeight.Bold)
                                Text(inv.customerName, style = MaterialTheme.typography.bodySmall)
                                Text(payLabel(inv.paymentMethod), style = MaterialTheme.typography.bodySmall,
                                    color = if (inv.paymentMethod == "credit") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("%.2f".format(inv.total), fontWeight = FontWeight.Bold)
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            inv.items.forEach { it ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${it.name} × ${it.qty} ${it.unit}", style = MaterialTheme.typography.bodySmall)
                                    Text("%.2f".format(it.price * it.qty), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    pdfState = "loading"
                                    scope.launch {
                                        when (val r = SparkApiClient(context).downloadInvoicePdf(inv.id, "فاتورة-${inv.number}")) {
                                            is ApiResult.Success -> pdfState = "done"
                                            is ApiResult.Failure -> pdfState = r.message
                                        }
                                    }
                                },
                                enabled = pdfState != "loading",
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (pdfState == "loading") {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("جارٍ التحميل من الكمبيوتر...")
                                } else {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("تحميل PDF إلى الهاتف")
                                }
                            }
                            when (pdfState) {
                                "done" -> Text("تم الحفظ في مجلد التنزيلات ✓", style = MaterialTheme.typography.bodySmall, color = SparkCash, modifier = Modifier.padding(top = 4.dp))
                                null, "loading" -> {}
                                else -> Text(pdfState ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
