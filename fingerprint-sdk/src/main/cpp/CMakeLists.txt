cmake_minimum_required(VERSION 3.22.1)

# 定义项目名称
project("fingerprint-native")

# 查找 NDK 提供的日志库
find_library(log-lib log)

# 添加共享库
add_library(
        fingerprint-native
        SHARED
        fingerprint-native.cpp
)

# 链接库
target_link_libraries(
        fingerprint-native
        ${log-lib}
)
