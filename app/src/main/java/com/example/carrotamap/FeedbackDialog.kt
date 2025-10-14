package com.example.carrotamap

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * 反馈弹窗组件
 * 提供用户反馈输入和图片上传功能
 */
@Composable
fun FeedbackDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSubmitFeedback: (String, List<Uri>?) -> Unit
) {
    if (!isVisible) return
    
    val scope = rememberCoroutineScope()
    
    // 状态变量
    var feedbackText by remember { mutableStateOf(TextFieldValue("")) }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        // 限制最多2张图片
        val newImages = uris.take(2)
        selectedImages = newImages
        Log.i("FeedbackDialog", "选择了 ${newImages.size} 张图片")
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Text(
                    text = "用户反馈",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 反馈内容输入框
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "反馈内容",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF424242),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        BasicTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = Color(0xFF212121)
                            ),
                            decorationBox = { innerTextField ->
                                if (feedbackText.text.isEmpty()) {
                                    Text(
                                        text = "请描述您遇到的问题或建议...",
                                        fontSize = 14.sp,
                                        color = Color(0xFF9E9E9E)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
                
                // 图片上传区域
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "图片附件 (最多2张)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF424242),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // 选择图片按钮
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加图片",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "选择图片",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        
                        // 显示已选择的图片
                        if (selectedImages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "已选择 ${selectedImages.size} 张图片:",
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                            
                            selectedImages.forEachIndexed { index, uri ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "图片 ${index + 1}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF424242),
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    IconButton(
                                        onClick = {
                                            selectedImages = selectedImages.toMutableList().apply {
                                                removeAt(index)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除图片",
                                            tint = Color(0xFFF44336),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 取消按钮
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9E9E9E)
                        )
                    ) {
                        Text(
                            text = "取消",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    
                    // 提交按钮
                    Button(
                        onClick = {
                            if (feedbackText.text.trim().isEmpty()) {
                                // 可以添加Toast提示
                                return@Button
                            }
                            
                            scope.launch {
                                isSubmitting = true
                                try {
                                    onSubmitFeedback(feedbackText.text.trim(), selectedImages.takeIf { it.isNotEmpty() })
                                    
                                    // 显示成功消息
                                    showSuccessMessage = true
                                    
                                    // 延迟关闭弹窗
                                    kotlinx.coroutines.delay(1500)
                                    onDismiss()
                                } catch (e: Exception) {
                                    Log.e("FeedbackDialog", "提交反馈失败: ${e.message}")
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        enabled = !isSubmitting && feedbackText.text.trim().isNotEmpty()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isSubmitting) "提交中..." else "提交反馈",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                
                // 成功提示
                if (showSuccessMessage) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "成功",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "反馈提交成功！",
                                color = Color(0xFF4CAF50),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
