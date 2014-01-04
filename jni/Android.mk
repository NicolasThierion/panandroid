LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_LIB_TYPE:=STATIC
OPENCV_INSTALL_MODULES:=on

include ../includeOpenCV.mk


include ../sdk/native/jni/OpenCV.mk

LOCAL_MODULE    := ocvstitcher
LOCAL_SRC_FILES := ocvstitcher.cpp
LOCAL_LDLIBS +=  -llog -ldl

include $(BUILD_SHARED_LIBRARY)
