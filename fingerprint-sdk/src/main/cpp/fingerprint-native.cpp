#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <android/log.h>
#include <sys/system_properties.h>
#include <unistd.h>
#include <sstream>

#define TAG "FingerprintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 使用更安全的自定义分隔符
const std::string FIELD_SEP = "[#]"; 
const std::string KV_SEP = "[=]";

std::string get_prop(const char* prop_name) {
    char value[PROP_VALUE_MAX];
    int len = __system_property_get(prop_name, value);
    if (len > 0) {
        return std::string(value);
    }
    return "UNKNOWN";
}

std::string read_file(const char* path) {
    std::ifstream is(path);
    if (is.is_open()) {
        std::string line;
        if (std::getline(is, line)) {
            return line;
        }
    }
    return "N/A";
}

// 增强模拟器检测逻辑
bool detect_emulator() {
    // 1. 检查特定的模拟器文件
    const char* emulator_files[] = {
        "/system/lib/libc_malloc_debug_qemu.so",
        "/sys/qemu_trace",
        "/system/bin/qemu-props",
        "/dev/socket/qemud",
        "/dev/qemu_pipe"
    };
    for (const char* file : emulator_files) {
        if (access(file, F_OK) == 0) return true;
    }

    // 2. 检查系统属性
    std::string hardware = get_prop("ro.hardware");
    if (hardware == "goldfish" || hardware == "ranchu" || hardware == "vbox86") return true;

    std::string model = get_prop("ro.product.model");
    if (model.find("sdk_gphone") != std::string::npos || model.find("Emulator") != std::string::npos) return true;

    return false;
}

// 增强底层 Root 检测
bool detect_root_native() {
    const char* su_paths[] = {
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su"
    };
    for (const char* path : su_paths) {
        if (access(path, F_OK) == 0) return true;
    }
    return false;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_device_fingerprint_FingerprintSdk_getNativeFingerprint(JNIEnv *env, jobject thiz) {
    LOGI("Calling getNativeFingerprint from C++ layer");
    
    std::stringstream ss;
    
    // CPU 信息
    ss << "cpuAbi" << KV_SEP << get_prop("ro.product.cpu.abi") << FIELD_SEP;
    ss << "cpuMinFreq" << KV_SEP << read_file("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq") << FIELD_SEP;
    ss << "cpuMaxFreq" << KV_SEP << read_file("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq") << FIELD_SEP;
    
    // 异常检测结果
    ss << "isEmulator" << KV_SEP << (detect_emulator() ? "true" : "false") << FIELD_SEP;
    ss << "isRootNative" << KV_SEP << (detect_root_native() ? "true" : "false") << FIELD_SEP;
    
    // Build 属性
    ss << "ro.product.model" << KV_SEP << get_prop("ro.product.model") << FIELD_SEP;
    ss << "ro.hardware" << KV_SEP << get_prop("ro.hardware") << FIELD_SEP;
    ss << "ro.kernel.qemu" << KV_SEP << get_prop("ro.kernel.qemu");

    return env->NewStringUTF(ss.str().c_str());
}
