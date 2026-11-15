package com.example.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun toVietnameseFirestoreError(exception: Throwable): String {
    val firestoreException = generateSequence(exception) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    return when (firestoreException?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Không đủ quyền truy cập dữ liệu này. Vui lòng kiểm tra phân quyền lớp học hoặc tài khoản."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Mất kết nối máy chủ. Thay đổi đã được lưu ngoại tuyến và sẽ tự đồng bộ khi có mạng."
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "Không tìm thấy dữ liệu yêu cầu."
        FirebaseFirestoreException.Code.ALREADY_EXISTS ->
            "Bản ghi đã tồn tại trong hệ thống."
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "Kết nối quá thời gian chờ. Vui lòng thử lại."
        else -> exception.localizedMessage ?: "Đã xảy ra lỗi khi xử lý dữ liệu Firestore."
    }
}
