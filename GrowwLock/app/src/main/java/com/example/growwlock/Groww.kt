package com.example.growwlock

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val code: Int, message: String) : Exception(message)

/** Read-only Groww Trade API calls: get an access token, read the order list. Never places orders. */
object Groww {
    private const val BASE = "https://api.groww.in"
    private const val PAGE_SIZE = 25

    private fun call(method: String, path: String, headers: Map<String, String>, body: String? = null): String {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException(code, "HTTP $code ${text.take(150)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    fun token(apiKey: String, apiSecret: String): String {
        val body = JSONObject().put("key_type", "totp").put("totp", Totp.code(apiSecret)).toString()
        val text = call(
            "POST", "/v1/token/api/access",
            mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json",
                "Accept" to "application/json"
            ),
            body
        )
        val j = JSONObject(text)
        var t = j.optString("token")
        if (t.isEmpty()) t = j.optJSONObject("payload")?.optString("token") ?: ""
        if (t.isEmpty()) throw ApiException(0, "Login worked but no token in reply")
        return t
    }

    private fun orderPage(token: String, page: Int): List<JSONObject> {
        val text = call(
            "GET", "/v1/order/list?page=$page&page_size=$PAGE_SIZE",
            mapOf(
                "Authorization" to "Bearer $token",
                "Accept" to "application/json",
                "X-API-VERSION" to "1.0"
            )
        )
        val j = JSONObject(text)
        val payload = j.optJSONObject("payload") ?: j
        val arr = payload.optJSONArray("order_list") ?: return emptyList()
        val out = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
        return out
    }

    private fun fetchPages(token: String, first: Int): List<JSONObject> {
        val seen = LinkedHashMap<String, JSONObject>()
        for (page in first until first + 10) {
            val batch = try {
                orderPage(token, page)
            } catch (e: ApiException) {
                if (page == first && e.code == 400) return emptyList() else throw e
            }
            var added = 0
            for (o in batch) {
                val id = o.optString("groww_order_id")
                if (id.isNotEmpty() && seen.put(id, o) == null) added++
            }
            if (added == 0 || batch.size < PAGE_SIZE) break
        }
        return seen.values.toList()
    }

    /** The docs do not say if page numbers start at 0 or 1, so try 0 first and 1 if nothing came back. */
    fun allOrders(token: String): List<JSONObject> {
        var result = fetchPages(token, 0)
        if (result.isEmpty()) result = fetchPages(token, 1)
        return result
    }
}
