package com.myglass.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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

// 提取自作者的着色器，带真实折射与边缘光
private const val REFRACTION_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float2 offset;
    uniform float4 cornerRadii;
    uniform float refractionHeight;
    uniform float refractionAmount;
    uniform float depthEffect;

    float radiusAt(float2 coord, float4 radii) {
        if (coord.x >= 0.0) {
            if (coord.y <= 0.0) return radii.y;
            else return radii.z;
        } else {
            if (coord.y <= 0.0) return radii.x;
            else return radii.w;
        }
    }

    float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
        float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
        float outside = length(max(cornerCoord, 0.0)) - radius;
        float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
        return outside + inside;
    }

    float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
        float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
        if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
            return sign(coord) * normalize(max(cornerCoord, 0.0));
        } else {
            float gradX = step(cornerCoord.y, cornerCoord.x);
            return sign(coord) * float2(gradX, 1.0 - gradX);
        }
    }

    float circleMap(float x) {
        return 1.0 - sqrt(1.0 - x * x);
    }

    half4 main(float2 coord) {
        float2 halfSize = size * 0.5;
        float2 centeredCoord = (coord + offset) - halfSize;
        float radius = radiusAt(coord, cornerRadii);
        
        float sd = sdRoundedRect(centeredCoord, halfSize, radius);
        if (-sd >= refractionHeight) {
            return content.eval(coord);
        }
        sd = min(sd, 0.0);
        
        float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
        float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
        
        float2 refractedCoord = coord + d * grad;
        return content.eval(refractedCoord);
    }
""".trimIndent()

@Composable
fun GlassLauncherScreen() {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    
    // 捕获整个屏幕的背景，用于底部的折射渲染
    val backgroundLayer = rememberGraphicsLayer()
    var dockSize by remember { mutableStateOf(IntSize.Zero) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 先记录背景
            .graphicsLayer { alpha = 0.99f } 
            .background(Color(0xFF1E1E2E))
    ) {
        // 背景层，加入一些彩色光晕，以凸显玻璃折射感
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    backgroundLayer.record { this@graphicsLayer.drawContent() }
                    drawLayer(backgroundLayer)
                }
        ) {
            Box(Modifier.size(200.dp).offset(50.dp, 100.dp).background(Color(0xFF6200EE).copy(alpha=0.4f), RoundedCornerShape(100.dp)))
            Box(Modifier.size(250.dp).offset(200.dp, 400.dp).background(Color(0xFF03DAC5).copy(alpha=0.3f), RoundedCornerShape(150.dp)))
            Box(Modifier.size(150.dp).offset(100.dp, 700.dp).background(Color(0xFFBB86FC).copy(alpha=0.4f), RoundedCornerShape(80.dp)))
            
            // App 网格
            val pageApps = allApps.take(20)
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 60.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (row in 0 until 5) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0 until 4) {
                            val index = row * 4 + col
                            if (index < pageApps.size) AnimatedAppIcon(pageApps[index]) else Spacer(modifier = Modifier.weight(1f))
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
                .onGloballyPositioned { dockSize = it.size }
                .graphicsLayer {
                    // 如果系统支持（Android 13+），则使用 RuntimeShader 渲染真液态玻璃
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && dockSize.width > 0) {
                        val shader = RuntimeShader(REFRACTION_SHADER)
                        shader.setFloatUniform("size", size.width, size.height)
                        shader.setFloatUniform("offset", 0f, 0f)
                        // 设置四个角的圆角半径
                        shader.setFloatUniform("cornerRadii", 80f, 80f, 80f, 80f)
                        // 折射高度和强度（数值越大，边缘水滴感越强）
                        shader.setFloatUniform("refractionHeight", 30f)
                        shader.setFloatUniform("refractionAmount", 40f)
                        shader.setFloatUniform("depthEffect", 1f)
                        
                        // 将背景层作为 shader 的采样源
                        renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                    } else {
                        // 低版本安卓降级为半透明+模糊
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                    }
                    // 设置背景裁切与混合
                    clip = true
                    shape = RoundedCornerShape(40.dp)
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
                .background(Color.White.copy(alpha = 0.1f)) // 给一点基础底色
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

// 动画控制器
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
                        // 按下缩小
                        scope.launch {
                            scale.animateTo(0.85f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
                        }
                        val released = tryAwaitRelease()
                        // 松开回弹
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
