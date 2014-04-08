
#include <jni.h>

#include <android/log.h>

#include <iostream>
#include <fstream>
#include <string>
#include <opencv2/opencv_modules.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/stitching/detail/autocalib.hpp>
#include <opencv2/stitching/detail/blenders.hpp>
#include <opencv2/stitching/detail/camera.hpp>
#include <opencv2/stitching/detail/exposure_compensate.hpp>
#include <opencv2/stitching/detail/matchers.hpp>
#include <opencv2/stitching/detail/motion_estimators.hpp>
#include <opencv2/stitching/detail/seam_finders.hpp>
#include <opencv2/stitching/detail/util.hpp>
#include <opencv2/stitching/detail/warpers.hpp>
#include <opencv2/stitching/warpers.hpp>

extern "C"
{
/**
	 * rotates an image by the given angle
	 *
	 * rotateImage (jstring imagePath, jint angle)
	 */
	JNIEXPORT jint JNICALL
	Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_setPadding
	(JNIEnv* env, jobject obj, jstring imageSrc,jstring imageDst, jint paddingL, jint paddingT, jint paddingR, jint paddingB)
	{
		Mat matSrc;
		Mat matDst;
		const char* src = env->GetStringUTFChars(imageSrc, 0);
		const char* dst = env->GetStringUTFChars(imageDst, 0);


		matSrc = imread(src, CV_LOAD_IMAGE_COLOR);
		if(! matSrc.data )                              // Check for invalid input
		{
			__android_log_print(ANDROID_LOG_ERROR, TAG, "Could not open or find the image %s", src);
			return -1;
		}

		/*if(strcmp(src, dst) == 0)
		{
			matDst = matSrc;
		}*/



	    copyMakeBorder( matSrc, matDst, paddingT, paddingB, paddingL, paddingR, BORDER_CONSTANT, 0 );


		imwrite(dst, matDst);

		env->ReleaseStringUTFChars(imageSrc, src);
		env->ReleaseStringUTFChars(imageDst, dst);

		matSrc.release();
		matDst.release();
		return 0;
	}

	/**
		 * rotates an image by the given angle
		 *
		 * rotateImage (jstring imagePath, jint angle)
		 */
		JNIEXPORT jint JNICALL
		Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_rotateImage
		(JNIEnv* env, jobject obj, jstring imagePath, jint angle)
		{
			Mat image;
			const char* path = env->GetStringUTFChars(imagePath, 0);
			image = imread(path, CV_LOAD_IMAGE_COLOR);
			if(! image.data )                              // Check for invalid input
			{
				__android_log_print(ANDROID_LOG_ERROR, TAG, "Could not open or find the image %s", path);
				return -1;
			}

			//perform rotation
			switch(angle)
			{
			case 0:
			case 360 :
			case -360 :
				break;
			case 90:
			case -270 :
				cv::transpose(image, image);
				cv::flip(image, image, 1);
				break;
			case 180 :
			case -180 :
				cv::transpose(image, image);
				cv::flip(image, image, 1);
				cv::transpose(image, image);
				break;
			case 270 :
			case -90 :
				cv::flip(image, image, 1);
				cv::transpose(image, image);
				break;
			default:
				__android_log_print(ANDROID_LOG_ERROR, TAG, "unsupported rotation angle : %d", angle);


			}

			imwrite(path, image);

			env->ReleaseStringUTFChars(imagePath, path);

			image.release();
			return 0;
		}
		JNIEXPORT jint JNICALL
		Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_resizeImg
		(JNIEnv* env, jobject obj, jstring imageSrc,jstring imageDst, jint newWidth, jint newHeight)
		{
			Mat matSrc;
			Mat matDst;
			const char* src = env->GetStringUTFChars(imageSrc, 0);
			const char* dst = env->GetStringUTFChars(imageDst, 0);

			matSrc = imread(src, CV_LOAD_IMAGE_COLOR);
			if(! matSrc.data )                              // Check for invalid input
			{
				__android_log_print(ANDROID_LOG_ERROR, TAG, "Could not open or find the image %s", src);
				return -1;
			}

			Size size;
			size.width = newWidth;
			size.height = newHeight;

			__android_log_print(ANDROID_LOG_INFO, TAG, "resizing to %dx%d", size.width, size.height);

			// Resize src to img size
			cv::resize(matSrc, matDst, size);
			imwrite(dst, matDst);

			env->ReleaseStringUTFChars(imageSrc, src);
			env->ReleaseStringUTFChars(imageDst, dst);

			matSrc.release();
			matDst.release();
			return 0;

		}
}
