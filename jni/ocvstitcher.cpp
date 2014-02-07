/**
 * ENSICAEN
 * 6 Boulevard Marechal Juin
 * F-14050 Caen Cedex
 *
 * This file is owned by ENSICAEN students.
 * No portion of this code may be reproduced, copied
 * or revised without written permission of the authors.
 */

/**
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @date Mon Feb 03 2014
 * @version 0.0.1
 */

/**
 * @file ocvstitcher.cpp
 * This file contains an implementation of the stitching pipeline with
 * high level functionality of OpenCV.
 * @todo Study stitching pipeline with stitching_detailled.cpp (Cf. OpenCV docs)
 * @todo Read abstract [BL07] (Cf. OpenCV docs)
 */

#include <jni.h>

#include <android/log.h>

#include <opencv2/highgui/highgui.hpp>
#include <opencv2/stitching/stitcher.hpp>

using namespace std;
using namespace cv;

#define TAG "OpenCVStitcher"

extern "C" {
JNIEXPORT jint JNICALL
Java_fr_ensicaen_panandroid_stitcher_StitcherActivity_OpenCVStitcher(
        JNIEnv* env, jobject obj, jstring folder, jobjectArray snapshots) {
    bool try_use_gpu = false;
    int numberSnapshots = env->GetArrayLength(snapshots);

    string filenameResult = "panorama.jpg";
    string folderResult(env->GetStringUTFChars(folder, JNI_FALSE));
    vector<Mat> images;
    Mat panorama;

    for (int i = 0; i < numberSnapshots; ++i) {
        jstring tmp = (jstring) env->GetObjectArrayElement(snapshots, i);
        string filename(env->GetStringUTFChars(tmp, 0));
        Mat img = imread(folderResult + filename);
        images.push_back(img);
    }

    Stitcher stitcher = Stitcher::createDefault(try_use_gpu);
    Stitcher::Status status = stitcher.stitch(images, panorama);

    if (status != Stitcher::OK) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Can't stitch images, error code = %d", int(status));
        return -1;
    }

    imwrite(folderResult + filenameResult, panorama);
    return 0;
}

}

