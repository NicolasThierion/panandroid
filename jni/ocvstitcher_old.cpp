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



//TODO : cleanup function.
//TODO : add memory usage to debug logs.
//@bug : out of memory when compositing with more than 5 images.

#define DEBUG

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

using namespace std;
using namespace cv;
using namespace cv::detail;

#define TAG "OpenCV stitcher"

/*************
 * PARAMETERS
 ************/
/** Wave correction : try to keep horizon horizontal **/
bool WAVE_CORRECTION = true;		// : true

/** ????? **/
bool isComposeScale = false;	//compositor crashes if true

/** Try to use GPU. **/
bool tryGPU = true;				// : false

/** Resolution for compositing step. **/
double composeMegapix = 0.5;	// :-1

/** Resolution for seam estimation step in Mpx. **/
double seamMegapix = 0.1;	// :0.1

/** Seam work aspect. **/
double seamWorkAspect = 0.5;	//1

/** Resolution for image registration step in Mpx. **/
double workMegapix = 0.2;	// :0.6

/** Work scale. **/
static double workScale = 0.5;	// :1

/** Blending strength from [0,100] range. **/
float blendStrength = 5;		//:5

/** Threshold for two images are from the same panorama confidence. **/
float confidenceThreshold = 1.f;		//1.f

/** Confidence for feature matching step. **/
float matchConfidence = 0.3f;			//0.3f

/** Blending method. **/
int BLEND_TYPE = Blender::MULTI_BAND;

/** Exposure compensation method. **/
int EXPOSURE_COMPENSATOR_TYPE = ExposureCompensator::GAIN_BLOCKS;

/** Warped image scale. **/
static float warpedImageScale;

/** Bundle adjustment cost function. reproj don't seems suitable for spherical pano**/
string bundleAdjustment = "ray"; 	//["reproj", "ray" : "ray"]

/** Set refinement mask for bundle adjustment. It looks like 'x_xxx'
    where 'x' means refine respective parameter and '_' means don't
    refine one, and has the following format:
    <fx><skew><ppx><aspect><ppy>. The default mask is 'xxxxx'. If bundle
    adjustment doesn't support estimation of selected parameter then
    the respective flag is ignored. **/
string bundleAdjustmentRefineMask = "xxxxx";

/** Type of features. **/
string featuresType = "orb";

/** Seam estimation method. **/
string seamFindType = "gc_color";

/** Warp surface type. **/
string WARP_TYPE = "spherical";


/*************
 * ATTRIBUTES
 ************/
/** Where store the result image. **/
static string _resultPath;

/** Vector of images features. **/
static vector<ImageFeatures> _features;

/** Full images size. **/
static vector<Size> _fullImagesSize;

/** Number of images **/
static int _nbImages;

/** Images path. **/
static vector<string> _imagesPath;

/** Images rotation **/
static vector<float* > _imagesRotations;


/** Vector of cameras parameters. **/
static vector<CameraParams> _cameras;

/** Vector of images. **/
static vector<Mat> _images;

/** Vector of images warped. **/
static vector<Mat> _imagesWarped;

/** Vector of images warped F. **/
vector<Mat> _imagesWarpedF;

/** Vector of _masks. **/
vector<Mat> _masks;

/** Vector of masks warped. **/
static vector<Mat> _masksWarped;

/** Vector of pairwise matches. **/
static vector<MatchesInfo> _pairwiseMatches;

/** Vector of corners. **/
vector<Point> _corners;

/** Vector of sizes. **/
vector<Size> _sizes;

/** Exposure compensator. **/
static Ptr<ExposureCompensator> _compensator = ExposureCompensator::createDefault(EXPOSURE_COMPENSATOR_TYPE);

/** Warper. **/
static Ptr<RotationWarper> _warper;

/** Warper creator. **/
Ptr<WarperCreator> _warperCreator;

/** Perform wave effect correction. **/
WaveCorrectKind _waveCorrection = detail::WAVE_CORRECT_HORIZ;

/*************
 * PROTOTYPES
 ************/
static int parseCmdArgs(int argc, char** argv);


/*******************
 * PUBLIC FUNCTIONS
 ******************/





extern "C"
{
        //================ REGISTRATION STEPS ============================
        /**
         * Init the stitcher. Fetch and store parameters, images and their respective rotation.
         *
         * newStitcher (jstring compositionFile, jobjectArray files, jobjectArray orientations)
         * @param jstring Result file.
         * @param files Base images.
         * @param orientations Pitch, yaw and roll of images.
         */
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_newStitcher
        (JNIEnv* env, jobject obj, jstring compositionFile, jobjectArray files, jobjectArray orientations)
        {

        		jstring tmpFileName;
				float* orientation;
				const char* path;
				_nbImages = env->GetArrayLength(files);
                jfloatArray orientationsArray;

#ifdef DEBUG
                int64 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
                __android_log_print(ANDROID_LOG_INFO, TAG, "init stitcher");
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif
                // Fetch and convert images path from jstring to string.
                for (int i = 0; i < _nbImages; ++i)
                {
                	tmpFileName = (jstring) env->GetObjectArrayElement(files, i);
					orientation = new float[3];

					orientationsArray = (jfloatArray)env->GetObjectArrayElement(orientations, i);
				    jfloat *orientationElement=env->GetFloatArrayElements(orientationsArray, 0);
				    for(int j=0; j<3; ++j)
				    {
				    	orientation[j] = orientationElement[j];
					}
					path = env->GetStringUTFChars(tmpFileName, 0);
					_imagesPath.push_back(path);
					_imagesRotations.push_back(orientation);
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

        /**
         * Find feature in images from internal image array.
         *
         * findFeatures()
         */
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_findFeatures
        (JNIEnv* env, jobject obj)
        {
                bool isSeamScale = false;
                bool isWorkScale = false;
                double seamScale = 1;
                Mat fullImage, image;
                Ptr<FeaturesFinder> finder;

#ifdef DEBUG
                int64 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
                __android_log_print(ANDROID_LOG_INFO, TAG, "find features");
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif

                // Check if we have enough images.
                _nbImages = static_cast<int>(_imagesPath.size());
                if (_nbImages < 2)
                {
                        __android_log_print(ANDROID_LOG_ERROR, TAG, "Need more _images.");
                        return -1;
                }

                // Build one of finding engines.
                if (featuresType == "surf")
                {
                        finder = new SurfFeaturesFinder();
                }
                else if (featuresType == "orb")
                {
                        finder = new OrbFeaturesFinder();
                }
                else
                {
                        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown 2D _features type: %s", featuresType.c_str());
                        return -1;
                }

                // Resize vectors used to store features of images and images itself.
                _features.resize(_nbImages);
                _fullImagesSize.resize(_nbImages);
                _images.resize(_nbImages);

                // Find features on all images.
                for (int i = 0; i < _nbImages; ++i)
                {
                        // Read image.
                        fullImage = imread(_imagesPath[i]);
                        _fullImagesSize[i] = fullImage.size();
                        if (fullImage.empty())
                        {
                                __android_log_print(ANDROID_LOG_ERROR, TAG, "Can't open image %s", _imagesPath[i].c_str());
                                return -1;
                        }

                        // Resize (medium resolution).
                        if (workMegapix < 0)
                        {
                                image = fullImage;
                                workScale = 1;
                                isWorkScale = true;
                        }
                        else
                        {
                                if (!isWorkScale)
                                {
                                        workScale = min(1.0, sqrt(workMegapix * 1e6 / fullImage.size().area()));
                                        isWorkScale = true;
                                }
                                resize(fullImage, image, Size(), workScale, workScale);
                        }

                        if (!isSeamScale)
                        {
                                seamScale = min(1.0, sqrt(seamMegapix * 1e6 / fullImage.size().area()));
                                seamWorkAspect = seamScale / workScale;
                                isSeamScale = true;
                        }

                        // Find _features in the current working image.
                        (*finder)(image, _features[i]);
                        _features[i].img_idx = i;
#ifdef DEBUG
                        __android_log_print(ANDROID_LOG_INFO, TAG, "Features in image #%d: %d", i + 1, _features[i].keypoints.size());
#endif
                        // Store image subscaled by seamScale.
                        resize(fullImage, image, Size(), seamScale, seamScale);
                        _images[i] = image.clone();

                        fullImage.release();
						image.release();

                }

                // Clean up workspace.
                finder->collectGarbage();

#ifdef DEBUG
                __android_log_print(ANDROID_LOG_INFO, TAG, "Finding _features time: %f sec", ((getTickCount() - t) / getTickFrequency()));
#endif
                return 0;
        }

        // Match _features.
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_matchFeatures
        (JNIEnv* env, jobject obj)
        {
                int numberImages;
                vector<string> imagesPathSubset;
                vector<Mat> imagesSubset;
                vector<Size> fullImagesSizeSubset;
                BestOf2NearestMatcher matcher(tryGPU, matchConfidence); // TODO : Adjust parameters using benchmark.

#ifdef DEBUG
                int64 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
                __android_log_print(ANDROID_LOG_INFO, TAG, "pairwise matching");
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif
                matcher(_features, _pairwiseMatches); // TODO : try another matcher method.
                matcher.collectGarbage();

                __android_log_print(ANDROID_LOG_INFO, TAG, "Pairwise matching time: %f sec", ((getTickCount() - t) / getTickFrequency()));

                t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "Selecting _images and matches subset...");

                vector<int> indices = leaveBiggestComponent(_features, _pairwiseMatches, confidenceThreshold);

                for (size_t i = 0; i < indices.size(); ++i) {
                        imagesPathSubset.push_back(_imagesPath[indices[i]]);
                        __android_log_print(ANDROID_LOG_INFO, TAG, "Select image #%d : %s", i + 1, _imagesPath[indices[i]].c_str());
                        imagesSubset.push_back(_images[indices[i]]);
                        fullImagesSizeSubset.push_back(_fullImagesSize[indices[i]]);
                }

                _images = imagesSubset;
                _nbImages = _images.size();
                _imagesPath = imagesPathSubset;
                _fullImagesSize = fullImagesSizeSubset;
#ifdef DEBUG
                __android_log_print(ANDROID_LOG_INFO, TAG, "Selecting _images and matches subset time: %f sec", ((getTickCount() - t) / getTickFrequency()));
#endif
                // Check if we still have enough _images.

                if (_nbImages < 2) {
                        __android_log_print(ANDROID_LOG_ERROR, TAG, "Need more _images.");
                        return -1;
                }

                return 0;
        }

        /* Adjust parameters. */
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_adjustParameters
        (JNIEnv* env, jobject obj)
        {
                vector<double> focals;
                vector<Mat> rmats;
                HomographyBasedEstimator estimator;
                Mat_<uchar> refineMask = Mat::zeros(3, 3, CV_8U);
                Ptr<detail::BundleAdjusterBase> adjuster;

#ifdef DEBUG
                int64 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
                __android_log_print(ANDROID_LOG_INFO, TAG, "adjusting parameters");
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif

                // Estimate camera parameters rough initial guess.
                estimator(_features, _pairwiseMatches, _cameras);

                for (size_t i = 0; i < _cameras.size(); ++i) {
                        Mat R;
                        _cameras[i].R.convertTo(R, CV_32F);
                        _cameras[i].R = R;
                        //LOGLN("Initial intrinsics #" << indices[i]+1 << ":\n" << _cameras[i].K());
                }

                if (bundleAdjustment == "reproj")
                        adjuster = new detail::BundleAdjusterReproj();
                else if (bundleAdjustment == "ray")
                        adjuster = new detail::BundleAdjusterRay();
                else {
                        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown bundle adjustment cost function: %s", bundleAdjustment.c_str());
                        return -1;
                }

                adjuster->setConfThresh(confidenceThreshold);

                if (bundleAdjustmentRefineMask[0] == 'x')
                        refineMask(0,0) = 1;
                if (bundleAdjustmentRefineMask[1] == 'x')
                        refineMask(0,1) = 1;
                if (bundleAdjustmentRefineMask[2] == 'x')
                        refineMask(0,2) = 1;
                if (bundleAdjustmentRefineMask[3] == 'x')
                        refineMask(1,1) = 1;
                if (bundleAdjustmentRefineMask[4] == 'x')
                        refineMask(1,2) = 1;

                adjuster->setRefinementMask(refineMask);
                (*adjuster)(_features, _pairwiseMatches, _cameras);

                // Find median focal length.
                for (size_t i = 0; i < _cameras.size(); ++i) {
                        //LOGLN("Camera #" << indices[i]+1 << ":\n" << _cameras[i].K());
                        focals.push_back(_cameras[i].focal);
                }

                sort(focals.begin(), focals.end());

                if (focals.size() % 2 == 1)
                        warpedImageScale = static_cast<float>(focals[focals.size() / 2]);
                else
                        warpedImageScale = static_cast<float>(focals[focals.size() / 2 - 1] + focals[focals.size() / 2]) * 0.5f;

                // Wave correction.
                if (WAVE_CORRECTION) {
                        for (size_t i = 0; i < _cameras.size(); ++i)
                                rmats.push_back(_cameras[i].R);

                        waveCorrect(rmats, _waveCorrection);

                        for (size_t i = 0; i < _cameras.size(); ++i)
                                _cameras[i].R = rmats[i];
                }
#ifdef DEBUG
                __android_log_print(ANDROID_LOG_INFO, TAG, "Adjusting parameters time: %f sec", ((getTickCount() - t) / getTickFrequency()));
#endif
                return 0;
        }

        //================ COMPOSITING STEPS ============================
        /* Warp _images. */
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_warpImages
        (JNIEnv* env, jobject obj)
        {
                int numberImages = static_cast<int>(_imagesPath.size());

                // Resize static vector.
                _corners.resize(numberImages);
                _imagesWarped.resize(numberImages);
                _imagesWarpedF.resize(numberImages);
                _masks.resize(numberImages);
                _masksWarped.resize(numberImages);
                _sizes.resize(numberImages);

#ifdef DEBUG
                int64 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
                __android_log_print(ANDROID_LOG_INFO, TAG, "wraping images");
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif
                // Prepare _images _masks.
                for (int i = 0; i < numberImages; ++i) {
                        _masks[i].create(_images[i].size(), CV_8U);
                        _masks[i].setTo(Scalar::all(255));
                }

                // Warp _images and their _masks.
                if (WARP_TYPE == "plane")
                        _warperCreator = new cv::PlaneWarper();
                else if (WARP_TYPE == "cylindrical")
                        _warperCreator = new cv::CylindricalWarper();
                else if (WARP_TYPE == "spherical")
                        _warperCreator = new cv::SphericalWarper();
                else if (WARP_TYPE == "fisheye")
                        _warperCreator = new cv::FisheyeWarper();
                else if (WARP_TYPE == "stereographic")
                        _warperCreator = new cv::StereographicWarper();
                else if (WARP_TYPE == "compressedPlaneA2B1")
                        _warperCreator = new cv::CompressedRectilinearWarper(2, 1);
                else if (WARP_TYPE == "compressedPlaneA1.5B1")
                        _warperCreator = new cv::CompressedRectilinearWarper(1.5, 1);
                else if (WARP_TYPE == "compressedPlanePortraitA2B1")
                        _warperCreator = new cv::CompressedRectilinearPortraitWarper(2, 1);
                else if (WARP_TYPE == "compressedPlanePortraitA1.5B1")
                        _warperCreator = new cv::CompressedRectilinearPortraitWarper(1.5, 1);
                else if (WARP_TYPE == "paniniA2B1")
                        _warperCreator = new cv::PaniniWarper(2, 1);
                else if (WARP_TYPE == "paniniA1.5B1")
                        _warperCreator = new cv::PaniniWarper(1.5, 1);
                else if (WARP_TYPE == "paniniPortraitA2B1")
                        _warperCreator = new cv::PaniniPortraitWarper(2, 1);
                else if (WARP_TYPE == "paniniPortraitA1.5B1")
                        _warperCreator = new cv::PaniniPortraitWarper(1.5, 1);
                else if (WARP_TYPE == "mercator")
                        _warperCreator = new cv::MercatorWarper();
                else if (WARP_TYPE == "transverseMercator")
                        _warperCreator = new cv::TransverseMercatorWarper();

                if (_warperCreator.empty()) {
                        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown _warper: %s", WARP_TYPE.c_str());
                        return -1;
                }

                _warper = _warperCreator->create(static_cast<float>(warpedImageScale * seamWorkAspect));

                for (int i = 0; i < numberImages; ++i) {
                        Mat_<float> K;
                        _cameras[i].K().convertTo(K, CV_32F);
                        float swa = (float) seamWorkAspect;
                        K(0,0) *= swa;
                        K(0,2) *= swa;
                        K(1,1) *= swa;
                        K(1,2) *= swa;

                        _corners[i] = _warper->warp(_images[i], K, _cameras[i].R, INTER_LINEAR, BORDER_REFLECT, _imagesWarped[i]);
                        __android_log_print(ANDROID_LOG_INFO, TAG, "Warp image #%d : %s", i + 1, _imagesPath[i].c_str());
                        _sizes[i] = _imagesWarped[i].size();
                        _warper->warp(_masks[i], K, _cameras[i].R, INTER_NEAREST, BORDER_CONSTANT, _masksWarped[i]);
                }

                for (int i = 0; i < numberImages; ++i)
                        _imagesWarped[i].convertTo(_imagesWarpedF[i], CV_32F);
#ifdef DEBUG
                 __android_log_print(ANDROID_LOG_INFO, TAG, "Warping _images time: %f sec", ((getTickCount() - t) / getTickFrequency()));
#endif
                 return 0;
        }

        /* Compensate exposure errors and find seam _masks. */
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_findSeamMasks
        (JNIEnv* env, jobject obj)
        {
                Ptr<SeamFinder> seamFinder;

#ifdef DEBUG
                int64 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
                __android_log_print(ANDROID_LOG_INFO, TAG, "find seam mask");
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif
                _compensator->feed(_corners, _imagesWarped, _masksWarped);

                 __android_log_print(ANDROID_LOG_INFO, TAG, "Compensating exposure errors time: %f sec", ((getTickCount() - t) / getTickFrequency()));

                 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "Finding seam _masks...");

                if (seamFindType == "no")
                        seamFinder = new detail::NoSeamFinder();
                else if (seamFindType == "voronoi")
                        seamFinder = new detail::VoronoiSeamFinder();
                else if (seamFindType == "gc_color")
                        seamFinder = new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR);
                else if (seamFindType == "gc_colorgrad")
                        seamFinder = new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR_GRAD);
                else if (seamFindType == "dp_color")
                        seamFinder = new detail::DpSeamFinder(DpSeamFinder::COLOR);
                else if (seamFindType == "dp_colorgrad")
                        seamFinder = new detail::DpSeamFinder(DpSeamFinder::COLOR_GRAD);

                if (seamFinder.empty()) {
                        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown seam finder: %s", WARP_TYPE.c_str());
                        return -1;
                }

                seamFinder->find(_imagesWarpedF, _corners, _masksWarped);

                // Release unused memory.
                _images.clear();
                _imagesWarped.clear();
                _imagesWarpedF.clear();
                _masks.clear();
#ifdef DEBUG
                __android_log_print(ANDROID_LOG_INFO, TAG, "Finding seam _masks time: %f sec", ((getTickCount() - t) / getTickFrequency()));
#endif

                return 0;
        }

        /* Compose final panorama. */
        JNIEXPORT jint JNICALL
        Java_fr_ensicaen_panandroid_stitcher_StitcherWrapper_composePanorama
        (JNIEnv* env, jobject obj)
        {
                float blendWidth;
                double composeScale = 1;
                double composeWorkAspect = 1;
                int numberImages = static_cast<int>(_imagesPath.size());
                Mat fullImage;
                Mat image;
                Mat imageWarped;
                Mat imageWarpedS;
                Mat dilatedMask;
                Mat seamMask;
                Mat mask;
                Mat maskWarped;
                Mat K;
                Ptr<Blender> blender;
                Rect roi;
                Size imageSize;
                Size destinationsz;
                Size sz;

#ifdef DEBUG
                int64 tt;
                int64 t = getTickCount();
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
                __android_log_print(ANDROID_LOG_INFO, TAG, "composition panorama");
                __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif


                if (!isComposeScale)
				{
						if (composeMegapix > 0)
								composeScale = min(1.0, sqrt(composeMegapix * 1e6 / fullImage.size().area()));

						isComposeScale = true;

						// Compute relative scales.
						composeWorkAspect = composeScale / workScale;

						// Update warped image scale.
						warpedImageScale *= static_cast<float>(composeWorkAspect);
						_warper = _warperCreator->create(warpedImageScale);

						// Update _corners and _sizes.
						for (int i = 0; i < numberImages; ++i)
						{
								// Update intrinsics.
								_cameras[i].focal *= composeWorkAspect;
								_cameras[i].ppx *= composeWorkAspect;
								_cameras[i].ppy *= composeWorkAspect;

								// Update corner and size.
								sz = _fullImagesSize[i];

								if (std::abs(composeScale - 1) > 1e-1)
								{
										sz.width = cvRound(_fullImagesSize[i].width * composeScale);
										sz.height = cvRound(_fullImagesSize[i].height * composeScale);
								}

								_cameras[i].K().convertTo(K, CV_32F);
								roi = _warper->warpRoi(sz, K, _cameras[i].R);
								_corners[i] = roi.tl();
								_sizes[i] = roi.size();
						}
				}

                for (int img_idx = 0; img_idx < numberImages; ++img_idx)
                {
#ifdef DEBUG
                        __android_log_print(ANDROID_LOG_INFO, TAG, "Compose image #%d : %s", img_idx + 1, _imagesPath[img_idx].c_str());
                        tt = getTickCount();
#endif
                        // Read image and resize it if necessary.
                        fullImage = imread(_imagesPath[img_idx]);

                        if (abs(composeScale - 1) > 1e-1)
                                resize(fullImage, image, Size(), composeScale, composeScale);
                        else
                                image = fullImage;

                        fullImage.release();
                        imageSize = image.size();

                        _cameras[img_idx].K().convertTo(K, CV_32F);

#ifdef DEBUG
						__android_log_print(ANDROID_LOG_INFO, TAG, "resize image time : %f", ((getTickCount() - tt) / getTickFrequency()) );
						tt = getTickCount();
#endif

                        // Warp the current image.
                        _warper->warp(image, K, _cameras[img_idx].R, INTER_LINEAR, BORDER_REFLECT, imageWarped);
#ifdef DEBUG
                        __android_log_print(ANDROID_LOG_INFO, TAG, "wrap image time : %f", ((getTickCount() - tt) / getTickFrequency()) );
                        tt = getTickCount();
#endif

                        // Warp the current image mask.
                        mask.create(imageSize, CV_8U);
                        mask.setTo(Scalar::all(255));
                        _warper->warp(mask, K, _cameras[img_idx].R, INTER_NEAREST, BORDER_CONSTANT, maskWarped);

#ifdef DEBUG
                        __android_log_print(ANDROID_LOG_INFO, TAG, "wrap mask time : %f", ((getTickCount() - tt) / getTickFrequency()) );
                        tt = getTickCount();
#endif

                        // Compensate exposure.
                        _compensator->apply(img_idx, _corners[img_idx], imageWarped, maskWarped);

#ifdef DEBUG
                        __android_log_print(ANDROID_LOG_INFO, TAG, "compensate exposure time : %f", ((getTickCount() - tt) / getTickFrequency()) );
                        tt = getTickCount();
#endif

                        imageWarped.convertTo(imageWarpedS, CV_16S);
                        imageWarped.release();
                        image.release();
                        mask.release();

                        dilate(_masksWarped[img_idx], dilatedMask, Mat());
                        resize(dilatedMask, seamMask, maskWarped.size());
                        maskWarped = seamMask & maskWarped;

                        if (blender.empty())
                        {
                                blender = Blender::createDefault(BLEND_TYPE, tryGPU);
                                destinationsz = resultRoi(_corners, _sizes).size();
                                blendWidth = sqrt(static_cast<float>(destinationsz.area())) * blendStrength / 100.f;

                                if (blendWidth < 1.f)
                                        blender = Blender::createDefault(Blender::NO, tryGPU);
                                else if (BLEND_TYPE == Blender::MULTI_BAND) {
                                        MultiBandBlender* mb = dynamic_cast<MultiBandBlender*>(static_cast<Blender*>(blender));
                                        mb->setNumBands(static_cast<int>(ceil(log(blendWidth)/log(2.)) - 1.));
                                        __android_log_print(ANDROID_LOG_INFO, TAG, "Multi-band blender, number of bands: %d", mb->numBands());
                                } else if (BLEND_TYPE == Blender::FEATHER) {
                                        FeatherBlender* fb = dynamic_cast<FeatherBlender*>(static_cast<Blender*>(blender));
                                        fb->setSharpness(1.f / blendWidth);
                                        __android_log_print(ANDROID_LOG_INFO, TAG, "Feather blender, sharpness: %f", fb->sharpness());
                                }

                                blender->prepare(_corners, _sizes);
                        }
#ifdef DEBUG
                        __android_log_print(ANDROID_LOG_INFO, TAG, "setting up blender time : %f", ((getTickCount() - tt) / getTickFrequency()) );
                        tt = getTickCount();
#endif

                        // Blend the current image.
                        blender->feed(imageWarpedS, maskWarped, _corners[img_idx]);

#ifdef DEBUG
                        __android_log_print(ANDROID_LOG_INFO, TAG, "blending time : %f", ((getTickCount() - tt) / getTickFrequency()) );
                        tt = getTickCount();
#endif
                }

                Mat result, resultMask;
                blender->blend(result, resultMask);
#ifdef DEBUG
                __android_log_print(ANDROID_LOG_INFO, TAG, "Compositing time: %f sec", ((getTickCount() - t) / getTickFrequency()));
                __android_log_print(ANDROID_LOG_INFO, TAG, "Writing image file :%s", _resultPath.c_str());
                imwrite(_resultPath, result);
#endif
                return 0;
        }
}


/**
 * openCVStitcher(JNIEnv* env, jobject obj, jobjectArray arguments)
 * Launch all-in-one openCV stitcher, with given arguments.
 *
 */
/*
extern "C"
{
		__android_log_print(ANDROID_LOG_INFO, TAG, "Warping images (auxiliary)...");

	#ifdef DEBUG
		t = getTickCount();
	#endif

	vector<Point> corners(num_images);
	vector<Mat> masks_warped(num_images);
	vector<Mat> images_warped(num_images);
	vector<Size> sizes(num_images);
	vector<Mat> masks(num_images);

	// Prepare images masks
	for (int i = 0; i < num_images; ++i) {
		masks[i].create(images[i].size(), CV_8U);
		masks[i].setTo(Scalar::all(255));
	}

	// Warp images and their masks
	Ptr<WarperCreator> warper_creator;

	#if defined(HAVE_OPENCV_GPU) && !defined(ANDROID)
	if (try_gpu && gpu::getCudaEnabledDeviceCount() > 0) {
		if (warp_type == "plane") {
				warper_creator = new cv::PlaneWarperGpu();
		} else if (warp_type == "cylindrical") {
				warper_creator = new cv::CylindricalWarperGpu();
		} else if (warp_type == "spherical") {
				warper_creator = new cv::SphericalWarperGpu();
		}
	} else
	#endif
	{
		if (warp_type == "plane") {
				warper_creator = new cv::PlaneWarper();
		} else if (warp_type == "cylindrical") {
				warper_creator = new cv::CylindricalWarper();
		} else if (warp_type == "spherical") {
				warper_creator = new cv::SphericalWarper();
		} else if (warp_type == "fisheye") {
				warper_creator = new cv::FisheyeWarper();
		} else if (warp_type == "stereographic") {
				warper_creator = new cv::StereographicWarper();
		} else if (warp_type == "compressedPlaneA2B1") {
				warper_creator = new cv::CompressedRectilinearWarper(2, 1);
		} else if (warp_type == "compressedPlaneA1.5B1") {
				warper_creator = new cv::CompressedRectilinearWarper(1.5, 1);
		} else if (warp_type == "compressedPlanePortraitA2B1") {
				warper_creator = new cv::CompressedRectilinearPortraitWarper(2, 1);
		} else if (warp_type == "compressedPlanePortraitA1.5B1") {
				warper_creator = new cv::CompressedRectilinearPortraitWarper(1.5, 1);
		} else if (warp_type == "paniniA2B1") {
				warper_creator = new cv::PaniniWarper(2, 1);
		} else if (warp_type == "paniniA1.5B1") {
				warper_creator = new cv::PaniniWarper(1.5, 1);
		} else if (warp_type == "paniniPortraitA2B1") {
				warper_creator = new cv::PaniniPortraitWarper(2, 1);
		} else if (warp_type == "paniniPortraitA1.5B1") {
				warper_creator = new cv::PaniniPortraitWarper(1.5, 1);
		} else if (warp_type == "mercator") {
				warper_creator = new cv::MercatorWarper();
		} else if (warp_type == "transverseMercator") {
				warper_creator = new cv::TransverseMercatorWarper();
		}
	}

	if (warper_creator.empty()) {
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Can't create the following warper: %s", warp_type.c_str());
		return 1;
	}

	Ptr<RotationWarper> warper = warper_creator->create(static_cast<float>(warped_image_scale * seam_work_aspect));

	for (int i = 0; i < num_images; ++i) {
		Mat_<float> K;
		cameras[i].K().convertTo(K, CV_32F);
		float swa = (float)seam_work_aspect;
		K(0,0) *= swa; K(0,2) *= swa;
		K(1,1) *= swa; K(1,2) *= swa;

		corners[i] = warper->warp(images[i], K, cameras[i].R, INTER_LINEAR, BORDER_REFLECT, images_warped[i]);
		sizes[i] = images_warped[i].size();

		warper->warp(masks[i], K, cameras[i].R, INTER_NEAREST, BORDER_CONSTANT, masks_warped[i]);
	}

	vector<Mat> images_warped_f(num_images);

	for (int i = 0; i < num_images; ++i) {
		images_warped[i].convertTo(images_warped_f[i], CV_32F);
	}

	__android_log_print(ANDROID_LOG_INFO, TAG, "Warping images, time: %f sec", ((getTickCount() - t) / getTickFrequency()));

	#ifdef DEBUG
		t = getTickCount();
	#endif

	Ptr<ExposureCompensator> compensator = ExposureCompensator::createDefault(expos_comp_type);
	compensator->feed(corners, images_warped, masks_warped);

	Ptr<SeamFinder> seam_finder;

	if (seam_find_type == "no")
		seam_finder = new detail::NoSeamFinder();
	else if (seam_find_type == "voronoi")
		seam_finder = new detail::VoronoiSeamFinder();
	else if (seam_find_type == "gc_color") {
		#if defined(HAVE_OPENCV_GPU) && !defined(ANDROID)
		if (try_gpu && gpu::getCudaEnabledDeviceCount() > 0)
				seam_finder = new detail::GraphCutSeamFinderGpu(GraphCutSeamFinderBase::COST_COLOR);
		else
		#endif

		seam_finder = new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR);
	} else if (seam_find_type == "gc_colorgrad") {
		#if defined(HAVE_OPENCV_GPU) && !defined(ANDROID)
		if (try_gpu && gpu::getCudaEnabledDeviceCount() > 0)
				seam_finder = new detail::GraphCutSeamFinderGpu(GraphCutSeamFinderBase::COST_COLOR_GRAD);
		else
		#endif

		seam_finder = new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR_GRAD);
	} else if (seam_find_type == "dp_color")
		seam_finder = new detail::DpSeamFinder(DpSeamFinder::COLOR);
	else if (seam_find_type == "dp_colorgrad")
		seam_finder = new detail::DpSeamFinder(DpSeamFinder::COLOR_GRAD);

	if (seam_finder.empty()) {
		__android_log_print(ANDROID_LOG_INFO, TAG, "Can't create the following seam finder: %s", seam_find_type.c_str());
		return 1;
	}

	seam_finder->find(images_warped_f, corners, masks_warped);

	// Release unused memory
	images.clear();
	images_warped.clear();
	images_warped_f.clear();
	masks.clear();

	__android_log_print(ANDROID_LOG_INFO, TAG, "Some stuff on images 2, time: %f sec", ((getTickCount() - t) / getTickFrequency()));
	__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing...");

	#ifdef DEBUG
		t = getTickCount();
	#endif

	Mat img_warped, img_warped_s;
	Mat dilated_mask, seam_mask, mask, mask_warped;
	Ptr<Blender> blender;
	//double compose_seam_aspect = 1;
	double compose_work_aspect = 1;

	for (int img_idx = 0; img_idx < num_images; ++img_idx) {
		__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing image #%d", indices[img_idx] + 1);

		// Read image and resize it if necessary
		full_img = imread(img_names[img_idx]);

		if (!is_compose_scale_set) {
				if (compose_megapix > 0)
						compose_scale = min(1.0, sqrt(compose_megapix * 1e6 / full_img.size().area()));

				is_compose_scale_set = true;

				// Compute relative scales
				//compose_seam_aspect = compose_scale / seam_scale;
				compose_work_aspect = compose_scale / work_scale;

				// Update warped image scale
				warped_image_scale *= static_cast<float>(compose_work_aspect);
				warper = warper_creator->create(warped_image_scale);

				// Update corners and sizes
				for (int i = 0; i < num_images; ++i) {
						// Update intrinsics
						cameras[i].focal *= compose_work_aspect;
						cameras[i].ppx *= compose_work_aspect;
						cameras[i].ppy *= compose_work_aspect;

						// Update corner and size
						Size sz = full_img_sizes[i];

						if (std::abs(compose_scale - 1) > 1e-1) {
								sz.width = cvRound(full_img_sizes[i].width * compose_scale);
								sz.height = cvRound(full_img_sizes[i].height * compose_scale);
						}

						Mat K;
						cameras[i].K().convertTo(K, CV_32F);
						Rect roi = warper->warpRoi(sz, K, cameras[i].R);
						corners[i] = roi.tl();
						sizes[i] = roi.size();
				}
		}

		if (abs(compose_scale - 1) > 1e-1)
				resize(full_img, img, Size(), compose_scale, compose_scale);
		else
				img = full_img;

		full_img.release();
		Size img_size = img.size();

		Mat K;
		cameras[img_idx].K().convertTo(K, CV_32F);

		// Warp the current image
		warper->warp(img, K, cameras[img_idx].R, INTER_LINEAR, BORDER_REFLECT, img_warped);

		// Warp the current image mask
		mask.create(img_size, CV_8U);
		mask.setTo(Scalar::all(255));
		warper->warp(mask, K, cameras[img_idx].R, INTER_NEAREST, BORDER_CONSTANT, mask_warped);

		// Compensate exposure
		compensator->apply(img_idx, corners[img_idx], img_warped, mask_warped);

		img_warped.convertTo(img_warped_s, CV_16S);
		img_warped.release();
		img.release();
		mask.release();

		dilate(masks_warped[img_idx], dilated_mask, Mat());
		resize(dilated_mask, seam_mask, mask_warped.size());
		mask_warped = seam_mask & mask_warped;

		if (blender.empty()) {
				blender = Blender::createDefault(blend_type, try_gpu);
				Size dst_sz = resultRoi(corners, sizes).size();
				float blend_width = sqrt(static_cast<float>(dst_sz.area())) * blend_strength / 100.f;

				if (blend_width < 1.f)
						blender = Blender::createDefault(Blender::NO, try_gpu);
				else if (blend_type == Blender::MULTI_BAND) {
						MultiBandBlender* mb = dynamic_cast<MultiBandBlender*>(static_cast<Blender*>(blender));
						mb->setNumBands(static_cast<int>(ceil(log(blend_width)/log(2.)) - 1.));
						__android_log_print(ANDROID_LOG_INFO, TAG, "Multi-band blender, number of bands: %d", mb->numBands());
				} else if (blend_type == Blender::FEATHER) {
						FeatherBlender* fb = dynamic_cast<FeatherBlender*>(static_cast<Blender*>(blender));
						fb->setSharpness(1.f/blend_width);
						__android_log_print(ANDROID_LOG_INFO, TAG, "Feather blender, sharpness: %f", fb->sharpness());
				}

				blender->prepare(corners, sizes);
		}

		// Blend the current image
		blender->feed(img_warped_s, mask_warped, corners[img_idx]);
	}

	Mat result, result_mask;
	blender->blend(result, result_mask);

	__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing, time: %f sec", ((getTickCount() - t) / getTickFrequency()));
	imwrite(result_name, result);

	__android_log_print(ANDROID_LOG_INFO, TAG, "Finished, total time: %f sec", ((getTickCount() - app_start_time) / getTickFrequency()));
	return 0;
}

}


/*
 * Untouched version.
 *
extern "C"
{
	JNIEXPORT jint JNICALL
	Java_fr_ensicaen_panandroid_stitcher_StitcherActivity_openCVStitcher(
			JNIEnv* env, jobject obj, jobjectArray arguments)
	{
	int argc = env->GetArrayLength(arguments);
	char** argv = new char*[argc];

	for (int i = 0; i < argc; i++) {
		jstring tmp = (jstring) env->GetObjectArrayElement(arguments, i);
		const char* argument = env->GetStringUTFChars(tmp, 0);
		argv[i] = new char[strlen(argument) + 1];
		strcpy(argv[i], argument);
		env->ReleaseStringUTFChars(tmp, argument);
	}

	for (int i = 0; i < argc; i++) {
		__android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", argv[i]);
	}

	#ifdef DEBUG
		int64 app_start_time = getTickCount();
	#endif

	cv::setBreakOnError(true);

	int retval = parseCmdArgs(argc, argv);

	if (retval) {
		return retval;
	}

	// Check if have enough images
	int num_images = static_cast<int>(img_names.size());

	if (num_images < 2) {
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Need more images");
		return -1;
	}

	double work_scale = 1, seam_scale = 1, compose_scale = 1;
	bool is_work_scale_set = false, is_seam_scale_set = false, is_compose_scale_set = false;

	__android_log_print(ANDROID_LOG_INFO, TAG, "Finding features...");

	#ifdef DEBUG
		int64 t = getTickCount();
	#endif

	Ptr<FeaturesFinder> finder;

	if (features_type == "surf") {
		#if defined(HAVE_OPENCV_NONFREE) && defined(HAVE_OPENCV_GPU) && !defined(ANDROID)
				if (try_gpu && gpu::getCudaEnabledDeviceCount() > 0) {
						finder = new SurfFeaturesFinderGpu();
				} else {
		#endif

		finder = new SurfFeaturesFinder();
	} else if (features_type == "orb") {
		finder = new OrbFeaturesFinder();
	} else {
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown 2D features type: %s", features_type.c_str());
		return -1;
	}

	Mat full_img, img;
	vector<ImageFeatures> features(num_images);
	vector<Mat> images(num_images);
	vector<Size> full_img_sizes(num_images);
	double seam_work_aspect = 1;

	for (int i = 0; i < num_images; ++i) {
		full_img = imread(img_names[i]);
		full_img_sizes[i] = full_img.size();

		if (full_img.empty()) {
				__android_log_print(ANDROID_LOG_ERROR, TAG, "Can't open image %s",  img_names[i].c_str());
				return -1;
		}

		if (work_megapix < 0) {
				img = full_img;
				work_scale = 1;
				is_work_scale_set = true;
		} else {
				if (!is_work_scale_set) {
						work_scale = min(1.0, sqrt(work_megapix * 1e6 / full_img.size().area()));
						is_work_scale_set = true;
				}

				resize(full_img, img, Size(), work_scale, work_scale);
		}

		if (!is_seam_scale_set) {
				seam_scale = min(1.0, sqrt(seam_megapix * 1e6 / full_img.size().area()));
				seam_work_aspect = seam_scale / work_scale;
				is_seam_scale_set = true;
		}

		// Information images côte à côte (infos du JSON avec pitch, roll, yaw)
		// Découper le tableau des features pour regrouper les features seulement deux à deux

		(*finder)(img, features[i]);
		features[i].img_idx = i;
		__android_log_print(ANDROID_LOG_INFO, TAG, "Features in image #%d: %d", i + 1, features[i].keypoints.size());

		resize(full_img, img, Size(), seam_scale, seam_scale);
		images[i] = img.clone();
	}

	finder->collectGarbage();
	full_img.release();
	img.release();

	__android_log_print(ANDROID_LOG_INFO, TAG, "Finding features, time: %f sec", ((getTickCount() - t) / getTickFrequency()));
	__android_log_print(ANDROID_LOG_INFO, TAG, "Pairwise matching...");

	#ifdef DEBUG
		t = getTickCount();
	#endif

	vector<MatchesInfo> pairwise_matches;
	BestOf2NearestMatcher matcher(try_gpu, match_conf);
	matcher(features, pairwise_matches);
	matcher.collectGarbage();
	__android_log_print(ANDROID_LOG_INFO, TAG, "Pairwise matching, time: %f sec", ((getTickCount() - t) / getTickFrequency()));

	#ifdef DEBUG
		t = getTickCount();
	#endif

	// Check if we should save matches graph
	if (save_graph) {
		__android_log_print(ANDROID_LOG_INFO, TAG, "Saving matches graph...");
		ofstream f(save_graph_to.c_str());
		f << matchesGraphAsString(img_names, pairwise_matches, conf_thresh);
	}

	// Leave only images we are sure are from the same panorama
	vector<int> indices = leaveBiggestComponent(features, pairwise_matches, conf_thresh);
	vector<Mat> img_subset;
	vector<string> img_names_subset;
	vector<Size> full_img_sizes_subset;

	for (size_t i = 0; i < indices.size(); ++i) {
		img_names_subset.push_back(img_names[indices[i]]);
		img_subset.push_back(images[indices[i]]);
		full_img_sizes_subset.push_back(full_img_sizes[indices[i]]);
	}

	images = img_subset;
	img_names = img_names_subset;
	full_img_sizes = full_img_sizes_subset;

	// Check if we still have enough images
	num_images = static_cast<int>(img_names.size());

	if (num_images < 2) {
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Need more images");
		return -1;
	}

	HomographyBasedEstimator estimator;
	vector<CameraParams> cameras;
	estimator(features, pairwise_matches, cameras);

	for (size_t i = 0; i < cameras.size(); ++i) {
		Mat R;
		cameras[i].R.convertTo(R, CV_32F);
		cameras[i].R = R;
		//__android_log_print(ANDROID_LOG_INFO, TAG, "Initial intrinsics #%d : %s", indices[i] + 1, cameras[i].K());
	}

	Ptr<detail::BundleAdjusterBase> adjuster;

	if (ba_cost_func == "reproj") {
		adjuster = new detail::BundleAdjusterReproj();
	} else if (ba_cost_func == "ray") {
		adjuster = new detail::BundleAdjusterRay();
	} else {
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown bundle adjustment cost function: %s", ba_cost_func.c_str());
		return -1;
	}

	adjuster->setConfThresh(conf_thresh);
	Mat_<uchar> refine_mask = Mat::zeros(3, 3, CV_8U);

	if (ba_refine_mask[0] == 'x') {
		refine_mask(0,0) = 1;
	}

	if (ba_refine_mask[1] == 'x') {
		refine_mask(0,1) = 1;
	}

	if (ba_refine_mask[2] == 'x') {
		refine_mask(0,2) = 1;
	}

	if (ba_refine_mask[3] == 'x') {
		refine_mask(1,1) = 1;
	}

	if (ba_refine_mask[4] == 'x') {
		refine_mask(1,2) = 1;
	}

	adjuster->setRefinementMask(refine_mask);
	(*adjuster)(features, pairwise_matches, cameras);

	// Find median focal length
	vector<double> focals;

	for (size_t i = 0; i < cameras.size(); ++i) {
		// LOGLN("Camera #" << indices[i]+1 << ":\n" << cameras[i].K());
		focals.push_back(cameras[i].focal);
	}

	sort(focals.begin(), focals.end());
	float warped_image_scale;

	if (focals.size() % 2 == 1) {
		warped_image_scale = static_cast<float>(focals[focals.size() / 2]);
	} else {
		warped_image_scale = static_cast<float>(focals[focals.size() / 2 - 1] + focals[focals.size() / 2]) * 0.5f;
	}

	if (do_wave_correct) {
		vector<Mat> rmats;

		for (size_t i = 0; i < cameras.size(); ++i) {
				rmats.push_back(cameras[i].R);
		}

		waveCorrect(rmats, wave_correct);

		for (size_t i = 0; i < cameras.size(); ++i) {
				cameras[i].R = rmats[i];
		}
	}

	__android_log_print(ANDROID_LOG_INFO, TAG, "Some stuff on images, time: %f sec", ((getTickCount() - t) / getTickFrequency()));
	__android_log_print(ANDROID_LOG_INFO, TAG, "Warping images (auxiliary)...");

	#ifdef DEBUG
		t = getTickCount();
	#endif

	vector<Point> corners(num_images);
	vector<Mat> masks_warped(num_images);
	vector<Mat> images_warped(num_images);
	vector<Size> sizes(num_images);
	vector<Mat> masks(num_images);

	// Prepare images masks
	for (int i = 0; i < num_images; ++i) {
		masks[i].create(images[i].size(), CV_8U);
		masks[i].setTo(Scalar::all(255));
	}

	// Warp images and their masks
	Ptr<WarperCreator> warper_creator;

	#if defined(HAVE_OPENCV_GPU) && !defined(ANDROID)
	if (try_gpu && gpu::getCudaEnabledDeviceCount() > 0) {
		if (warp_type == "plane") {
				warper_creator = new cv::PlaneWarperGpu();
		} else if (warp_type == "cylindrical") {
				warper_creator = new cv::CylindricalWarperGpu();
		} else if (warp_type == "spherical") {
				warper_creator = new cv::SphericalWarperGpu();
		}
	} else
	#endif
	{
		if (warp_type == "plane") {
				warper_creator = new cv::PlaneWarper();
		} else if (warp_type == "cylindrical") {
				warper_creator = new cv::CylindricalWarper();
		} else if (warp_type == "spherical") {
				warper_creator = new cv::SphericalWarper();
		} else if (warp_type == "fisheye") {
				warper_creator = new cv::FisheyeWarper();
		} else if (warp_type == "stereographic") {
				warper_creator = new cv::StereographicWarper();
		} else if (warp_type == "compressedPlaneA2B1") {
				warper_creator = new cv::CompressedRectilinearWarper(2, 1);
		} else if (warp_type == "compressedPlaneA1.5B1") {
				warper_creator = new cv::CompressedRectilinearWarper(1.5, 1);
		} else if (warp_type == "compressedPlanePortraitA2B1") {
				warper_creator = new cv::CompressedRectilinearPortraitWarper(2, 1);
		} else if (warp_type == "compressedPlanePortraitA1.5B1") {
				warper_creator = new cv::CompressedRectilinearPortraitWarper(1.5, 1);
		} else if (warp_type == "paniniA2B1") {
				warper_creator = new cv::PaniniWarper(2, 1);
		} else if (warp_type == "paniniA1.5B1") {
				warper_creator = new cv::PaniniWarper(1.5, 1);
		} else if (warp_type == "paniniPortraitA2B1") {
				warper_creator = new cv::PaniniPortraitWarper(2, 1);
		} else if (warp_type == "paniniPortraitA1.5B1") {
				warper_creator = new cv::PaniniPortraitWarper(1.5, 1);
		} else if (warp_type == "mercator") {
				warper_creator = new cv::MercatorWarper();
		} else if (warp_type == "transverseMercator") {
				warper_creator = new cv::TransverseMercatorWarper();
		}
	}

	if (warper_creator.empty()) {
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Can't create the following warper: %s", warp_type.c_str());
		return 1;
	}

	Ptr<RotationWarper> warper = warper_creator->create(static_cast<float>(warped_image_scale * seam_work_aspect));

	for (int i = 0; i < num_images; ++i) {
		Mat_<float> K;
		cameras[i].K().convertTo(K, CV_32F);
		float swa = (float)seam_work_aspect;
		K(0,0) *= swa; K(0,2) *= swa;
		K(1,1) *= swa; K(1,2) *= swa;

		corners[i] = warper->warp(images[i], K, cameras[i].R, INTER_LINEAR, BORDER_REFLECT, images_warped[i]);
		sizes[i] = images_warped[i].size();

		warper->warp(masks[i], K, cameras[i].R, INTER_NEAREST, BORDER_CONSTANT, masks_warped[i]);
	}

	vector<Mat> images_warped_f(num_images);

	for (int i = 0; i < num_images; ++i) {
		images_warped[i].convertTo(images_warped_f[i], CV_32F);
	}

	__android_log_print(ANDROID_LOG_INFO, TAG, "Warping images, time: %f sec", ((getTickCount() - t) / getTickFrequency()));

	#ifdef DEBUG
		t = getTickCount();
	#endif

	Ptr<ExposureCompensator> compensator = ExposureCompensator::createDefault(expos_comp_type);
	compensator->feed(corners, images_warped, masks_warped);

	Ptr<SeamFinder> seam_finder;

	if (seam_find_type == "no")
		seam_finder = new detail::NoSeamFinder();
	else if (seam_find_type == "voronoi")
		seam_finder = new detail::VoronoiSeamFinder();
	else if (seam_find_type == "gc_color") {
		#if defined(HAVE_OPENCV_GPU) && !defined(ANDROID)
		if (try_gpu && gpu::getCudaEnabledDeviceCount() > 0)
				seam_finder = new detail::GraphCutSeamFinderGpu(GraphCutSeamFinderBase::COST_COLOR);
		else
		#endif

		seam_finder = new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR);
	} else if (seam_find_type == "gc_colorgrad") {
		#if defined(HAVE_OPENCV_GPU) && !defined(ANDROID)
		if (try_gpu && gpu::getCudaEnabledDeviceCount() > 0)
				seam_finder = new detail::GraphCutSeamFinderGpu(GraphCutSeamFinderBase::COST_COLOR_GRAD);
		else
		#endif

		seam_finder = new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR_GRAD);
	} else if (seam_find_type == "dp_color")
		seam_finder = new detail::DpSeamFinder(DpSeamFinder::COLOR);
	else if (seam_find_type == "dp_colorgrad")
		seam_finder = new detail::DpSeamFinder(DpSeamFinder::COLOR_GRAD);

	if (seam_finder.empty()) {
		__android_log_print(ANDROID_LOG_INFO, TAG, "Can't create the following seam finder: %s", seam_find_type.c_str());
		return 1;
	}

	seam_finder->find(images_warped_f, corners, masks_warped);

	// Release unused memory
	images.clear();
	images_warped.clear();
	images_warped_f.clear();
	masks.clear();

	__android_log_print(ANDROID_LOG_INFO, TAG, "Some stuff on images 2, time: %f sec", ((getTickCount() - t) / getTickFrequency()));
	__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing...");

	#ifdef DEBUG
		t = getTickCount();
	#endif

	Mat img_warped, img_warped_s;
	Mat dilated_mask, seam_mask, mask, mask_warped;
	Ptr<Blender> blender;
	//double compose_seam_aspect = 1;
	double compose_work_aspect = 1;

	for (int img_idx = 0; img_idx < num_images; ++img_idx) {
		__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing image #%d", indices[img_idx] + 1);

		// Read image and resize it if necessary
		full_img = imread(img_names[img_idx]);

		if (!is_compose_scale_set) {
				if (compose_megapix > 0)
						compose_scale = min(1.0, sqrt(compose_megapix * 1e6 / full_img.size().area()));

				is_compose_scale_set = true;

				// Compute relative scales
				//compose_seam_aspect = compose_scale / seam_scale;
				compose_work_aspect = compose_scale / work_scale;

				// Update warped image scale
				warped_image_scale *= static_cast<float>(compose_work_aspect);
				warper = warper_creator->create(warped_image_scale);

				// Update corners and sizes
				for (int i = 0; i < num_images; ++i) {
						// Update intrinsics
						cameras[i].focal *= compose_work_aspect;
						cameras[i].ppx *= compose_work_aspect;
						cameras[i].ppy *= compose_work_aspect;

						// Update corner and size
						Size sz = full_img_sizes[i];

						if (std::abs(compose_scale - 1) > 1e-1) {
								sz.width = cvRound(full_img_sizes[i].width * compose_scale);
								sz.height = cvRound(full_img_sizes[i].height * compose_scale);
						}

						Mat K;
						cameras[i].K().convertTo(K, CV_32F);
						Rect roi = warper->warpRoi(sz, K, cameras[i].R);
						corners[i] = roi.tl();
						sizes[i] = roi.size();
				}
		}

		if (abs(compose_scale - 1) > 1e-1)
				resize(full_img, img, Size(), compose_scale, compose_scale);
		else
				img = full_img;

		full_img.release();
		Size img_size = img.size();

		Mat K;
		cameras[img_idx].K().convertTo(K, CV_32F);

		// Warp the current image
		warper->warp(img, K, cameras[img_idx].R, INTER_LINEAR, BORDER_REFLECT, img_warped);

		// Warp the current image mask
		mask.create(img_size, CV_8U);
		mask.setTo(Scalar::all(255));
		warper->warp(mask, K, cameras[img_idx].R, INTER_NEAREST, BORDER_CONSTANT, mask_warped);

		// Compensate exposure
		compensator->apply(img_idx, corners[img_idx], img_warped, mask_warped);

		img_warped.convertTo(img_warped_s, CV_16S);
		img_warped.release();
		img.release();
		mask.release();

		dilate(masks_warped[img_idx], dilated_mask, Mat());
		resize(dilated_mask, seam_mask, mask_warped.size());
		mask_warped = seam_mask & mask_warped;

		if (blender.empty()) {
				blender = Blender::createDefault(blend_type, try_gpu);
				Size dst_sz = resultRoi(corners, sizes).size();
				float blend_width = sqrt(static_cast<float>(dst_sz.area())) * blend_strength / 100.f;

				if (blend_width < 1.f)
						blender = Blender::createDefault(Blender::NO, try_gpu);
				else if (blend_type == Blender::MULTI_BAND) {
						MultiBandBlender* mb = dynamic_cast<MultiBandBlender*>(static_cast<Blender*>(blender));
						mb->setNumBands(static_cast<int>(ceil(log(blend_width)/log(2.)) - 1.));
						__android_log_print(ANDROID_LOG_INFO, TAG, "Multi-band blender, number of bands: %d", mb->numBands());
				} else if (blend_type == Blender::FEATHER) {
						FeatherBlender* fb = dynamic_cast<FeatherBlender*>(static_cast<Blender*>(blender));
						fb->setSharpness(1.f/blend_width);
						__android_log_print(ANDROID_LOG_INFO, TAG, "Feather blender, sharpness: %f", fb->sharpness());
				}

				blender->prepare(corners, sizes);
		}

		// Blend the current image
		blender->feed(img_warped_s, mask_warped, corners[img_idx]);
	}

	Mat result, result_mask;
	blender->blend(result, result_mask);

	__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing, time: %f sec", ((getTickCount() - t) / getTickFrequency()));
	imwrite(result_name, result);

	__android_log_print(ANDROID_LOG_INFO, TAG, "Finished, total time: %f sec", ((getTickCount() - app_start_time) / getTickFrequency()));
	return 0;
}

}
*/


/* ********
 * PRIVATE FUNCTIONS
 * *******/


/*
static int parseCmdArgs(int argc, char** argv)
{
	for (int i = 0; i < argc; ++i)
	{
		if (string(argv[i]) == "--preview") {
			preview = true;
		} else if (string(argv[i]) == "--try_gpu") {
			if (string(argv[i + 1]) == "no") {
				try_gpu = false;
			} else if (string(argv[i + 1]) == "yes") {
				try_gpu = true;
			} else {
				__android_log_print(ANDROID_LOG_ERROR, TAG, "Bad --try_gpu flag value");
				return -1;
			}

			i++;
		} else if (string(argv[i]) == "--work_megapix") {
				work_megapix = atof(argv[i + 1]);
				i++;
		} else if (string(argv[i]) == "--seam_megapix") {
				seam_megapix = atof(argv[i + 1]);
				i++;
		} else if (string(argv[i]) == "--compose_megapix") {
				compose_megapix = atof(argv[i + 1]);
				i++;
		} else if (string(argv[i]) == "--result") {
				result_name = argv[i + 1];
				i++;
		} else if (string(argv[i]) == "--features") {
				features_type = argv[i + 1];

				if (features_type == "orb") {
						match_conf = 0.3f;
						i++;
				}
		} else if (string(argv[i]) == "--match_conf") {
				match_conf = static_cast<float>(atof(argv[i + 1]));
				i++;
		} else if (string(argv[i]) == "--conf_thresh") {
				conf_thresh = static_cast<float>(atof(argv[i + 1]));
				i++;
		} else if (string(argv[i]) == "--ba") {
				ba_cost_func = argv[i + 1];
				i++;
		} else if (string(argv[i]) == "--ba_refine_mask") {
				ba_refine_mask = argv[i + 1];

				if (ba_refine_mask.size() != 5) {
						__android_log_print(ANDROID_LOG_ERROR, TAG, "Incorrect refinement mask length");
						return -1;
				}

				i++;
		} else if (string(argv[i]) == "--wave_correct") {
				if (string(argv[i + 1]) == "no") {
						do_wave_correct = false;
				} else if (string(argv[i + 1]) == "horiz") {
						do_wave_correct = true;
						wave_correct = detail::WAVE_CORRECT_HORIZ;
				} else if (string(argv[i + 1]) == "vert") {
						do_wave_correct = true;
						wave_correct = detail::WAVE_CORRECT_VERT;
				} else {
						__android_log_print(ANDROID_LOG_ERROR, TAG, "Bad --wave_correct flag value");
						return -1;
				}

				i++;
		} else if (string(argv[i]) == "--save_graph") {
				save_graph = true;
				save_graph_to = argv[i + 1];
				i++;
		} else if (string(argv[i]) == "--warp") {
				warp_type = string(argv[i + 1]);
				i++;
		} else if (string(argv[i]) == "--expos_comp") {
				if (string(argv[i + 1]) == "no") {
						expos_comp_type = ExposureCompensator::NO;
				} else if (string(argv[i + 1]) == "gain") {
						expos_comp_type = ExposureCompensator::GAIN;
				} else if (string(argv[i + 1]) == "gain_blocks") {
						expos_comp_type = ExposureCompensator::GAIN_BLOCKS;
				} else {
						__android_log_print(ANDROID_LOG_ERROR, TAG, "Bad exposure compensation method");
						return -1;
				}

				i++;
		} else if (string(argv[i]) == "--seam") {
				if (string(argv[i + 1]) == "no" ||
					string(argv[i + 1]) == "voronoi" ||
					string(argv[i + 1]) == "gc_color" ||
					string(argv[i + 1]) == "gc_colorgrad" ||
					string(argv[i + 1]) == "dp_color" ||
					string(argv[i + 1]) == "dp_colorgrad") {
						seam_find_type = argv[i + 1];
				} else {
						__android_log_print(ANDROID_LOG_ERROR, TAG, "Bad seam finding method");
						return -1;
				}

				i++;
		} else if (string(argv[i]) == "--blend") {
				if (string(argv[i + 1]) == "no") {
						blend_type = Blender::NO;
				} else if (string(argv[i + 1]) == "feather") {
						blend_type = Blender::FEATHER;
				} else if (string(argv[i + 1]) == "multiband") {
						blend_type = Blender::MULTI_BAND;
				} else {
						__android_log_print(ANDROID_LOG_ERROR, TAG, "Bad blending method");
						return -1;
				}

				i++;
		} else if (string(argv[i]) == "--blend_strength") {
				blend_strength = static_cast<float>(atof(argv[i + 1]));
				i++;
		} else if (string(argv[i]) == "--output") {
				result_name = argv[i + 1];
				i++;
		}
		else
		{
			img_names.push_back(argv[i]);
		}
	}

	if (preview) {
			compose_megapix = 0.6;
	}

	return 0;
}
*/

