package com.myglass.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

// 数据类
data class AppInfo(val name: String, val packageName: String, val icon: ImageBitmap)

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
    var currentPage by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        
        // 🚀 核心优化 1：将图标降采样至 144x144，极大释放 GPU 压力
        allApps = resolveInfos.map {
            AppInfo(
                name = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap(width = 144, height = 144).asImageBitmap()
            )
        }.sortedBy { it.name }
    }

    val appsPerPage = 20
    val totalPages = (allApps.size + appsPerPage - 1) / appsPerPage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E))
            // 🚀 核心优化 2：放弃笨重的 Pager，使用原生手势滑动
            .pointerInput(totalPages) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount < -100f && currentPage < totalPages - 1) {
                        currentPage++
                    } else if (dragAmount > 100f && currentPage > 0) {
                        currentPage--
                    }
                }
            }
    ) {
        val pageApps = allApps.drop(currentPage * appsPerPage).take(appsPerPage)

        // 网格布局
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (row in 0 until 5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 0 until 4) {
                        val index = row * 4 + col
                        if (index < pageApps.size) {
                            AnimatedAppIcon(pageApps[index])
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 底部 Dock 栏（暂时保留半透明，下一版换液态玻璃）
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
                    AnimatedAppIcon(app, isDock = true)
                }
            }
        }
    }
}

// 🚀 核心优化 3：加入 iOS 风格的弹簧按压动画
@Composable
fun AnimatedAppIcon(app: AppInfo, isDock: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // 弹簧缩放动画状态
    val scale = remember { Animatable(1f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .onGloballyPositioned { coordinates ->
                offset = coordinates.positionInWindow()
                size = coordinates.size
            }
            .pointerInput(Unit) {
                // 监听按压手势，实现 iOS 的“弹一下”
                androidx.compose.foundation.gestures.detectTapGestures(
                    onPress = {
                        scope.launch {
                            scale.animateTo(0.8f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
                        }
                        val released = tryAwaitRelease()
                        scope.launch {
                            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
                        }
                        if (released) {
                            launchAppWithAnimation(context, view, app, offset, size)
                        }
                    }
                )
            }
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(56.dp)
        )
        if (!isDock) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = app.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
        }
    }
}

// 启动应用
private fun launchAppWithAnimation(
    context: android.content.Context,
    view: android.view.View,
    app: AppInfo,
    offset: Offset,
    size: IntSize
) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    if (intent != null) {
        try {
            // 尝试调用系统缩放动画
            val options = ActivityOptions.makeScaleUpAnimation(
                view,
                offset.x.toInt(),
                offset.y.toInt(),
                size.width,
                size.height
            ).toBundle()
            context.startActivity(intent, options)
        } catch (e: Exception) {
            context.startActivity(intent)
        }
    }
}
