package com.myglass.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.ui.graphics.ImageBitmap
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
    
    // 记录当前是第几页
    var currentPage by remember { mutableStateOf(0) }
    val appsPerPage = 20 // 每页20个应用

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        
        allApps = resolveInfos.map {
            AppInfo(
                name = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap(width = 144, height = 144).asImageBitmap()
            )
        }.sortedBy { it.name }
    }

    // 计算总页数
    val totalPages = if (allApps.isEmpty()) 1 else (allApps.size + appsPerPage - 1) / appsPerPage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E1E2E), Color(0xFF14141B))
                )
            )
            // 🚀 核心：监听全屏左右滑动手势，实现翻页
            .pointerInput(totalPages) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    // 向左滑 (dragAmount < -80f) -> 去下一页
                    if (dragAmount < -80f && currentPage < totalPages - 1) {
                        currentPage++
                    } 
                    // 向右滑 (dragAmount > 80f) -> 去上一页
                    else if (dragAmount > 80f && currentPage > 0) {
                        currentPage--
                    }
                }
            }
    ) {
        // 根据 currentPage 截取当前页的 App
        val startIndex = currentPage * appsPerPage
        val pageApps = allApps.drop(startIndex).take(appsPerPage)

        // 🚀 动画：当页码改变时，让页面平滑地左右滑动切换
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState > initialState) {
                    // 向左滑，新页面从右侧进来
                    slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(tween(300))
                } else {
                    // 向右滑，新页面从左侧进来
                    slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut(tween(300))
                }
            },
            label = "page_anim",
            modifier = Modifier.fillMaxSize()
        ) { targetPage ->
            // 这里的 targetPage 就是动画执行过程中的当前页
            val animatedStartIndex = targetPage * appsPerPage
            val animatedPageApps = allApps.drop(animatedStartIndex).take(appsPerPage)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (row in 0 until 5) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0 until 4) {
                            val index = row * 4 + col
                            if (index < animatedPageApps.size) {
                                AnimatedAppIcon(animatedPageApps[index])
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 底部液态玻璃 Dock 栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.85f)
                .height(80.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
                .blur(16.dp)
                .clip(RoundedCornerShape(40.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                allApps.take(4).forEach { app -> AnimatedAppIcon(app, isDock = true) }
            }
        }
    }
}

@Composable
fun AnimatedAppIcon(app: AppInfo, isDock: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

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
                detectTapGestures(
                    onPress = {
                        scope.launch {
                            scale.animateTo(0.85f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
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

private fun launchAppWithAnimation(context: android.content.Context, view: android.view.View, app: AppInfo, offset: Offset, size: IntSize) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    if (intent != null) {
        try {
            val options = ActivityOptions.makeScaleUpAnimation(view, offset.x.toInt(), offset.y.toInt(), size.width, size.height).toBundle()
            context.startActivity(intent, options)
        } catch (e: Exception) {
            context.startActivity(intent)
        }
    }
}
