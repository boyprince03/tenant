package com.stevedaydream.tenantapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stevedaydream.tenantapp.MainActivity
import com.stevedaydream.tenantapp.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")

        remoteMessage.notification?.let {
            Log.d(TAG, "Notification Message Body: ${it.body}")
            sendNotification(it.title, it.body, remoteMessage.data)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
    }

    private fun sendNotification(title: String?, messageBody: String?, data: Map<String, String>) {
        val landlordId = data["landlordId"]
        val paymentId = data["paymentId"]
        val navigateTo = data["navigateTo"] ?: "landlord_home"

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // 【*** 核心修改：新增邏輯處理新的導航類型 ***】
            when (navigateTo) {
                "room_change_approval" -> {
                    putExtra("navigateTo", "room_change_approval")
                    putExtra("landlordId", landlordId)
                }
                "payment_approval" -> {
                    putExtra("navigateTo", "payment_approval")
                    putExtra("landlordId", landlordId)
                    putExtra("paymentId", paymentId)
                }
                else -> {
                    putExtra("navigateTo", navigateTo)
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fcm_default_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}