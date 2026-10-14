package com.example.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class CompilationNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_PROGRESS_ID = "compilation_progress_channel"
        const val CHANNEL_RESULT_ID = "compilation_result_channel"

        const val PROGRESS_NOTIFICATION_ID = 2024
        const val RESULT_NOTIFICATION_ID = 2025
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal para atualizações em tempo real (baixa prioridade para não emitir bips a cada etapa)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Progresso da Compilação",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Atualizações contínuas das etapas de compilação do APK"
                setShowBadge(false)
            }

            // Canal para resultado final (alta prioridade com som/alerta de conclusão)
            val resultChannel = NotificationChannel(
                CHANNEL_RESULT_ID,
                "Resultado da Compilação",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de sucesso ou falha final da compilação do APK"
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(resultChannel)
        }
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /**
     * Atualiza o progresso em tempo real da compilação (Etapas 1, 2, 3, 4).
     */
    fun updateProgress(
        fileName: String,
        stepNumber: Int,
        stepTitle: String,
        totalSteps: Int = 4,
        detail: String? = null
    ) {
        val cleanFileName = if (fileName.isNotBlank()) fileName else "Projeto.zip"
        val statusText = "Etapa $stepNumber de $totalSteps: $stepTitle"
        val fullDescription = if (!detail.isNullOrBlank()) "$statusText\n$detail" else statusText

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(cleanFileName)
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullDescription))
            .setContentIntent(createPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(totalSteps, stepNumber, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        try {
            notificationManager.notify(PROGRESS_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permissão pode ainda não ter sido concedida
        }
    }

    /**
     * Notificação final de sucesso. Cancela a barra de progresso em andamento e emite alerta final.
     */
    fun notifySuccess(fileName: String, artifactName: String = "APK") {
        // Remove IMEDIATAMENTE a notificação em andamento para não travar na etapa anterior
        cancelProgress()

        val cleanFileName = if (fileName.isNotBlank()) fileName else "Projeto.zip"
        val title = "Compilação Concluída!"
        val message = "O $artifactName de '$cleanFileName' está pronto para download."

        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("✓ $message Toque para abrir o app e instalar o APK."))
            .setContentIntent(createPendingIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        try {
            notificationManager.notify(RESULT_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * Notificação final de falha. Cancela a barra de progresso em andamento e emite alerta de erro.
     */
    fun notifyFailure(fileName: String, reason: String = "Erro na compilação") {
        // Remove IMEDIATAMENTE a notificação em andamento
        cancelProgress()

        val cleanFileName = if (fileName.isNotBlank()) fileName else "Projeto.zip"
        val title = "Falha na Compilação"
        val message = "Não foi possível compilar '$cleanFileName': $reason."

        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("✗ $message Toque para visualizar os logs de erro e tentar novamente."))
            .setContentIntent(createPendingIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        try {
            notificationManager.notify(RESULT_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * Cancela a notificação de progresso da compilação.
     */
    fun cancelProgress() {
        try {
            notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
        } catch (_: Exception) { }
    }

    /**
     * Cancela todas as notificações de compilação.
     */
    fun cancelAll() {
        try {
            notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
            notificationManager.cancel(RESULT_NOTIFICATION_ID)
        } catch (_: Exception) { }
    }

    fun cancelNotification() {
        cancelAll()
    }
}
