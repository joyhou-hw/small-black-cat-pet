package com.luoxiaohei.pet.domain.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * 提醒广播接收器：接收 AlarmManager 定时提醒
 * M1 占位，M5 阶段接入完整提醒逻辑
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        Timber.i("收到提醒广播: type=$type")
    }
}
