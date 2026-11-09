package com.luoxiaohei.pet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * 开机自启接收器
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("开机完成，启动宠物Service")
            PetForegroundService.start(context)
        }
    }
}
