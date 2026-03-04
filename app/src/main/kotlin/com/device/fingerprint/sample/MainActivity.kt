package com.device.fingerprint.sample

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.device.fingerprint.FingerprintSdk

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 SDK
        FingerprintSdk.getInstance().init(this)

        val tvFingerprint = findViewById<TextView>(R.id.tv_fingerprint)
        val btnGetFingerprint = findViewById<Button>(R.id.btn_get_fingerprint)

        btnGetFingerprint.setOnClickListener {
            try {
                // 1. 调用采集函数
                val info = FingerprintSdk.getInstance().collectDeviceInfo()
                
                // 2. 将全量信息转换为 JSON 并打印到 Logcat
                val fullDataJson = info.toJson().toString(4)
                android.util.Log.d("DeviceFingerprint", "Full Collected Data:\n$fullDataJson")

                // 3. 更新 UI 显示核心字段
                val displayInfo = """
                    Brand: ${info.brand}
                    Model: ${info.model}
                    OS: ${info.os}
                    Screen: ${info.screenWidthPx}x${info.screenHeightPx}
                    CPU ABI: ${info.cpuAbi}
                    RAM: ${info.ramSize / 1024 / 1024} MB
                    isEmulator: ${info.isEmulator}
                    isRoot: ${info.isRoot} (Native: ${info.isRootNative})
                    Battery: ${info.batteryLevel}%
                    
                    (Full JSON printed to Logcat with tag 'DeviceFingerprint')
                """.trimIndent()
                
                tvFingerprint.text = "Device Info: \n$displayInfo"
                Toast.makeText(this@MainActivity, "Data Collected & Logged", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
