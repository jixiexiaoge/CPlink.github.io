package com.example.carrotamap

/**
 * 车辆信息数据类
 * 包含厂商、车型和指纹信息
 */
data class VehicleInfo(
    val manufacturer: String,  // 厂商
    val model: String,        // 车型
    val fingerprint: String    // 指纹
)

/**
 * 厂商信息数据类
 */
data class Manufacturer(
    val name: String,         // 厂商名称
    val models: List<Model>   // 该厂商下的车型列表
)

/**
 * 车型信息数据类
 */
data class Model(
    val name: String,         // 车型名称
    val fingerprint: String   // 对应的指纹
)
