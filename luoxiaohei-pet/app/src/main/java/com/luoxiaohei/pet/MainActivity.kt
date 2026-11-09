package com.luoxiaohei.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luoxiaohei.pet.service.PetForegroundService
import com.luoxiaohei.pet.ui.theme.LuoxiaoheiPetTheme

/**
 * 主 Activity：设置页入口，同时提供启动/停止宠物Service的按钮
 *
 * M2：增加悬浮窗权限检查和引导
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LuoxiaoheiPetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStartPet = { startPetService() },
                        onStopPet = { stopPetService() }
                    )
                }
            }
        }
    }

    private fun startPetService() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // 引导用户开启悬浮窗权限
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        val intent = Intent(this, PetForegroundService::class.java)
        startForegroundService(intent)
    }

    private fun stopPetService() {
        stopService(Intent(this, PetForegroundService::class.java))
    }
}

@Composable
fun MainScreen(
    onStartPet: () -> Unit,
    onStopPet: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "罗小黑",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "住在手机里的猫妖",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStartPet, modifier = Modifier.fillMaxWidth()) {
            Text("召唤罗小黑")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onStopPet, modifier = Modifier.fillMaxWidth()) {
            Text("让罗小黑睡觉")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "点击「召唤罗小黑」后需要在设置中开启悬浮窗权限",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    LuoxiaoheiPetTheme {
        MainScreen(onStartPet = {}, onStopPet = {})
    }
}
