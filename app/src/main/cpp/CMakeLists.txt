cmake_minimum_required(VERSION 3.10.2)
project("uif_engine")

# MUST BE SHARED LIBRARY FOR ANDROID JNI
add_library(uif_engine SHARED uif_jni.cpp)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(uif_engine ${log-lib} ${jnigraphics-lib})
