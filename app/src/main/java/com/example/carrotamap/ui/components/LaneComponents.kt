package com.example.carrotamap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.LaneInfo
import com.example.carrotamap.XiaogeVehicleData

/**
 * 车道图标映射工具
 */
object LaneIconHelper {
    // Lane Actions (aligned with Amap LaneAction)
    private const val ACTION_AHEAD = 0
    private const val ACTION_LEFT = 1
    private const val ACTION_RIGHT = 3
    private const val ACTION_LU_TURN = 5
    private const val ACTION_RU_TURN = 8

    // Lane Types (IDs)
    private const val LANE_TYPE_AHEAD_LEFT = 2
    private const val LANE_TYPE_AHEAD_RIGHT = 4
    private const val LANE_TYPE_LEFT_RIGHT = 6
    private const val LANE_TYPE_AHEAD_LEFT_RIGHT = 7
    private const val LANE_TYPE_AHEAD_LU_TURN = 9
    private const val LANE_TYPE_AHEAD_RU_TURN = 10
    private const val LANE_TYPE_LEFT_LU_TURN = 11
    private const val LANE_TYPE_RIGHT_RU_TURN = 12
    private const val LANE_TYPE_AHEAD_RIGHT_RU_TURN = 13
    private const val LANE_TYPE_LEFT_IN_LEFT_LU_TURN = 14
    private const val LANE_TYPE_AHEAD_LEFT_LU_TURN = 15
    private const val LANE_TYPE_LEFT_RU_TURN = 19
    private const val LANE_TYPE_BUS = 20
    private const val LANE_TYPE_VARIABLE = 21
    private const val LANE_TYPE_RIGHT_ONLY = 18
    private const val LANE_TYPE_AHEAD_ONLY_SPECIAL = 15
    private const val LANE_TYPE_AHEAD_RIGHT_SPECIAL = 32

    /**
     * Map Amap Navigation Icon (TBT) to Lane Action
     */
    private fun mapNaviIconToAction(naviIcon: Int): Int {
        return when (naviIcon) {
            2, 4 -> ACTION_LEFT
            3, 5 -> ACTION_RIGHT
            9 -> ACTION_AHEAD
            6 -> ACTION_LU_TURN
            7 -> ACTION_RU_TURN
            else -> ACTION_AHEAD
        }
    }

    /**
     * 根据高德图标 ID 和推荐状态获取资源 ID
     */
    fun getLaneIconResId(context: android.content.Context, iconId: String, isRecommended: Boolean, naviIcon: Int = -1): Int? {
        val res = context.resources
        val packageName = context.packageName
        
        fun isValidResId(id: Int): Boolean {
            return id != 0 && (id ushr 24) != 0
        }

        fun getValidIdentifier(name: String): Int {
            var id = res.getIdentifier("global_image_$name", "drawable", packageName)
            if (isValidResId(id)) return id
            
            id = res.getIdentifier("navistate_$name", "drawable", packageName)
            if (isValidResId(id)) return id
            
            id = res.getIdentifier(name, "drawable", packageName)
            if (isValidResId(id)) return id
            
            return 0
        }

        val laneType = iconId.toIntOrNull() ?: 0
        val hexId = Integer.toHexString(laneType)

        // Special Case: User corrections for specific IDs
        when (laneType) {
            LANE_TYPE_RIGHT_ONLY -> {
                val resId = getValidIdentifier("auto_landback_3")
                if (resId != 0) return resId
            }
            LANE_TYPE_AHEAD_ONLY_SPECIAL -> {
                val resId = getValidIdentifier("auto_landback_0")
                if (resId != 0) return resId
            }
            LANE_TYPE_AHEAD_RIGHT_SPECIAL -> {
                val resId = getValidIdentifier("landfront_40")
                if (resId != 0) return resId
            }
            89 -> {
                val resId = getValidIdentifier("fee")
                if (resId != 0) return resId
            }
            30 -> {
                val resId = getValidIdentifier("landfront_20")
                if (resId != 0) return resId
            }
            25, 31 -> {
                val resId = getValidIdentifier("landfront_21")
                if (resId != 0) return resId
            }
            43 -> {
                val resId = getValidIdentifier("landfront_b1")
                if (resId != 0) return resId
            }
            3 -> {
                val resId = getValidIdentifier("landback_3")
                if (resId != 0) return resId
            }
            16 -> {
                val resId = getValidIdentifier("auto_landback_1")
                if (resId != 0) return resId
            }
            11 -> {
                val resId = getValidIdentifier("landback_b")
                if (resId != 0) return resId
            }
            12 -> {
                val resId = getValidIdentifier("landfront_recommend_43")
                if (resId != 0) return resId
            }
            40 -> {
                val resId = getValidIdentifier("landfront_recommend_95")
                if (resId != 0) return resId
            }
            1 -> {
                val resId = getValidIdentifier("landback_1")
                if (resId != 0) return resId
            }
            0 -> {
                val resId = getValidIdentifier("landback_0")
                if (resId != 0) return resId
            }
            4 -> {
                val resId = getValidIdentifier("landback_4")
                if (resId != 0) return resId
            }
            54 -> {
                val resId = getValidIdentifier("landfront_15")
                if (resId != 0) return resId
            }
            5 -> {
                val resId = getValidIdentifier("landback_5")
                if (resId != 0) return resId
            }
            else -> {
                if (iconId == "3") {
                    val resId = getValidIdentifier("landback_3")
                    if (resId != 0) return resId
                }
            }
        }

        // Try Complex Lane Logic
        if (isRecommended) {
            val action = if (naviIcon != -1) mapNaviIconToAction(naviIcon) else ACTION_AHEAD
            val complexResName = getComplexLaneIcon(laneType, action)
            if (complexResName != null) {
                var resId = getValidIdentifier("auto_${complexResName.replace("landfront", "landback")}")
                if (resId != 0) return resId

                resId = getValidIdentifier(complexResName)
                if (resId != 0) return resId
            }
        }

        // Try Auto Series
        if (laneType >= 15) {
            val offsetId = laneType - 15
            val offsetHex = Integer.toHexString(offsetId)
            
            var resIdOffset = getValidIdentifier("auto_landback_$offsetId")
            if (resIdOffset != 0) return resIdOffset
            
            if (offsetHex != offsetId.toString()) {
                resIdOffset = getValidIdentifier("auto_landback_$offsetHex")
                if (resIdOffset != 0) return resIdOffset
            }
        }

        var resId = getValidIdentifier("auto_landback_$iconId")
        if (resId != 0) return resId

        if (hexId != iconId) {
            resId = getValidIdentifier("auto_landback_$hexId")
            if (resId != 0) return resId
        }

        // Fallback: Dynamic Lookup
        if (isRecommended) {
            resId = getValidIdentifier("landfront_$iconId")
            if (resId != 0) return resId
            
            if (hexId != iconId) {
                resId = getValidIdentifier("landfront_$hexId")
                if (resId != 0) return resId
            }
        }

        resId = getValidIdentifier("landback_$iconId")
        if (resId != 0) return resId

        if (hexId != iconId) {
            resId = getValidIdentifier("landback_$hexId")
            if (resId != 0) return resId
        }

        resId = getValidIdentifier(iconId)
        if (resId != 0) return resId

        return null
    }

    /**
     * Logic from DriveWayLinear.java complexGuide
     */
    private fun getComplexLaneIcon(laneType: Int, action: Int): String? {
        return when (laneType) {
            LANE_TYPE_AHEAD_RU_TURN -> when (action) {
                ACTION_AHEAD -> "landfront_a0"
                ACTION_RU_TURN -> "landfront_a8"
                else -> null
            }
            LANE_TYPE_AHEAD_LU_TURN -> when (action) {
                ACTION_AHEAD -> "landfront_90"
                ACTION_LU_TURN -> "landfront_95"
                else -> null
            }
            LANE_TYPE_AHEAD_LEFT -> when (action) {
                ACTION_AHEAD -> "landfront_20"
                ACTION_LEFT -> "landfront_21"
                else -> null
            }
            LANE_TYPE_AHEAD_RIGHT -> when (action) {
                ACTION_AHEAD -> "landfront_recommend_95"
                ACTION_RIGHT -> "landfront_recommend_43"
                else -> null
            }
            LANE_TYPE_LEFT_RIGHT -> when (action) {
                ACTION_LEFT -> "landfront_61"
                ACTION_RIGHT -> "landfront_63"
                else -> null
            }
            LANE_TYPE_AHEAD_LEFT_RIGHT -> when (action) {
                ACTION_AHEAD -> "landfront_70"
                ACTION_LEFT -> "landfront_71"
                ACTION_RIGHT -> "landfront_73"
                else -> null
            }
            LANE_TYPE_LEFT_LU_TURN -> when (action) {
                ACTION_LU_TURN -> "landfront_b5"
                ACTION_LEFT -> "landfront_b1"
                else -> null
            }
            LANE_TYPE_RIGHT_RU_TURN -> when (action) {
                ACTION_RU_TURN -> "landfront_c8"
                ACTION_RIGHT -> "landfront_c3"
                else -> null
            }
            LANE_TYPE_LEFT_IN_LEFT_LU_TURN -> when (action) {
                ACTION_LEFT -> "landfront_e1"
                ACTION_LU_TURN -> "landfront_e5"
                else -> null
            }
            LANE_TYPE_AHEAD_LEFT_LU_TURN -> when (action) {
                ACTION_AHEAD -> "landfront_f0"
                ACTION_LEFT -> "landfront_f1"
                ACTION_LU_TURN -> "landfront_f5"
                else -> null
            }
            LANE_TYPE_LEFT_RU_TURN -> when (action) {
                ACTION_LEFT -> "landfront_j1"
                ACTION_LU_TURN, ACTION_RU_TURN -> "landfront_j8"
                else -> null
            }
            LANE_TYPE_AHEAD_RIGHT_RU_TURN -> when (action) {
                ACTION_AHEAD -> "landfront_70"
                ACTION_RIGHT -> "landfront_73"
                ACTION_RU_TURN -> "landfront_c8"
                else -> null
            }
            LANE_TYPE_BUS -> "landfront_kk"
            LANE_TYPE_VARIABLE -> "landback_l"
            else -> null
        }
    }
}

/**
 * 车道定位计算结果
 */
data class LanePositionResult(
    val index: Int,
    val isAccurate: Boolean
)

/**
 * 车道信息显示组件
 */
@Composable
fun LaneInfoDisplay(
    laneInfoList: List<LaneInfo>,
    naviIcon: Int = -1,
    nextRoadNOAOrNot: Boolean = false,
    trafficLightCount: Int = 0,
    routeRemainTrafficLightNum: Int = 0,
    roadcate: Int = -1,
    xiaogeData: XiaogeVehicleData? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 计算车辆所在车道定位
    val lanePosition = remember(laneInfoList.size, xiaogeData?.modelV2?.meta, roadcate) {
        val meta = xiaogeData?.modelV2?.meta
        val laneCount = laneInfoList.size
        
        if (meta == null || laneCount == 0) null
        else {
            val dl = meta.distanceToRoadEdgeLeft
            val dr = meta.distanceToRoadEdgeRight
            val isHighway = roadcate == 0 || roadcate == 1
            val refWidth = 3.2f // 基准车道宽
            
            when {
                laneCount <= 1 -> {
                    LanePositionResult(0, true)
                }
                
                else -> {
                    // 使用路缘距离推断车道索引
                    val leftLanes = (dl / refWidth).toInt()
                    val rightLanes = (dr / refWidth).toInt()
                    
                    // 理想情况下 leftLanes + 1 + rightLanes == 实际物理车道数
                    // 但这里我们要映射到导航给出的 laneCount
                    val inferredIndex = leftLanes
                    
                    // 限制在有效范围内
                    val index = inferredIndex.coerceIn(0, laneCount - 1)
                    
                    // 置信度判断：如果路缘距离很近，置信度高
                    val isAccurate = dl < 2.0f || dr < 2.0f || isHighway
                    
                    LanePositionResult(index, isAccurate)
                }
            }
        }
    }

    val itemWidth = if (laneInfoList.size > 6) 24.dp else 30.dp
    val itemHeight = 30.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0091FF),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp, horizontal = 6.dp)
                .heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：NOA状态
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = "NOA",
                        color = if (nextRoadNOAOrNot) Color.Green else Color.LightGray,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            // 中间：车道信息
            Row(
                modifier = Modifier.weight(3f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (laneInfoList.isEmpty()) {
                    val meta = xiaogeData?.modelV2?.meta
                    val displayText = if (lanePosition != null && lanePosition.index >= 0) {
                        "在第 ${lanePosition.index + 1} 车道行驶"
                    } else if (meta != null) {
                        val dl = meta.distanceToRoadEdgeLeft
                        val dr = meta.distanceToRoadEdgeRight
                        val threshold = 3.2f
                        
                        when {
                            dl > threshold && dr > threshold -> "在中间车道行驶"
                            dl <= threshold && dr > threshold -> "在最左侧车道行驶"
                            dl > threshold && dr <= threshold -> "在最右侧车道行驶"
                            else -> "车道行驶中"
                        }
                    } else {
                        "无视觉车道信息数据"
                    }
                    
                    Text(
                        text = displayText,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    laneInfoList.forEachIndexed { index, lane ->
                        val resId = remember(lane.id, lane.isRecommended, naviIcon) {
                            LaneIconHelper.getLaneIconResId(context, lane.id, lane.isRecommended, naviIcon)
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = itemWidth, height = itemHeight)
                                    .then(
                                        if (lane.isRecommended) {
                                            Modifier
                                                .background(
                                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    width = 2.5.dp,
                                                    color = Color(0xFF10B981),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(2.dp)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (resId != null && resId != 0) {
                                    Image(
                                        painter = painterResource(id = resId),
                                        contentDescription = "Lane ${lane.id}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .alpha(if (lane.isRecommended) 1.0f else 0.6f),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = lane.id,
                                            color = if (lane.isRecommended) Color(0xFF10B981) else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // 动态定位标识
                            if (lanePosition != null && lanePosition.index == index) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Canvas(modifier = Modifier.size(width = itemWidth / 5, height = itemHeight / 8)) {
                                    val trianglePath = Path().apply {
                                        moveTo(size.width / 2f, 0f)
                                        lineTo(size.width, size.height)
                                        lineTo(0f, size.height)
                                        close()
                                    }
                                    drawPath(
                                        path = trianglePath,
                                        color = if (lanePosition.isAccurate) Color(0xFFFF0000) else Color(0xFFFFFF00)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp + (itemHeight / 8)))
                            }
                        }
                    }
                }
            }

            // 右侧：红绿灯信息
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🚦",
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${if (trafficLightCount >= 0) trafficLightCount else 0} / $routeRemainTrafficLightNum",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
