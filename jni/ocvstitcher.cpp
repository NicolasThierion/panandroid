#include <jni.h>


#include <opencv2/core/core.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/calib3d/calib3d.hpp>
#include <opencv2/stitching/stitcher.hpp>

#include <vector>
#include <iostream>
#include <stdio.h>
#include <list>
#include <sstream>
#include <string>

#include <android/log.h>
#define DEBUG_TAG "NDK_AndroidNDK3SampleActivity"

using namespace std;
using namespace cv;

extern "C" {
//JNIEXPORT Mat JNICALL Java_org_opencv_samples_tutorial3_Sample3Native_FindFeatures(JNIEnv*, jobject, jlong addrGray, jlong addrRgba)


JNIEXPORT void JNICALL Java_org_panandroid_stitcher_StitcherActivity_FindFeatures(
        JNIEnv*, jobject, jlong im1, jlong im2, jlong im3, jint no_images) {


	//jboolean isCopy;
	//const char * szLogThis = (*env)->GetStringUTFChars(env, , &isCopy);
	//(*env)->ReleaseStringUTFChars(env, logThis, szLogThis);

    vector < Mat > imgs;
    bool try_use_gpu = false;
    // New testing
    Mat& temp1 = *((Mat*) im1);
    Mat& temp2 = *((Mat*) im2);
    Mat& pano = *((Mat*) im3);

    for (int k = 0; k < no_images; ++k) {
        string id;
        ostringstream convert;
        convert << k;
        id = convert.str();
    	__android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "NDK:LC: [%s]", id.c_str());

        Mat img = imread("/storage/sdcard0/panoTmpImage/im" + id + ".jpeg");


        imgs.push_back(img);
    }

    Stitcher stitcher = Stitcher::createDefault(try_use_gpu);
    Stitcher::Status status = stitcher.stitch(imgs, pano);

}

}
