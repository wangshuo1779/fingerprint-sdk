package com.device.fingerprint.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 设备指纹完整数据模型
 */
data class DeviceInfo(
    // 1. 基础属性 (Image 1)
    var brand: String? = null,
    var model: String? = null,
    var device: String? = null,
    var os: String? = null,
    var screenWidthPx: Int = 0,
    var screenHeightPx: Int = 0,
    var scaledDensity: Float = 0f,
    var density: Float = 0f,
    var densityDpi: Int = 0,
    var xdpi: Float = 0f,
    var ydpi: Float = 0f,
    var cpuMinFreq: String? = null,
    var cpuMaxFreq: String? = null,
    var cpuAbi: String? = null,
    var ramSize: Long = 0,
    var storageSize: Long = 0,
    var buildTime: Long = 0,
    var language: String? = null,

    // 2. 传感器 (Image 2)
    var sensorCount: Int = 0,
    var sensorList: List<String>? = null,
    var hasFingerprint: Boolean = false,
    var hasCamera: Boolean = false,
    var hasNfc: Boolean = false,
    var hasInfrared: Boolean = false,

    // 3. 网络环境 (Image 2)
    var macAddress: String? = null,
    var ipAddress: String? = null,
    var simOperator: String? = null,
    var imsi: String? = null,
    var simCount: Int = 0,
    var baseStationInfo: String? = null,
    var timezone: String? = null,

    // 4. 电池使用情况 (Image 3)
    var batteryVoltage: Int = 0,
    var batteryStatus: Int = 0,
    var batteryHealth: Int = 0,
    var batteryType: String? = null,
    var batteryLevel: Int = 0,
    var batteryTemp: Int = 0,

    // 5. 异常标签与安全属性 (Image 3)
    var buildProperties: Map<String, String>? = null,
    var isEmulator: Boolean = false,
    var isRoot: Boolean = false,
    var isRootNative: Boolean = false,
    var isVpn: Boolean = false,
    var isDeveloperMode: Boolean = false,
    var isMockLocation: Boolean = false
) {
    /**
     * 将所有采集到的信息转换为 JSON 对象，方便直接查看和上报
     */
    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            // 基础属性
            json.put("brand", brand)
            json.put("model", model)
            json.put("device", device)
            json.put("os", os)
            json.put("screenWidthPx", screenWidthPx)
            json.put("screenHeightPx", screenHeightPx)
            json.put("density", density.toDouble())
            json.put("cpuAbi", cpuAbi)
            json.put("cpuMaxFreq", cpuMaxFreq)
            json.put("ramSize", ramSize)
            json.put("storageSize", storageSize)
            
            // 传感器
            json.put("sensorCount", sensorCount)
            sensorList?.let {
                val sensorArray = JSONArray()
                it.forEach { name -> sensorArray.put(name) }
                json.put("sensorList", sensorArray)
            }
            
            // 安全与异常
            json.put("isRoot", isRoot)
            json.put("isRootNative", isRootNative)
            json.put("isEmulator", isEmulator)
            json.put("isVpn", isVpn)
            json.put("isDeveloperMode", isDeveloperMode)
            
            // 电池
            json.put("batteryLevel", batteryLevel)
            json.put("batteryHealth", batteryHealth)
            json.put("batteryVoltage", batteryVoltage)
            
            // 其他
            json.put("timezone", timezone)
            json.put("language", language)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return json
    }
}
