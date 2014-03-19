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

/* *********
 * DEFAULT PARAMETERS
 * ********/
#define DEBUG
#define TAG "OpenCV stitcher"

/** bunch of all images to stitch **/
vector<string> img_names;

/** **/
bool preview = false;

/** if stitcher have to try to use GPU **/
bool try_gpu = true;

/** **/
double work_megapix = 0.6;

/** **/
double seam_megapix = 0.1;

/** **/
double compose_megapix = -1;

/** **/
float conf_thresh = 1.f;

/** **/
string features_type = "orb";

/** **/
string ba_cost_func = "ray";

/** **/
string ba_refine_mask = "xxxxx";

/** **/
bool do_wave_correct = true;

/** **/
WaveCorrectKind wave_correct = detail::WAVE_CORRECT_HORIZ;

/** **/
bool save_graph = false;

/** **/
std::string save_graph_to;

/** **/
string warp_type = "spherical";

/** **/
int expos_comp_type = ExposureCompensator::GAIN_BLOCKS;

/** Matcher threshold??**///TODO
float match_conf = 0.3f;

/** **/
string seam_find_type = "gc_color";

/** **/
int blend_type = Blender::MULTI_BAND;

/** **/
float blend_strength = 5;

/** **/
string result_name = "result.jpg";

/** **/
static char** imagesPath;



/* *********
 * PROTOTYPES
 * *********/
static int parseCmdArgs(int argc, char** argv);

/* *********
 * PUBLIC FUNCTIONS
 * ********/
/**
 * storeImagesPath(JNIEnv* env, jobject obj, jobjectArray files)
 * Store the images path from array 'files' into stitcher's memory.
 **/
extern "C"
{
	JNIEXPORT jint JNICALL
	Java_fr_ensicaen_panandroid_stitcher_StitcherActivity_storeImagesPath
	(JNIEnv* env, jobject obj, jobjectArray files)
	{
		jstring tmp;
		const char* path;
		int numImages = env->GetArrayLength(files);

		imagesPath = new char*[numImages];

		for (int i = 0; i < numImages; ++i)
		{
			tmp = (jstring) env->GetObjectArrayElement(files, i);
			path = env->GetStringUTFChars(tmp, 0);
			imagesPath[i] = new char[strlen(path) + 1];
			strcpy(imagesPath[i], path);
			env->ReleaseStringUTFChars(tmp, path);
		}

		return 0;
	}
}


/**
 * openCVStitcher(JNIEnv* env, jobject obj, jobjectArray arguments)
 * Launch all-in-one openCV stitcher, with given arguments.
 *
 */
extern "C"
{
	JNIEXPORT jint JNICALL
	Java_fr_ensicaen_panandroid_stitcher_StitcherActivity_openCVStitcher(
			JNIEnv* env, jobject obj, jobjectArray arguments)
	{
		int argc = env->GetArrayLength(arguments);
		char** argv = new char*[argc];

		//fetch & convert arguments to UTF-8 argv array.
		for (int i = 0; i < argc; i++)
		{
			jstring tmp = (jstring) env->GetObjectArrayElement(arguments, i);
			const char* argument = env->GetStringUTFChars(tmp, 0);
			argv[i] = new char[strlen(argument) + 1];
			strcpy(argv[i], argument);
			env->ReleaseStringUTFChars(tmp, argument);
		}

		//print args on log.
		for (int i = 0; i < argc; i++)
		{
			__android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", argv[i]);
		}

		//init benchmark timer.
		#ifdef DEBUG
			int64 app_start_time = getTickCount();
		#endif

		cv::setBreakOnError(true);

		//parse args & exit on error.
		int retval = parseCmdArgs(argc, argv);
		if (retval)
		{
			return retval;
		}

		// Check if have enough images
		int num_images = static_cast<int>(img_names.size());
		if (num_images < 2)
		{
			__android_log_print(ANDROID_LOG_ERROR, TAG, "Need more images");
			return -1;
		}


		//===============================================
		//finding features
		double work_scale = 1, seam_scale = 1, compose_scale = 1;
		bool is_work_scale_set = false, is_seam_scale_set = false, is_compose_scale_set = false;
		__android_log_print(ANDROID_LOG_INFO, TAG, "Finding features...");

		#ifdef DEBUG
			int64 t = getTickCount();
		#endif

		// build one of several finders, based on different finding engines.
		Ptr<FeaturesFinder> finder;
		if (features_type == "surf")
		{
			//seems we will never get there because on android?
			#if defined(HAVE_OPENCV_NONFREE) && defined(HAVE_OPENCV_GPU) && !defined(ANDROID)
				if (try_gpu && gpu::getCudaEnabledDeviceCount() > 0)
				{
					finder = new SurfFeaturesFinderGpu();
				}
				else
				//{
				//TODO :
				//seems strange code there. How can it compile?

			#endif

			finder = new SurfFeaturesFinder();
		}
		else if (features_type == "orb")
		{
			finder = new OrbFeaturesFinder();
		}
		else
		{
			__android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown 2D features type: %s", features_type.c_str());
			return -1;
		}

	Mat full_img, img;
	vector<ImageFeatures> features(num_images);
	vector<Mat> images(num_images);
	vector<Size> full_img_sizes(num_images);
	double seam_work_aspect = 1;

	//proccess "find features on all images"
	for (int i = 0; i < num_images; ++i)
	{
		//read image
		full_img = imread(img_names[i]);
		full_img_sizes[i] = full_img.size();
		if (full_img.empty())
		{
			__android_log_print(ANDROID_LOG_ERROR, TAG, "Can't open image %s",  img_names[i].c_str());
			return -1;
		}

		//Create a working image, subscaled by work_scale or work megapix
		if (work_megapix < 0)
		{
			img = full_img;
			work_scale = 1;
			is_work_scale_set = true;
		}
		else
		{
			if (!is_work_scale_set)
			{
				work_scale = min(1.0, sqrt(work_megapix * 1e6 / full_img.size().area()));
				is_work_scale_set = true;
			}

			resize(full_img, img, Size(), work_scale, work_scale);
		}

		if (!is_seam_scale_set)
		{
			seam_scale = min(1.0, sqrt(seam_megapix * 1e6 / full_img.size().area()));
			seam_work_aspect = seam_scale / work_scale;
			is_seam_scale_set = true;
		}

		//find features in the current subscaled image
		(*finder)(img, features[i]);
		features[i].img_idx = i;
		__android_log_print(ANDROID_LOG_INFO, TAG, "Features in image #%d: %d", i + 1, features[i].keypoints.size());

		//and finaly store the image subscaled by seam_subscale.
		resize(full_img, img, Size(), seam_scale, seam_scale);
		images[i] = img.clone();
	}

	//clean up workspace
	finder->collectGarbage();
	full_img.release();
	img.release();

	__android_log_print(ANDROID_LOG_INFO, TAG, "Finding features, time: %f sec", ((getTickCount() - t) / getTickFrequency()));
	__android_log_print(ANDROID_LOG_INFO, TAG, "Pairwise matching...");

	//findFeatures done!!
	//===============================================
	//start matching.
	#ifdef DEBUG
		t = getTickCount();
	#endif


	vector<MatchesInfo> pairwise_matches;

	//construct a feature matcher
	BestOf2NearestMatcher matcher(try_gpu, match_conf);//TODO : adjust parameters and benchmark
	matcher(features, pairwise_matches); //TODO : call another matcher method.
	matcher.collectGarbage();


	__android_log_print(ANDROID_LOG_INFO, TAG, "Pairwise matching, time: %f sec", ((getTickCount() - t) / getTickFrequency()));
	//matching done


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
	//matching done !!
	//===============================================
	//starting linear transformation

	//TODO
	//should skip this step and manually create rotation matrix R with the known pitch/yaw.roll??
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

