package com.example.carrotamap

import org.junit.Test
import org.junit.Assert.*

/**
 * CarrotAmap 核心功能单元测试
 * 测试关键的数据处理和转换功能
 */
class CarrotAmapUnitTest {

    @Test
    fun carrotManFields_initialization_isCorrect() {
        val fields = CarrotManFields()

        // 测试默认值
        assertEquals(30, fields.nRoadLimitSpeed)
        assertEquals(0, fields.active_carrot)
        assertEquals(false, fields.isNavigating)
        assertEquals("good", fields.dataQuality)
    }

    @Test
    fun validateDestination_validCoordinates_returnsTrue() {
        // 测试有效的中国坐标
        val result = validateDestination(116.397128, 39.916527, "北京天安门")
        assertTrue("有效的北京坐标应该返回true", result)
    }

    @Test
    fun validateDestination_invalidCoordinates_returnsFalse() {
        // 测试无效坐标
        val result1 = validateDestination(0.0, 0.0, "无效坐标")
        assertFalse("零坐标应该返回false", result1)

        val result2 = validateDestination(200.0, 100.0, "超出范围")
        assertFalse("超出中国范围的坐标应该返回false", result2)

        val result3 = validateDestination(116.397128, 39.916527, "")
        assertFalse("空名称应该返回false", result3)
    }

    @Test
    fun haversineDistance_calculation_isAccurate() {
        // 测试北京到上海的距离计算
        val beijingLat = 39.916527
        val beijingLon = 116.397128
        val shanghaiLat = 31.230416
        val shanghaiLon = 121.473701

        val distance = haversineDistance(beijingLat, beijingLon, shanghaiLat, shanghaiLon)

        // 北京到上海大约1067公里，允许50公里误差
        assertTrue("北京到上海距离应该在1000-1150公里之间",
                   distance > 1000000 && distance < 1150000)
    }

    @Test
    fun shouldUpdateDestination_differentNames_returnsTrue() {
        val result = shouldUpdateDestination(
            116.0, 39.0, "旧目的地",
            116.0, 39.0, "新目的地"
        )
        assertTrue("不同名称应该触发更新", result)
    }

    @Test
    fun shouldUpdateDestination_sameLocationAndName_returnsFalse() {
        val result = shouldUpdateDestination(
            116.0, 39.0, "相同目的地",
            116.0, 39.0, "相同目的地"
        )
        assertFalse("相同位置和名称不应该触发更新", result)
    }
}