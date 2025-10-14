package com.example.carrotamap.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 底部导航按钮组件
 */
@Composable
fun NavigationButtons(
    onToggleOpenpilotCard: () -> Unit,
    onHelp: () -> Unit,
    onTutorial: () -> Unit
) {
    // 底部三个按钮布局 - 与上面按钮保持一致的风格
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp) // 与上面按钮保持一致的间距
    ) {
        // 左侧空位：原调试按钮已移除（调试入口迁移到左上角圆形速度控件）

        // 反馈按钮已移动到下方控制行中间（保持不变）
    }
}
