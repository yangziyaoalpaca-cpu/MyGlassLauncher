package com.myglass.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(val name: String, val packageName: String, val icon: ImageBitmap)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlassLauncherScreen()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassLauncherScreen() {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        allApps = resolveInfos.map {
            AppInfo(
                name = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap().asImageBitmap()
            )
        }.sortedBy { it.name }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E)) 
    ) {
        // 每页放 20 个应用，所以总共是 (allApps.size + 19) / 20 页
        val pagerState = rememberPagerState(pageCount = { (allApps.size + 19) / 20 })
        
        HorizontalPager(
            state = pagerState, 
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val startIndex = page * 20
            val pageApps = allApps.drop(startIndex).take(20)
            
            // 🚀 性能暴增方案：使用纯 Column + Row 代替沉重的 LazyGrid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 将 20 个应用分成 5 行，每行 4 个
                for (row in 0 until 5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0 until 4) {
                            val index = row * 4 + col
                            if (index < pageApps.size) {
                                AppIcon(pageApps[index])
                            } else {
                                // 占位，保持排版整齐
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 底部 Dock 栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.85f)
                .height(80.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                allApps.take(4).forEach { app ->
                    DockAppIcon(app)
                }
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo) {
    val context = LocalContext.current
    val view = LocalView.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp) // 固定宽度，防止挤压
            .onGloballyPositioned { coordinates ->
                offset = coordinates.positionInWindow()
                size = coordinates.size
            }
            .clickable {
                launchAppWithAnimation(context, view, app, offset, size)
            }
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = app.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun DockAppIcon(app: AppInfo) {
    val context = LocalContext.current
    val view = LocalView.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Image(
        bitmap = app.icon,
        contentDescription = app.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(56.dp)
            .onGloballyPositioned { coordinates ->
                offset = coordinates.positionInWindow()
                size = coordinates.size
            }
            .clickable {
                launchAppWithAnimation(context, view, app, offset, size)
            }
    )
}

// 🚀 抽离出来的启动函数，确保带上 FLAG_ACTIVITY_NEW_TASK 并强制使用动画
private fun launchAppWithAnimation(
    context: android.content.Context,
    view: android.view.View,
    app: AppInfo,
    offset: Offset,
    size: IntSize
) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
        // 这个 Flag 是桌面启动第三方应用必须加的
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (intent != null) {
        try {
            val options = ActivityOptions.makeScaleUpAnimation(
                view,
                offset.x.toInt(),
                offset.y.toInt(),
                size.width,
                size.height
            ).toBundle()
            context.startActivity(intent, options)
        } catch (e: Exception) {
            // 如果系统拦截了动画导致崩溃，就退回到普通启动
            context.startActivity(intent)
        }
    }
}
