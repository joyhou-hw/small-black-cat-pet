package com.luoxiaohei.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luoxiaohei.pet.data.config.PreferencesManager
import com.luoxiaohei.pet.data.config.SecureStorage
import com.luoxiaohei.pet.data.db.AppDatabase
import androidx.room.Room
import com.luoxiaohei.pet.service.PetForegroundService
import com.luoxiaohei.pet.ui.chat.ChatScreen
import com.luoxiaohei.pet.ui.chat.ChatViewModel
import com.luoxiaohei.pet.ui.chat.ChatViewModelFactory
import com.luoxiaohei.pet.ui.settings.SettingsScreen
import com.luoxiaohei.pet.ui.theme.LuoxiaoheiPetTheme

/**
 * 主 Activity：导航容器
 *
 * M3：整合主页/聊天/设置三个页面
 * - home: 主页（召唤/休眠/聊天入口/设置入口）
 * - chat: 聊天页
 * - settings: 设置页
 *
 * 监听 Service 双击宠物广播，自动跳转到聊天页
 */
class MainActivity : ComponentActivity() {

    /** 双击宠物广播接收器 */
    private val openChatReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PetForegroundService.ACTION_OPEN_CHAT) {
                // 导航到聊天页（通过 Compose State 触发）
                pendingNavigation = "chat"
            }
        }
    }

    /** 待执行的导航 */
    @Volatile
    private var pendingNavigation: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册广播
        registerReceiver(openChatReceiver, IntentFilter(PetForegroundService.ACTION_OPEN_CHAT))

        // 初始化配置管理器
        val preferences = PreferencesManager(this)
        val secureStorage = SecureStorage(this)
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "luoxiaohei.db")
            .fallbackToDestructiveMigration()
            .build()

        setContent {
            LuoxiaoheiPetTheme {
                val navController = rememberNavController()

                // 监听待执行导航
                LaunchedEffect(Unit) {
                    snapshotFlow { pendingNavigation }
                        .filterNotNull()
                        .collect { dest ->
                            pendingNavigation = null
                            navController.navigate(dest)
                        }
                }

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        MainScreen(
                            preferences = preferences,
                            onStartPet = { startPetService() },
                            onStopPet = { stopPetService() },
                            onChat = { navController.navigate("chat") },
                            onSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("chat") {
                        ChatScreen(
                            viewModel = viewModel(factory = ChatViewModelFactory(preferences, secureStorage, database)),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            preferences = preferences,
                            secureStorage = secureStorage,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(openChatReceiver)
        super.onDestroy()
    }

    private fun startPetService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
    preferences: PreferencesManager? = null,
    onStartPet: () -> Unit = {},
    onStopPet: () -> Unit = {},
    onChat: () -> Unit = {},
    onSettings: () -> Unit = {}
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
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onChat, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Chat, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("和罗小黑聊天")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("设置")
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
        MainScreen()
    }
}
