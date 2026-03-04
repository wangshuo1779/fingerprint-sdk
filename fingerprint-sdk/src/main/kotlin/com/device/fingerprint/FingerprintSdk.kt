package com.device.fingerprint

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import com.device.fingerprint.model.DeviceInfo
import java.util.*

/**
 * 设备指纹 SDK 核心入口
 */
class FingerprintSdk private constructor() {
    private var context: Context? = null
    private var isInitialized = false
    private var isNativeLibraryLoaded = false

    companion object {
        private const val TAG = "FingerprintSdk"
        
        // 与 C++ 层一致的分隔符
        private const val FIELD_SEP = "[#]"
        private const val KV_SEP = "[=]"
        
        @Volatile
        private var instance: FingerprintSdk? = null

        @JvmStatic
        fun getInstance(): FingerprintSdk {
            return instance ?: synchronized(this) {
                instance ?: FingerprintSdk().also { instance = it }
            }
        }

        init {
            // B. 缺乏原生库加载的容错处理 - 优化
            try {
                System.loadLibrary("fingerprint-native")
                // 注意：由于 init 块中无法直接修改外部类的非静态变量，
                // 我们通过设置实例变量的方式在第一次调用时标记。
                // 更好的做法是在 getInstance 时处理，或者在这里调用一个 static 方法。
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library 'fingerprint-native' not found for this architecture: ${Build.SUPPORTED_ABIS.contentToString()}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }

    /**
     * 初始化 SDK
     */
    fun init(context: Context) {
        if (isInitialized) return
        this.context = context.applicationContext
        
        // 检查库是否已加载
        checkNativeLibrary()
        
        this.isInitialized = true
        Log.i(TAG, "FingerprintSdk initialized. Native support: $isNativeLibraryLoaded")
    }

    private fun checkNativeLibrary() {
        try {
            // 尝试调用一个简单的原生方法或通过反射检查
            // 这里我们通过一个标记位来管理
            isNativeLibraryLoaded = try {
                // 尝试调用 native 方法来验证加载状态
                getNativeFingerprint()
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            } catch (e: Exception) {
                false
            }
        } catch (e: Exception) {
            isNativeLibraryLoaded = false
        }
    }

    /**
     * 获取设备指纹数据模型 (包含图片中要求的所有字段)
     */
    fun collectDeviceInfo(): DeviceInfo {
        if (!isInitialized) {
            throw IllegalStateException("FingerprintSdk is not initialized. Call init() first.")
        }
        val ctx = context!!
        val info = DeviceInfo()

        // 1. 基础属性 (Image 1)
        info.brand = Build.BRAND
        info.model = Build.MODEL
        info.device = Build.DEVICE
        info.os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        
        val dm = ctx.resources.displayMetrics
        info.screenWidthPx = dm.widthPixels
        info.screenHeightPx = dm.heightPixels
        info.density = dm.density
        info.densityDpi = dm.densityDpi
        info.xdpi = dm.xdpi
        info.ydpi = dm.ydpi
        info.scaledDensity = dm.scaledDensity
        
        info.ramSize = getTotalRam(ctx)
        info.storageSize = getTotalInternalMemorySize()
        info.buildTime = Build.TIME
        info.language = Locale.getDefault().language

        // 从 C++ 层获取底层硬件特征
        if (isNativeLibraryLoaded) {
            try {
                val nativeData = getNativeFingerprint()
                parseNativeData(nativeData, info)
            } catch (e: Exception) {
                Log.e(TAG, "Native call failed", e)
            }
        } else {
            Log.w(TAG, "Native library not loaded, skipping native collection")
        }

        // 2. 传感器 (Image 2)
        val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        info.sensorCount = sensors.size
        info.sensorList = sensors.map { it.name }

        val pm = ctx.packageManager
        info.hasFingerprint = pm.hasSystemFeature("android.hardware.fingerprint")
        info.hasCamera = pm.hasSystemFeature("android.hardware.camera")
        info.hasNfc = pm.hasSystemFeature("android.hardware.nfc")
        info.hasInfrared = pm.hasSystemFeature("android.hardware.consumerir")

        // 3. 网络环境 (Image 2)
        info.timezone = TimeZone.getDefault().id
        // 注意：IMEI, IMSI, MAC 在现代 Android (10+) 中由于隐私限制无法通过普通 API 获取
        info.macAddress = "RESTRICTED (API 29+)" 
        info.imsi = "RESTRICTED (PRIVACY)"

        // 4. 电池使用情况 (Image 3)
        collectBatteryInfo(ctx, info)

        // 5. 异常标签 (Image 3)
        info.isRoot = checkRootMethod()
        info.isVpn = checkVpn(ctx)
        info.isDeveloperMode = Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        
        return info
    }

    /**
     * 获取设备指纹 ID (结合采集的数据生成)
     */
    fun getFingerprintId(): String {
        val info = collectDeviceInfo()
        // 这里只是一个简单的示例，实际项目中会使用更复杂的 Hash 算法
        return "DEV_FINGERPRINT_${info.brand}_${info.model}_${System.currentTimeMillis()}"
    }

    private fun getTotalRam(context: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(mi)
        return mi.totalMem
    }

    private fun getTotalInternalMemorySize(): Long {
        val path = android.os.Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        return totalBlocks * blockSize
    }

    private fun collectBatteryInfo(context: Context, info: DeviceInfo) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        batteryStatus?.let {
            info.batteryLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            info.batteryVoltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            info.batteryStatus = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            info.batteryHealth = it.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            info.batteryTemp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            info.batteryType = it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        }
    }

    private fun checkRootMethod(): Boolean {
        val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/working/bin/su", "/system/bin/failsafe/su", "/data/local/su")
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        return false
    }

    private fun checkVpn(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
    }

    private fun parseNativeData(nativeData: String, info: DeviceInfo) {
        if (nativeData.isEmpty()) return
        try {
            // A. JNI 数据交互过于简单 - 优化：使用更稳健的分隔符和防御性解析
            val parts = nativeData.split(FIELD_SEP)
            for (part in parts) {
                if (part.isEmpty()) continue
                val kv = part.split(KV_SEP)
                if (kv.size == 2) {
                    val key = kv[0]
                    val value = kv[1]
                    when (key) {
                        "cpuAbi" -> info.cpuAbi = value
                        "cpuMinFreq" -> info.cpuMinFreq = value
                        "cpuMaxFreq" -> info.cpuMaxFreq = value
                        "isEmulator" -> {
                            // 只要原生层或 Java 层有一个检测到是模拟器，就标记为是
                            if (value.toBoolean()) info.isEmulator = true
                        }
                        "isRootNative" -> info.isRootNative = value.toBoolean()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing native data: $nativeData", e)
        }
    }

    /**
     * 原生方法：从 C++ 层获取特征
     */
    private external fun getNativeFingerprint(): String
}
