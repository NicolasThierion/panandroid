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
LOCAL_SRC_FILES := jniwrapper.cpp \
					opencv2/modules/stitching/src/autocalib.cpp \
					opencv2/modules/stitching/src/blenders.cpp \
					opencv2/modules/stitching/src/camera.cpp \
					opencv2/modules/stitching/src/exposure_compensate.cpp \
					opencv2/modules/stitching/src/motion_estimators.cpp \
					opencv2/modules/stitching/src/seam_finders.cpp \
					opencv2/modules/stitching/src/util.cpp \
					opencv2/modules/stitching/src/warpers.cpp \
					opencv2/modules/stitching/src/stitcher.cpp \
LOCAL_LDLIBS +=  -llog -ldl

include $(BUILD_SHARED_LIBRARY)

APP_PLATFORM := android-17
APP_OPTIM:= debug
APP_CFLAGS+= -O2 -mfpu=neon
LOCAL_CFLAGS    := -DRAPIDXML_NO_EXCEPTIONS
LOCAL_CFLAGS    += -g
LOCAL_CFLAGS    += -ggdb
LOCAL_CFLAGS    += -O1