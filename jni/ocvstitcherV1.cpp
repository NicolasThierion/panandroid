/*
 * Copyright (C) 2013 Nicolas THIERION, Saloua BENSEDDIK, Jean Marguerite.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

#define DEBUG

#if defined(DEBUG) && !defined(ANDROID)
#define debugPrint( ...) {fprintf(stdout, __VA_ARGS__);fflush(stdout);}

#elif defined(DEBUG) && defined(ANDROID)
#define debugPrint( ...) {__android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__);}
#endif


#include <iostream>
#include <fstream>
#include <string>
//#include <opencv2/opencv_modules.hpp>


#include "opencv2/highgui/highgui.hpp"


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



/*

#include "opencv2/modules/stitching/include/autocalib.hpp"
#include "opencv2/modules/stitching/include/blenders.hpp"
#include "opencv2/modules/stitching/include/camera.hpp"
#include "opencv2/modules/stitching/include/exposure_compensate.hpp"
#include "opencv2/modules/stitching/include/motion_estimators.hpp"
#include "opencv2/modules/stitching/include/seam_finders.hpp"
#include "opencv2/modules/stitching/include/util.hpp"
#include "opencv2/modules/stitching/include/warpers.hpp"
#include "opencv2/modules/stitching/include/stitcher.hpp"
*/
using namespace std;
using namespace cv;
using namespace cv::detail;

/*************
 * PARAMETERS
 ************/
/** ????? **/
bool doWaveCorrect = true;

/** ????? **/
bool isComposeScale = false;	//compositor crashes if true

/** Try to use GPU. **/
bool tryGPU = true;

/** Resolution for compositing step. **/
double composeMegapix = 0.5;		//:-1

/** Resolution for seam estimation step in Mpx. **/
double seamMegapix = 0.2;

/** Seam work aspect. **/
double seamWorkAspect = 1;

/** Resolution for image registration step in Mpx. **/
double workMegapix = 0.3;	//:0.6

/** Work scale. **/
static double workScale = 1;

/** Blending strength from [0,100] range. **/
float blendStrength = 5;

/** Threshold for two images are from the same panorama confidence. **/
float confidenceThreshold = 1.f;

/** Confidence for feature matching step. **/
float matchConfidence = 0.3f;

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
string warpType = "spherical";

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

//================ REGISTRATION STEPS ============================
// Find feature in images from internal image array.
int findFeatures()
{
        bool isSeamScale = false;
        bool isWorkScale = false;
        double seamScale = 1;
        Mat fullImage, image;
        Ptr<FeaturesFinder> finder;

#ifdef DEBUG
        int64 t = getTickCount();
        __android_log_print(ANDROID_LOG_INFO, TAG, "=======================");
        __android_log_print(ANDROID_LOG_INFO, TAG, "Find features");
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

// Match features.
int matchFeatures()
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

// Adjust parameters.
int adjustParameters()
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
                // LOGLN("Initial intrinsics #" << indices[i]+1 << ":\n" << _cameras[i].K());
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
                // LOGLN("Camera #" << indices[i]+1 << ":\n" << _cameras[i].K());
                focals.push_back(_cameras[i].focal);
        }

        sort(focals.begin(), focals.end());

        if (focals.size() % 2 == 1)
                warpedImageScale = static_cast<float>(focals[focals.size() / 2]);
        else
                warpedImageScale = static_cast<float>(focals[focals.size() / 2 - 1] + focals[focals.size() / 2]) * 0.5f;

        // Wave correction.
        if (doWaveCorrect) {
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

//================ COMPOSITION STEPS ============================

int warpImages()
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
        if (warpType == "plane")
                _warperCreator = new cv::PlaneWarper();
        else if (warpType == "cylindrical")
                _warperCreator = new cv::CylindricalWarper();
        else if (warpType == "spherical")
                _warperCreator = new cv::SphericalWarper();
        else if (warpType == "fisheye")
                _warperCreator = new cv::FisheyeWarper();
        else if (warpType == "stereographic")
                _warperCreator = new cv::StereographicWarper();
        else if (warpType == "compressedPlaneA2B1")
                _warperCreator = new cv::CompressedRectilinearWarper(2, 1);
        else if (warpType == "compressedPlaneA1.5B1")
                _warperCreator = new cv::CompressedRectilinearWarper(1.5, 1);
        else if (warpType == "compressedPlanePortraitA2B1")
                _warperCreator = new cv::CompressedRectilinearPortraitWarper(2, 1);
        else if (warpType == "compressedPlanePortraitA1.5B1")
                _warperCreator = new cv::CompressedRectilinearPortraitWarper(1.5, 1);
        else if (warpType == "paniniA2B1")
                _warperCreator = new cv::PaniniWarper(2, 1);
        else if (warpType == "paniniA1.5B1")
                _warperCreator = new cv::PaniniWarper(1.5, 1);
        else if (warpType == "paniniPortraitA2B1")
                _warperCreator = new cv::PaniniPortraitWarper(2, 1);
        else if (warpType == "paniniPortraitA1.5B1")
                _warperCreator = new cv::PaniniPortraitWarper(1.5, 1);
        else if (warpType == "mercator")
                _warperCreator = new cv::MercatorWarper();
        else if (warpType == "transverseMercator")
                _warperCreator = new cv::TransverseMercatorWarper();

        if (_warperCreator.empty()) {
                __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown _warper: %s", warpType.c_str());
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

// Compensate exposure errors and find seam _masks.
int findSeamMask()
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
                __android_log_print(ANDROID_LOG_ERROR, TAG, "Unknown seam finder: %s", warpType.c_str());
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

// Compose final panorama.
int composePanorama()
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
        __android_log_print(ANDROID_LOG_INFO, TAG, "image converted" );

        dilate(_masksWarped[img_idx], dilatedMask, Mat());
        __android_log_print(ANDROID_LOG_INFO, TAG, "image dilated" );

        resize(dilatedMask, seamMask, maskWarped.size());
        maskWarped = seamMask & maskWarped;
        __android_log_print(ANDROID_LOG_INFO, TAG, "image resized" );


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

int main(int argc, char* argv[])
{

        return 0;
}

