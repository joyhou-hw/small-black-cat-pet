package com.luoxiaohei.pet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import timber.log.Timber

/**
 * Application 入口：初始化日志、通知渠道
 */
class LuoxiaoheiApp : Application() {

    companion object {
        const val CHANNEL_PET = "channel_pet"
        const val CHANNEL_REMINDER = "channel_reminder"
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PET, getString(R.string.notification_channel_pet), NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDER, getString(R.string.notification_channel_reminder), NotificationManager.IMPORTANCE_HIGH)
            )
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("罗小黑 Application 初始化完成")
    }
}
