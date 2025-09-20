package com.stevedaydream.tenantapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService // 新增 import
import com.google.firebase.messaging.RemoteMessage // 新增 import
import com.stevedaydream.tenantapp.MainActivity
import com.stevedaydream.tenantapp.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"

    // 當 App 在前景時收到通知，會觸發此方法
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 可以在這裡 log 訊息，方便除錯
        Log.d(TAG, "From: ${remoteMessage.from}")

        // 檢查通知 payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Notification Message Body: ${it.body}")
            // 將收到的資料傳遞給 sendNotification 函式來建立並顯示通知
            sendNotification(it.title, it.body, remoteMessage.data)
        }
    }

    /**
     * 當 Firebase 分配新的 token 給這個裝置時會呼叫。
     * 您可以將此 token 送到您的後端伺服器，以便未來可以指定推播給此裝置。
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // 如果您有後端伺服器，可以在這裡將 token 送上去
        // sendRegistrationToServer(token)
    }

    private fun sendNotification(title: String?, messageBody: String?, data: Map<String, String>) {
        val landlordId = data["landlordId"]?.toIntOrNull()

        // 點擊通知後要開啟的 Activity
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // 清除在此之上的 Activity
            // 透過 intent 傳遞參數，讓 MainActivity 知道要導航到特定頁面
            if (landlordId != null) {
                putExtra("navigateTo", "room_change_approval")
                putExtra("landlordId", landlordId)
            }
        }

        // PendingIntent 讓系統可以在未來某個時間點，代表您的 App 執行這個 Intent
        val pendingIntent = PendingIntent.getActivity(
            this, 0 /* Request code */, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fcm_default_channel" // 通知渠道 ID
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 請確認您有這個圖示資源
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true) // 點擊後自動關閉通知
            .setContentIntent(pendingIntent) // 設定點擊後的行為
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 設定通知優先級

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0 (Oreo) 以上需要建立 Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 顯示通知
        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }
}