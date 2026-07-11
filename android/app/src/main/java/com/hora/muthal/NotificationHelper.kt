package com.hora.muthal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.material.color.MaterialColors
import com.hora.muthal.ui.MainActivity
import com.hora.muthal.util.CurrencyHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {
    private const val CHANNEL_ID = "muthal_transaction_alerts"
    private const val TRANSACTION_NOTIFICATION_ID = 1001

    /**
     * Create the notification channel for transaction alerts.
     * Called once from MuthalApplication.onCreate().
     * (Android 8+ only — on older versions, this is a no-op.)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transaction Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for new income and expense entries"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200)  // Subtle tick
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Send a transaction alert notification when an entry is saved.
     * Follows Hora notifications design standard:
     * - Full-bleed background colour (setColorized + setColor)
     * - Monochrome Malayalam icon (ic_notification.xml, engine-generated, system-tinted)
     * - Clear text hierarchy (title bold, content secondary, subtext metadata)
     * - Vibration gated on haptics preference
     *
     * @param context The app context
     * @param amount The transaction amount
     * @param category The category name
     * @param type "income" or "expense"
     */
    fun notifyTransactionPosted(
        context: Context,
        amount: Double,
        category: String,
        type: String
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // Format the amount with currency
        val amountFormatted = CurrencyHelper.format(amount, "INR")

        // Build the title and body based on type
        val title = when (type.lowercase()) {
            "income" -> "Income posted: $amountFormatted"
            "expense" -> "Expense posted: $amountFormatted"
            else -> "Transaction posted: $amountFormatted"
        }
        val body = category

        // Format the time for the subtext
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedTime = timeFormat.format(Date())

        // Intent to open the app when the notification is tapped
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification per Hora standard
        // Get the dynamic Material You primary color via MaterialColors utility
        val primaryColor = MaterialColors.getColor(context, android.R.attr.colorPrimary, 0xFF1C1B1F.toInt())
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)  // engine-generated 24×24dp monochrome, system-tinted
            .setContentTitle(title)                          // Bold primary text
            .setContentText(body)                            // Secondary text
            .setSubText("Muthal • $formattedTime")          // Metadata: app name + time
            .setColor(primaryColor)                          // Primary theme colour (Material You dynamic)
            .setColorized(true)                              // Enable full-bleed background
            .setPriority(NotificationCompat.PRIORITY_HIGH)   // Expanded by default
            .setAutoCancel(true)                             // Dismiss on tap
            .setContentIntent(pendingIntent)                 // Launch app on tap
            .setVibrate(                                     // Vibration gated on haptics preference
                if (PreferenceHelper.isHapticsEnabled(context))
                    longArrayOf(0, 200)
                else
                    longArrayOf(0)
            )

        manager.notify(TRANSACTION_NOTIFICATION_ID, builder.build())
    }
}
