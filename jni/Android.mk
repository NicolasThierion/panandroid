# ENSICAEN
# 6 Boulevard Marechal Juin
# F-14050 Caen Cedex
#
# This file is owned by ENSICAEN students.
# No portion of this code may be reproduced, copied
# or revised without written permission of the authors.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_LIB_TYPE        := STATIC
OPENCV_INSTALL_MODULES := on

#include ../includeOpenCV.mk

include $(OPENCV_HOME)/sdk/native/jni/OpenCV.mk

LOCAL_MODULE    := jniwrapper
LOCAL_SRC_FILES := jniwrapper.cpp
LOCAL_LDLIBS +=  -llog -ldl

include $(BUILD_SHARED_LIBRARY)
