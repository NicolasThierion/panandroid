/*M///////////////////////////////////////////////////////////////////////////////////////
//
//  IMPORTANT: READ BEFORE DOWNLOADING, COPYING, INSTALLING OR USING.
//
//  By downloading, copying, installing or using the software you agree to this license.
//  If you do not agree to this license, do not download, install,
//  copy or use the software.
//
//
//                          License Agreement
//                For Open Source Computer Vision Library
//
// Copyright (C) 2000-2008, Intel Corporation, all rights reserved.
// Copyright (C) 2009, Willow Garage Inc., all rights reserved.
// Third party copyrights are property of their respective owners.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
//   * Redistribution's of source code must retain the above copyright notice,
//     this list of conditions and the following disclaimer.
//
//   * Redistribution's in binary form must reproduce the above copyright notice,
//     this list of conditions and the following disclaimer in the documentation
//     and/or other materials provided with the distribution.
//
//   * The name of the copyright holders may not be used to endorse or promote products
//     derived from this software without specific prior written permission.
//
// This software is provided by the copyright holders and contributors "as is" and
// any express or implied warranties, including, but not limited to, the implied
// warranties of merchantability and fitness for a particular purpose are disclaimed.
// In no event shall the Intel Corporation or contributors be liable for any direct,
// indirect, incidental, special, exemplary, or consequential damages
// (including, but not limited to, procurement of substitute goods or services;
// loss of use, data, or profits; or business interruption) however caused
// and on any theory of liability, whether in contract, strict liability,
// or tort (including negligence or otherwise) arising in any way out of
// the use of this software, even if advised of the possibility of such damage.
//
//
//M*/


#define V3
#define DEBUG

#define TAG "OpenCV stitcher"
#include <jni.h>
#include <android/log.h>

#ifdef V1
#include "ocvstitcherV1.cpp"
#endif

#ifdef V2
#include "ocvstitcherV2.cpp"
#endif

#ifdef V3
#include "ocvstitcherV3.cpp"
#endif

#include "tools.cpp"


//TODO : cleanup function.
//TODO : add memory usage to debug logs.
//@bug : out of memory when compositing with more than 5 images.

/*******************
 * PUBLIC FUNCTIONS
 ******************/


extern "C"
{
        //================ REGISTRATION STEPS ============================
        /**
         * Init the stitcher. Fetch and store parameters, images and their respective rotation.
         * @param compositionFile Result file when panorama is store.
         * @param files Base path images.
         * @param orientations Pitch, yaw and roll of images.
         */
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_newStitcher
        (JNIEnv* env, jobject obj, jstring compositionFile, jobjectArray files, jobjectArray matchingMask)
        {

        		jstring tmpFileName;
				const char* path;
				_nbImages = env->GetArrayLength(files);
                jintArray matchingMaskLine;

#ifdef DEBUG
                int64 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
                __android_log_print(ANDROID_LOG_INFO, TAG, "Init stitcher");
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif

                _matchingMask = Mat::zeros(_nbImages, _nbImages, CV_8U);

                // Fetch and convert images path from jstring to string.
                for (int i = 0; i < _nbImages; ++i)
                {
                	tmpFileName = (jstring) env->GetObjectArrayElement(files, i);
					matchingMaskLine = (jintArray)env->GetObjectArrayElement(matchingMask, i);
				    int *matchingMaskElement=env->GetIntArrayElements(matchingMaskLine, 0);
				    for(int j=0; j<_nbImages; ++j)
				    {
				    	_matchingMask.at<uchar>(i, j) = (uchar)matchingMaskElement[j];
					}


					path = env->GetStringUTFChars(tmpFileName, 0);
					_imagesPath.push_back(path);
#ifdef DEBUG
					__android_log_print(ANDROID_LOG_INFO, TAG, "Store path #%d : %s", i + 1, path);
#endif
					env->ReleaseStringUTFChars(tmpFileName, path);
                }

                // Path to store panorama is the last element.
                path = env->GetStringUTFChars(compositionFile, 0);
                _resultPath = path;
                env->ReleaseStringUTFChars(tmpFileName, path);
#ifdef DEBUG
                __android_log_print(ANDROID_LOG_INFO, TAG, "Stitcher initialized  (%f sec)", ((getTickCount() - t) / getTickFrequency()));
#endif
                return 0;
        }

#ifdef TO_REMOVE

        //================ COMPOSITING STEPS ============================

        // Find feature in images from internal image array.
             JNIEXPORT jint JNICALL
             Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_findFeatures
             (JNIEnv* env, jobject obj)
             {
                     return findFeatures();
             }

             // Match _features.
             JNIEXPORT jint JNICALL
             Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_matchFeatures
             (JNIEnv* env, jobject obj)
             {
                     return matchFeatures();
             }

             // Adjust parameters.
             JNIEXPORT jint JNICALL
             Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_adjustParameters
             (JNIEnv* env, jobject obj)
             {
                     return adjustParameters();
             }

        // Warp _images.
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_warpImages
        (JNIEnv* env, jobject obj)
        {
                return warpImages();
        }

        // Compensate exposure errors and find seam _masks.
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_findSeamMasks
        (JNIEnv* env, jobject obj)
        {
                return findSeamMask();
        }
#endif
        // Compose final panorama.
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_composePanorama
        (JNIEnv* env, jobject obj)
        {
                return composePanorama();
        }
        // get current progress
		JNIEXPORT jint JNICALL
		Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_getProgress
		(JNIEnv* env, jobject obj)
		{
				return getProgress();
		}

		// get indices of used images in the panorama.
		JNIEXPORT jintArray JNICALL
		Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_getUsedIndices
		(JNIEnv* env, jobject obj)
		{
			jintArray indices;
			int size = _indices.size();
			indices = (*env).NewIntArray(size);
			if (indices == NULL)
			{
				return NULL; /* out of memory error thrown */
			}
			int i=0;
			vector<int>::iterator it, itEnd = _indices.end();

			// fill a temp structure to use to populate the java int array
			jint* fill = new jint[size];
			for (it = _indices.begin(); it != itEnd; ++it)
			{
				fill[i++] = (*it);
			}
			// move from the temp structure to the java structure
			(*env).SetIntArrayRegion(indices, 0, size, fill);
			free(fill);
			return indices;
		}

		JNIEXPORT jdouble JNICALL
		Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_getWorkingResolution
		(JNIEnv* env, jobject obj)
		{
			return compose_megapix;
		}


}

