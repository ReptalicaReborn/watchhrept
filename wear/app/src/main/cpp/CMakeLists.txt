cmake_minimum_required(VERSION 3.22.1)
project(nodebridge CXX)

# Set by app/build.gradle.kts to the extracted nodejs-mobile release.
set(NODE_MOBILE_DIR "" CACHE PATH "Extracted nodejs-mobile release")

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES IMPORTED_LOCATION ${NODE_MOBILE_DIR}/bin/${ANDROID_ABI}/libnode.so)

add_library(nodebridge SHARED native-lib.cpp)
target_include_directories(nodebridge PRIVATE ${NODE_MOBILE_DIR}/include/node)
find_library(log-lib log)
# AGP packages imported libraries that targets link against, so libnode.so ships with the APK.
target_link_libraries(nodebridge node ${log-lib})
