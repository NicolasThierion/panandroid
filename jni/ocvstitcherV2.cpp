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
//M*/

#define DEBUG
#define CUSTOM_STITCHER

#include <iostream>
#include <fstream>
#include <string>

#include <opencv2/opencv_modules.hpp>
#include <opencv2/highgui/highgui.hpp>

#ifdef CUSTOM_STITCHER

#include "opencv2/modules/stitching/include/opencv2/stitching/detail/autocalib.hpp"
#include "opencv2/modules/stitching/include/opencv2/stitching/detail/blenders.hpp"
#include "opencv2/modules/stitching/include/opencv2/stitching/detail/camera.hpp"
#include "opencv2/modules/stitching/include/opencv2/stitching/detail/exposure_compensate.hpp"
#include "opencv2/modules/stitching/include/opencv2/stitching/detail/motion_estimators.hpp"
#include "opencv2/modules/stitching/include/opencv2/stitching/detail/seam_finders.hpp"
#include "opencv2/modules/stitching/include/opencv2/stitching/detail/util.hpp"
#include "opencv2/modules/stitching/include/opencv2/stitching/detail/warpers.hpp"
#include "opencv2/modules/stitching/include/opencv2/stitching/stitcher.hpp"
#else
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
#include <opencv2/stitching/stitcher.hpp>
#endif

using namespace std;
using namespace cv;
using namespace cv::detail;

#define TAG "OpenCV stitcher"


/* ****
 * PRIVATE PROTOTYPES DEFINITIONS
 * ****/
static Ptr<WarperCreator> buildWarper(string);
static Ptr<FeaturesFinder> buildFeaturesFinder(string);
static Ptr<BundleAdjusterBase> buildBundleAdjuster(string);
static Ptr<ExposureCompensator> buildExposureCompensator(int);
static Ptr<Blender> buildBlender(int ,  vector<Point> , vector<Size> , int );

static Ptr<detail::FeaturesMatcher> buildFeaturesMatcher();


/* ****
 * PARAMETERS
 * ***/

/** Warp surface type. **/
string WARP_TYPE = "spherical";

/** finder type. **/
string FINDER_TYPE = "orb" ;				// {"surf", "orb" : "orb" }

/** Adjuster type **/
string BUNDLE_ADJUSTER_TYPE = "ray" ;			// {"reproj", "ray" : "ray" }

/** wave correction type. **/
bool ENABLE_WAVE_CORRECTION=true;		//:true
enum WaveCorrectKind WAVE_CORRECTION_TYPE=detail::WAVE_CORRECT_HORIZ;	//{WAVE_CORRECT_VERT, WAVE_CORRECT_HORIZ : WAVE_CORRECT_HORIZ}

/** exposure compensator type **/
int EXPOSURE_COMPENSATOR_TYPE = ExposureCompensator::GAIN_BLOCKS; //{GAIN_BLOCKS, BLOCK, NO : GAIN_BLOCKS}

/** Blending method. **/
int BLENDER_TYPE = Blender::MULTI_BAND; //{MULTI_BAND, FEATHER, NO : BlenderMULTI_BAND;};


/** Pictures resolution for finding the seam **/
float SEAM_ESTIMATION_RESOL = 0.08f;		// :0.1f

/** ?? **/
float REGISTRATION_RESOL = 0.08f; 		// :0.6f

/**Pictures resolution for the composition step. **/
float COMPOSITION_RESOL = 0.6f; 		// :0.6f

/** Threshold for two images are from the same panorama confidence. Lower value decrease precision. **/
float PANO_CONFIDENCE_THRESH = 0.7f; 	//:1.0f /!\ too low value (<0.5) can cause crashes!!!

/** Blending strength from [0,100] range. **/
float BLENDER_STRENGTH = 5;	//5

float MATCHER_CONF = 0.45; //0.65
int MATCHER_THRESH1 = 6;
int MATCHER_THRESH2 = 6;

bool TRY_GPU=true;

/* ***************
 * GLOBAL VARIABLES
 * ***************/
/* ***
 * Stitcher components
 * ***/
Ptr<cv::Stitcher> _stitcher;

/** warper (spherical) **/
Ptr<WarperCreator> _warperCreator;

/** features finder **/
Ptr<FeaturesFinder> _featuresFinder;

/** exposure compensator **/
Ptr<ExposureCompensator> _exposureCompensator;

/** blender **/
Ptr<Blender> _blender;

/** bundle adjuster **/
Ptr<detail::BundleAdjusterBase> _bundleAdjuster;

/** feature matcher **/
Ptr<detail::FeaturesMatcher> _featuresMatcher;


/* ***
 * Stitcher data
 * ***/
/** Number of images **/
static int _nbImages;

/** Images path. **/
static vector<string> _imagesPath;

/** loaded images **/
static vector <Mat> _images;

static Mat _matchingMask;

/** Where store the result image. **/
static string _resultPath;

/** final composed panorama **/
static Mat _pano;



/* ********
 * CONSTRUCTOR
 * ********/
/**
 * Step 1:
 * Find feature in images from internal image array.
 *
 * findFeatures()
 */
int findFeatures()
{
	//TODO
	return 0;
}

/**
 * Step 2 :
 * Math features.
 */
int matchFeatures()
{
    return 0;
}

/* Adjust parameters. */
int adjustParameters()
{
#ifdef DEBUG
	int64 t = getTickCount();
	__android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
	__android_log_print(ANDROID_LOG_INFO, TAG, "adjusting parameters");
	__android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif
	_warperCreator = buildWarper(WARP_TYPE);
	if(_warperCreator == 0)
	{
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot build warper of unknown type %s", WARP_TYPE.c_str());
		return -1;
	}
	__android_log_print(ANDROID_LOG_INFO, TAG, "featuresFinder..");

	_featuresFinder = buildFeaturesFinder(FINDER_TYPE);
	if(_featuresFinder == 0)
	{
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot build featureFinder of unknown type %s", FINDER_TYPE.c_str());
		return -1;
	}
	_featuresMatcher = buildFeaturesMatcher();
	if(_featuresMatcher == 0)
		{
			__android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot build feature matcher of unknown type %s", FINDER_TYPE.c_str());
			return -1;
		}

	__android_log_print(ANDROID_LOG_INFO, TAG, "bundleAdjuster..");
	_bundleAdjuster= buildBundleAdjuster(BUNDLE_ADJUSTER_TYPE);
	if(_bundleAdjuster == 0)
	{
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown bundle adjustment cost function: %s", BUNDLE_ADJUSTER_TYPE.c_str());
		return -1;
	}
	__android_log_print(ANDROID_LOG_INFO, TAG, "setting params..");

	_exposureCompensator = buildExposureCompensator(EXPOSURE_COMPENSATOR_TYPE);
	if(_exposureCompensator == 0)
	{
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown _exposureCompensatortype %d", EXPOSURE_COMPENSATOR_TYPE);
		return -1;
	}



	_stitcher = new cv::Stitcher(Stitcher::createDefault(TRY_GPU));

	//convert images into mat.
	vector<string>::iterator it, itEnd = _imagesPath.end();
	for(it = _imagesPath.begin(); it != itEnd; ++it)
	{
		_images.push_back(imread((*it)));
	}

	_stitcher->setWarper(_warperCreator);
	_stitcher->setFeaturesFinder(_featuresFinder);
	_stitcher->setRegistrationResol(REGISTRATION_RESOL);
	_stitcher->setSeamEstimationResol(SEAM_ESTIMATION_RESOL);
	_stitcher->setCompositingResol(COMPOSITION_RESOL);
	_stitcher->setPanoConfidenceThresh(PANO_CONFIDENCE_THRESH);
	_stitcher->setWaveCorrection(ENABLE_WAVE_CORRECTION);
	_stitcher->setWaveCorrectKind(WAVE_CORRECTION_TYPE);
	//_stitcher->setMatchingMask(_matchingMask);
	_stitcher->setFeaturesMatcher(_featuresMatcher);

	//TODO : adjust params
	_stitcher->setBundleAdjuster(_bundleAdjuster);
	return 0;

}



/* Warp images. */
int warpImages()
{
	return 0;

}

/* Compensate exposure errors and find seam masks. */
int findSeamMask()
{
	//TODO
	return 0;
}

/* Compose final panorama. */
int composePanorama()
{
#ifdef DEBUG
	int64 t = getTickCount();
	__android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
	__android_log_print(ANDROID_LOG_INFO, TAG, "stitching %d images", _images.size());
	__android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
#endif


/*
	_blender = buildBlender(BLENDER_TYPE);
		if(_blender == 0)
		{
			__android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown _exposureCompensatortype %d", EXPOSURE_COMPENSATOR_TYPE);
			return -1;
		}
	Stitcher::Status;

	status = _stitcher->estimateTransform(images);


	status = _stitcher->setBlender(_blender);


	 status = _stitcher->composePanorama();*/

#ifdef DEBUG
	__android_log_print(ANDROID_LOG_INFO, TAG, "Estimating transform");
#endif
	Stitcher::Status status = _stitcher->estimateTransform(_images);
	if (status != Stitcher::OK)
		return status;

#ifdef DEBUG
	__android_log_print(ANDROID_LOG_INFO, TAG, "Composing pano");
#endif
	status = _stitcher->composePanorama(_pano);


	imwrite(_resultPath, _pano);


#ifdef DEBUG
	__android_log_print(ANDROID_LOG_INFO, TAG, "Stitching time: %f sec", ((getTickCount() - t) / getTickFrequency()));
#endif
	switch (status)
	{
	case Stitcher::OK:
		return 0;
	default :
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Stitcher failed (need more images)");
		return -1;
	}
}


static Ptr<WarperCreator> buildWarper(string warpType)
{
	Ptr<WarperCreator> warperCreator;
	// Warp _images and their _masks.
	if (warpType == "plane")
		warperCreator = new cv::PlaneWarper();
	else if (warpType == "cylindrical")
		warperCreator = new cv::CylindricalWarper();
	else if (warpType == "spherical")
		warperCreator = new cv::SphericalWarper();
	else if (warpType == "fisheye")
		warperCreator = new cv::FisheyeWarper();
	else if (warpType == "stereographic")
		warperCreator = new cv::StereographicWarper();
	else if (warpType == "compressedPlaneA2B1")
		warperCreator = new cv::CompressedRectilinearWarper(2, 1);
	else if (warpType == "compressedPlaneA1.5B1")
		warperCreator = new cv::CompressedRectilinearWarper(1.5, 1);
	else if (warpType == "compressedPlanePortraitA2B1")
		warperCreator = new cv::CompressedRectilinearPortraitWarper(2, 1);
	else if (warpType == "compressedPlanePortraitA1.5B1")
		warperCreator = new cv::CompressedRectilinearPortraitWarper(1.5, 1);
	else if (warpType == "paniniA2B1")
		warperCreator = new cv::PaniniWarper(2, 1);
	else if (warpType == "paniniA1.5B1")
		warperCreator = new cv::PaniniWarper(1.5, 1);
	else if (warpType == "paniniPortraitA2B1")
		warperCreator = new cv::PaniniPortraitWarper(2, 1);
	else if (warpType == "paniniPortraitA1.5B1")
		warperCreator = new cv::PaniniPortraitWarper(1.5, 1);
	else if (warpType == "mercator")
		warperCreator = new cv::MercatorWarper();
	else if (warpType == "transverseMercator")
		warperCreator = new cv::TransverseMercatorWarper();

	if (warperCreator.empty()) {
		__android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown warper: %s", warpType.c_str());
		return 0;
	}

	return warperCreator;

}

//TODO : adjust features params
static Ptr<FeaturesFinder> buildFeaturesFinder(string featuresType)
{
    Ptr<FeaturesFinder> finder;
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
		return 0;
	}

	return finder;
}

static Ptr<detail::BundleAdjusterBase> buildBundleAdjuster(string bundleAdjustment)
{
    Ptr<detail::BundleAdjusterBase> adjuster;
	if (bundleAdjustment == "reproj")
		adjuster = new detail::BundleAdjusterReproj();
	else if (bundleAdjustment == "ray")
		adjuster = new detail::BundleAdjusterRay();
	else {
		return 0;
	}
	return adjuster;
}

static Ptr<detail::Blender> buildBlender(int blenderType, vector<Point> corners, vector<Size> sizes, int blendStrength)
{
	Ptr<Blender> blender = Blender::createDefault(blenderType, TRY_GPU);
	Size destinationsz = resultRoi(corners, sizes).size();

	int blendWidth = sqrt(static_cast<float>(destinationsz.area())) * blendStrength / 100.f;

	if (blendWidth < 1.f)
			blender = Blender::createDefault(Blender::NO, TRY_GPU);
	else if (blenderType == Blender::MULTI_BAND)
	{
			MultiBandBlender* mb = dynamic_cast<MultiBandBlender*>(static_cast<Blender*>(blender));
			mb->setNumBands(static_cast<int>(ceil(log(blendWidth)/log(2.)) - 1.));
			__android_log_print(ANDROID_LOG_INFO, TAG, "Multi-band blender, number of bands: %d", mb->numBands());
	}
	else if (blenderType == Blender::FEATHER)
	{
			FeatherBlender* fb = dynamic_cast<FeatherBlender*>(static_cast<Blender*>(blender));
			fb->setSharpness(1.f / blendWidth);
			__android_log_print(ANDROID_LOG_INFO, TAG, "Feather blender, sharpness: %f", fb->sharpness());
	}
	blender->prepare(corners, sizes);
	return blender;
}

static Ptr<detail::ExposureCompensator> buildExposureCompensator(int exposureCompensationType)
{
    Ptr<detail::ExposureCompensator> compensator;
    compensator = ExposureCompensator::createDefault(exposureCompensationType);
    return compensator;
}

static Ptr<detail::FeaturesMatcher> buildFeaturesMatcher()
{
	Ptr<detail::FeaturesMatcher>  matcher = new BestOf2NearestMatcher(TRY_GPU, MATCHER_CONF, MATCHER_THRESH1, MATCHER_THRESH2);
	return matcher;
}




