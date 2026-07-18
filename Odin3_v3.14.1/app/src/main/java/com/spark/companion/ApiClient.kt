package com.spark.companion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * تخزين بيانات الاقتران (عنوان الجهاز + التوكن) محليًا على الهاتف بعد أول اقتران ناجح،
 * حتى لا يحتاج المستخدم لإعادة إدخال رمز الـPIN كل مرة يفتح فيها التطبيق.
 */
object SessionStore {
    private const val PREFS = "spark_companion_session"

    fun save(context: Context, ip: String, port: Int, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ip", ip).putInt("port", port).putString("token", token).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun ip(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ip", null)

    fun port(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("port", 4489)

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null)

    fun isPaired(context: Context): Boolean = token(context) != null && ip(context) != null
}

/** نتيجة عامة لأي طلب شبكة: إما نجاح ببيانات، أو فشل برسالة واضحة بالعربية. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}

data class ProductItem(
    val id: String, val name: String, val barcode: String, val category: String,
    val quantity: Double, val minQty: Double, val unit: String,
    val costPrice: Double, val sellPrice: Double, val rollLength: Double, val rollPrice: Double,
)
data class InvoiceLineItem(val name: String, val unit: String, val qty: Double, val price: Double)
data class InvoiceSummaryItem(
    val id: String, val number: String, val customerName: String, val total: Double,
    val paymentMethod: String, val date: String, val items: List<InvoiceLineItem>,
)
data class DayPoint(val date: String, val total: Double)
data class LowStockRow(val name: String, val quantity: Double, val minQty: Double)
data class PaymentBreakdown(val cash: Double, val card: Double, val transfer: Double, val credit: Double)
data class DashboardSummary(
    val todaySalesTotal: Double, val todayInvoiceCount: Int, val todayProfit: Double,
    val yesterdaySalesTotal: Double, val salesChangePct: Double,
    val monthSalesTotal: Double, val monthInvoiceCount: Int, val monthProfit: Double,
    val inventoryValue: Double, val lowStockCount: Int,
    val paymentBreakdown: PaymentBreakdown, val last7Days: List<DayPoint>, val topLowStock: List<LowStockRow>,
)

class SparkApiClient(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * ينظّف عنوان الـIP المُدخل من المستخدم: يشيل "http://" لو موجودة بالغلط،
     * ويشيل أي منفذ (port) اتكتب معاه غصب داخل نفس الحقل (زي "192.168.1.5:4489")
     * حتى لا يتكرر المنفذ عند بناء الرابط النهائي ويسبب كراش.
     */
    private fun sanitizeIp(rawIp: String): String {
        var value = rawIp.trim()
        value = value.removePrefix("http://").removePrefix("https://")
        val slashIndex = value.indexOf('/')
        if (slashIndex != -1) value = value.substring(0, slashIndex)
        val colonIndex = value.indexOf(':')
        if (colonIndex != -1) value = value.substring(0, colonIndex)
        return value
    }

    private fun baseUrl(ip: String, port: Int) = "http://${sanitizeIp(ip)}:$port"

    /** يرسل رمز PIN المعروض على شاشة الكمبيوتر مع اسم الجهاز، ويحفظ التوكن محليًا عند النجاح. */
    suspend fun pair(ip: String, port: Int, pin: String, deviceName: String): ApiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val cleanIp = sanitizeIp(ip)
                val body = JSONObject().put("pin", pin).put("deviceName", deviceName)
                    .toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("${baseUrl(cleanIp, port)}/api/pair").post(body).build()
                client.newCall(req).execute().use { resp ->
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    if (resp.isSuccessful && json.optBoolean("ok")) {
                        val token = json.getString("token")
                        SessionStore.save(context, cleanIp, port, token)
                        ApiResult.Success(json.optString("shopName", ""))
                    } else {
                        ApiResult.Failure(json.optString("error", "تعذّر الاقتران"))
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure("تعذّر الوصول للكمبيوتر — تأكد إنك على نفس شبكة الواي فاي")
            } catch (e: IllegalArgumentException) {
                ApiResult.Failure("عنوان الـIP أو المنفذ غير صحيح، تأكد من كتابة رقم IP فقط بدون رموز إضافية")
            }
        }

    private suspend fun authedGet(path: String): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        val ip = SessionStore.ip(context) ?: return@withContext ApiResult.Failure("غير مقترن بعد")
        val port = SessionStore.port(context)
        val token = SessionStore.token(context) ?: return@withContext ApiResult.Failure("غير مقترن بعد")
        try {
            val req = Request.Builder().url("${baseUrl(ip, port)}$path")
                .addHeader("Authorization", "Bearer $token").build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                if (resp.code == 401) return@withContext ApiResult.Failure("انتهى الاقتران، أعد الربط من الإعدادات")
                if (!resp.isSuccessful) return@withContext ApiResult.Failure("خطأ في الاتصال (${resp.code})")
                ApiResult.Success(JSONObject(text))
            }
        } catch (e: IOException) {
            ApiResult.Failure("تعذّر الوصول للكمبيوتر — تأكد إنك على نفس شبكة الواي فاي")
        } catch (e: IllegalArgumentException) {
            ApiResult.Failure("عنوان الـIP أو المنفذ غير صحيح")
        }
    }

    suspend fun getSummary(): ApiResult<DashboardSummary> = when (val r = authedGet("/api/summary")) {
        is ApiResult.Success -> {
            val d = r.data
            val pb = d.optJSONObject("paymentBreakdown") ?: JSONObject()
            val last7 = d.optJSONArray("last7Days") ?: JSONArray()
            val last7List = (0 until last7.length()).map { i ->
                val o = last7.getJSONObject(i)
                DayPoint(o.optString("date"), o.optDouble("total", 0.0))
            }
            val topLow = d.optJSONArray("topLowStock") ?: JSONArray()
            val topLowList = (0 until topLow.length()).map { i ->
                val o = topLow.getJSONObject(i)
                LowStockRow(o.optString("name"), o.optDouble("quantity", 0.0), o.optDouble("minQty", 0.0))
            }
            ApiResult.Success(
                DashboardSummary(
                    todaySalesTotal = d.optDouble("todaySalesTotal", 0.0),
                    todayInvoiceCount = d.optInt("todayInvoiceCount", 0),
                    todayProfit = d.optDouble("todayProfit", 0.0),
                    yesterdaySalesTotal = d.optDouble("yesterdaySalesTotal", 0.0),
                    salesChangePct = d.optDouble("salesChangePct", 0.0),
                    monthSalesTotal = d.optDouble("monthSalesTotal", 0.0),
                    monthInvoiceCount = d.optInt("monthInvoiceCount", 0),
                    monthProfit = d.optDouble("monthProfit", 0.0),
                    inventoryValue = d.optDouble("inventoryValue", 0.0),
                    lowStockCount = d.optInt("lowStockCount", 0),
                    paymentBreakdown = PaymentBreakdown(
                        cash = pb.optDouble("cash", 0.0), card = pb.optDouble("card", 0.0),
                        transfer = pb.optDouble("transfer", 0.0), credit = pb.optDouble("credit", 0.0),
                    ),
                    last7Days = last7List, topLowStock = topLowList,
                )
            )
        }
        is ApiResult.Failure -> r
    }

    suspend fun getInventory(): ApiResult<List<ProductItem>> = when (val r = authedGet("/api/inventory")) {
        is ApiResult.Success -> {
            val arr: JSONArray = r.data.optJSONArray("items") ?: JSONArray()
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ProductItem(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    barcode = o.optString("barcode", ""),
                    category = o.optString("category", ""),
                    quantity = o.optDouble("quantity", 0.0),
                    minQty = o.optDouble("minQty", 0.0),
                    unit = o.optString("unit", ""),
                    costPrice = o.optDouble("costPrice", 0.0),
                    sellPrice = o.optDouble("sellPrice", 0.0),
                    rollLength = o.optDouble("rollLength", 0.0),
                    rollPrice = o.optDouble("rollPrice", 0.0),
                )
            }
            ApiResult.Success(list)
        }
        is ApiResult.Failure -> r
    }

    suspend fun getInvoices(): ApiResult<List<InvoiceSummaryItem>> = when (val r = authedGet("/api/invoices?limit=100")) {
        is ApiResult.Success -> {
            val arr: JSONArray = r.data.optJSONArray("invoices") ?: JSONArray()
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val lineItems = (0 until itemsArr.length()).map { j ->
                    val it = itemsArr.getJSONObject(j)
                    InvoiceLineItem(
                        name = it.optString("name"), unit = it.optString("unit", ""),
                        qty = it.optDouble("qty", 0.0), price = it.optDouble("price", 0.0),
                    )
                }
                InvoiceSummaryItem(
                    id = o.optString("id"),
                    number = o.optString("number"),
                    customerName = o.optString("customerName", "زبون نقدي"),
                    total = o.optDouble("total", 0.0),
                    paymentMethod = o.optString("paymentMethod", ""),
                    date = o.optString("date", ""),
                    items = lineItems,
                )
            }
            ApiResult.Success(list)
        }
        is ApiResult.Failure -> r
    }

    /** يحمّل PDF فاتورة معيّنة من الكمبيوتر مباشرة (بدون أي تدخل من المستخدم هناك) ويحفظه في مجلد التنزيلات بالهاتف. */
    suspend fun downloadInvoicePdf(invoiceId: String, suggestedFileName: String): ApiResult<android.net.Uri> =
        withContext(Dispatchers.IO) {
            val ip = SessionStore.ip(context) ?: return@withContext ApiResult.Failure("غير مقترن بعد")
            val port = SessionStore.port(context)
            val token = SessionStore.token(context) ?: return@withContext ApiResult.Failure("غير مقترن بعد")
            try {
                val longClient = client.newBuilder().readTimeout(15, TimeUnit.SECONDS).build()
                val req = Request.Builder().url("${baseUrl(ip, port)}/api/invoices/$invoiceId/pdf")
                    .addHeader("Authorization", "Bearer $token").build()
                longClient.newCall(req).execute().use { resp ->
                    if (resp.code == 401) return@withContext ApiResult.Failure("انتهى الاقتران، أعد الربط من الإعدادات")
                    if (resp.code == 503) {
                        val msg = try { JSONObject(resp.body?.string() ?: "{}").optString("error") } catch (e: Exception) { null }
                        return@withContext ApiResult.Failure(msg?.takeIf { it.isNotBlank() } ?: "افتح نظام سبارك على الكمبيوتر أولًا")
                    }
                    if (!resp.isSuccessful) return@withContext ApiResult.Failure("تعذّر تحميل الملف (${resp.code})")
                    val bytes = resp.body?.bytes() ?: return@withContext ApiResult.Failure("ملف فارغ")

                    val resolver = context.contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$suggestedFileName.pdf")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext ApiResult.Failure("تعذّر حفظ الملف على الهاتف")
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ApiResult.Success(uri)
                }
            } catch (e: IOException) {
                ApiResult.Failure("تعذّر الوصول للكمبيوتر — تأكد إنك على نفس شبكة الواي فاي")
            }
        }
}
