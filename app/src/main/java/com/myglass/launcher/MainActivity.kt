package com.myglass.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 定义一个 App 数据类，用来存名字、包名和图标
data class AppInfo(val name: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlassLauncherScreen()
        }
    }
}

@Composable
fun GlassLauncherScreen() {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // 启动时获取手机里所有安装的 App
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        allApps = resolveInfos.map {
            AppInfo(
                name = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm)
            )
        }.sortedBy { it.name }
    }

    // 桌面背景（深色背景，为了衬托出玻璃效果）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E))
    ) {
        // 应用网格分页 (每页最多 20 个 App)
        val pagerState = rememberPagerState(pageCount = { (allApps.size + 19) / 20 })
        
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val startIndex = page * 20
            val pageApps = allApps.drop(startIndex).take(20)
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().padding(top = 60.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(pageApps) { app ->
                    AppIcon(app)
                }
            }
        }

        // 底部液态玻璃 Dock 栏（先用半透明+高斯模糊模拟）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.85f)
                .height(80.dp)
                .blur(20.dp) 
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Liquid Glass Dock", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        }
    }
}

@Composable
fun AppIcon(app: AppInfo) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .clickable {
                // 点击图标启动应用
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                }
            }
    ) {
        // 将安卓 Drawable 转成 Compose 可用的 Image
        Image(
            painter = BitmapPainter((app.icon as android.graphics.drawable.BitmapDrawable).bitmap.asImageBitmap()),
            contentDescription = app.name,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = app.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}
