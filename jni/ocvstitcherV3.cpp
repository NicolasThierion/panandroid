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

#include <iostream>
#include <fstream>
#include <string>

#include <opencv2/highgui/highgui.hpp>
#include <opencv2/opencv_modules.hpp>
#include <opencv2/highgui/highgui.hpp>
#include "opencv2/stitching/detail/autocalib.hpp"
#include "opencv2/stitching/detail/blenders.hpp"
#include "opencv2/stitching/detail/camera.hpp"
#include "opencv2/stitching/detail/exposure_compensate.hpp"
#include "opencv2/stitching/detail/matchers.hpp"
#include "opencv2/stitching/detail/motion_estimators.hpp"
#include "opencv2/stitching/detail/seam_finders.hpp"
#include "opencv2/stitching/detail/util.hpp"
#include "opencv2/stitching/detail/warpers.hpp"
#include "opencv2/stitching/warpers.hpp"

using namespace std;
using namespace cv;
using namespace cv::detail;

#define TAG "OpenCV stitcher"
#define ENABLE_LOG true

/*************
 * STEPS & PROGRESS VALUES
 ************/
int FINDER_STEP = 15;
int MATCHER_STEP = 10;
int ESTIMATOR_STEP = 5;
int ADJUSTER_STEP = 5;
int WARPER_STEP = 10;
int COMPENSATOR_STEP = 20;
int SEAM_STEP = 10;
int COMPOSITOR_STEP = 30;


/*************
 * PARAMETERS
 ************/


/** ??? **/
bool preview = false;

/** don't support cuda at this time ... **/
bool try_cuda = false;

/** neither openCL **/
bool try_ocl = false;

/** working resolution **/
double work_megapix = 0.35;

/** seam finding resolution **/
double seam_megapix = 0.2;

/** final panorama resolution **/
double compose_megapix = 1.0;

/** Threshold for two images are from the same panorama confidence. **/
float conf_thresh = 0.7f;	// frequent crashes when < 0.7

/** Bundle adjustment cost function. reproj don't seems suitable for spherical pano**/
string ba_cost_func = "ray"; 	//["reproj", "ray" : "ray"]

/** Set refinement mask for bundle adjustment. It looks like 'x_xxx'
    where 'x' means refine respective parameter and '_' means don't
    refine one, and has the following format:
    <fx><skew><ppx><aspect><ppy>. The default mask is 'xxxxx'. If bundle
    adjustment doesn't support estimation of selected parameter then
    the respective flag is ignored. **/
string ba_refine_mask = "xxxxx";

/** if we should keep horizon straight **/
bool do_wave_correct = true;

WaveCorrectKind wave_correct = detail::WAVE_CORRECT_HORIZ;
bool save_graph = false;
std::string save_graph_to;

/** Warp surface type. **/
string warp_type = "spherical";

/** Exposure compensation method. **/
int expos_comp_type = ExposureCompensator::GAIN;

/** Confidence for feature matching step. **/
float match_conf = 0.27f;

/** Seam estimation method. **/
string seam_find_type = "dp_colorgrad";	//dp_** is WAY faster!!!

/** Blending method. **/
int blend_type = Blender::MULTI_BAND;

/** Blending strength from [0,100] range. **/
float blend_strength = 5;


/** orb featureFinder parameters **/
Size ORB_GRID_SIZE = Size(1,1);
size_t ORB_FEATURES_N = 3500;

/*************
 * ATTRIBUTES
 ************/

/** final panorama name **/
static string _resultPath;

/** loaded images loaded **/
vector<String> _imagesPath;
int _nbImages;

/** current progression of the stitching **/
float _progress = -1;

/** mask used to know what images should we match together **/
static Mat _matchingMask;

/** indices of used images **/
vector<int> _indices;

float _progressStep = 1;

int composePanorama()
{
	_progress = 0;

#if ENABLE_LOG
	__android_log_print(ANDROID_LOG_INFO, TAG, "Compose panorama...");
    int64 app_start_time = getTickCount();
#endif

    cv::setBreakOnError(true);

    // Check if have enough images
    _nbImages = static_cast<int>(_imagesPath.size());
    if (_nbImages < 2)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Finding features...");
        return -1;
    }

    double work_scale = 1, seam_scale = 1, compose_scale = 1;
    bool is_work_scale_set = false, is_seam_scale_set = false, is_compose_scale_set = false;

    // ================ Finding features... ==================
#if ENABLE_LOG
    __android_log_print(ANDROID_LOG_INFO, TAG, "Finding features...");
    int64 t = getTickCount();
#endif

    _progressStep = ((float)FINDER_STEP / (float)_nbImages);
    Ptr<FeaturesFinder> finder = new OrbFeaturesFinder(ORB_GRID_SIZE, ORB_FEATURES_N);
    Mat full_img, img;
    vector<ImageFeatures> features(_nbImages);
    vector<Mat> images(_nbImages);
    vector<Size> full_img_sizes(_nbImages);
    double seam_work_aspect = 1;

    for (int i = 0; i < _nbImages; ++i)
    {
        full_img = imread(_imagesPath[i]);
        full_img_sizes[i] = full_img.size();

        if (full_img.empty())
        {
        	__android_log_print(ANDROID_LOG_ERROR, TAG, "Can't open image %s", _imagesPath[i].c_str());
            return -1;
        }
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

        (*finder)(img, features[i]);
        features[i].img_idx = i;
        LOGLN("Features in image #" << i+1 << ": " << features[i].keypoints.size());
    	__android_log_print(ANDROID_LOG_INFO, TAG, "Features in image #%d ; %d", i+1,features[i].keypoints.size());

        resize(full_img, img, Size(), seam_scale, seam_scale);
        images[i] = img.clone();

        _progress+=_progressStep;
    }

    finder->collectGarbage();
    full_img.release();
    img.release();

	__android_log_print(ANDROID_LOG_INFO, TAG, "Finding features, time: %f sec",((getTickCount() - t) / getTickFrequency()));

    // ================ Pairwise matching... ==================
#if ENABLE_LOG
	__android_log_print(ANDROID_LOG_INFO, TAG, "Pairwise matching");
    t = getTickCount();
#endif


    vector<MatchesInfo> pairwise_matches;
    BestOf2NearestMatcher matcher(try_cuda, match_conf);
    matcher(features, pairwise_matches, _matchingMask);
    matcher.collectGarbage();
    _progress+=MATCHER_STEP;

    LOGLN("Pairwise matching, time: " << ((getTickCount() - t) / getTickFrequency()) << " sec");
	__android_log_print(ANDROID_LOG_INFO, TAG, "Pairwise matching, time: %f sec",((getTickCount() - t) / getTickFrequency()));



    // Check if we should save matches graph
    if (save_graph)
    {
        LOGLN("Saving matches graph...");
        ofstream f(save_graph_to.c_str());
        f << matchesGraphAsString(_imagesPath, pairwise_matches, conf_thresh);
    }

    // Leave only images we are sure are from the same panorama
    _indices = leaveBiggestComponent(features, pairwise_matches, conf_thresh);



    vector<Mat> img_subset;
    vector<String> _imagesPath_subset;
    vector<Size> full_img_sizes_subset;
    for (size_t i = 0; i < _indices.size(); ++i)
    {
        _imagesPath_subset.push_back(_imagesPath[_indices[i]]);
        img_subset.push_back(images[_indices[i]]);
        full_img_sizes_subset.push_back(full_img_sizes[_indices[i]]);
    }

    images = img_subset;
    _imagesPath = _imagesPath_subset;
    full_img_sizes = full_img_sizes_subset;

    // Check if we still have enough images
    _nbImages = static_cast<int>(_imagesPath.size());
    if (_nbImages < 2)
    {
        LOGLN("Need more images");
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "Need more images");

        return -1;
    }
    // ================ estimate homography... ==================
	__android_log_print(ANDROID_LOG_INFO, TAG, "estimate homography");

    HomographyBasedEstimator estimator;
    vector<CameraParams> cameras;
    estimator(features, pairwise_matches, cameras);


    for (size_t i = 0; i < cameras.size(); ++i)
    {
        Mat R;
        cameras[i].R.convertTo(R, CV_32F);
        cameras[i].R = R;
        LOGLN("Initial intrinsics #" << indices[i]+1 << ":\n" << cameras[i].K());
    	__android_log_print(ANDROID_LOG_INFO, TAG, "Initial intrinsics #%d\n",_indices[i]+1);


    }
    _progress+=ESTIMATOR_STEP;

    // ================ adjuster... ==================
    Ptr<detail::BundleAdjusterBase> adjuster;
    if (ba_cost_func == "reproj") adjuster = new detail::BundleAdjusterReproj();
    else if (ba_cost_func == "ray") adjuster =  new detail::BundleAdjusterRay();
    else
    {
        cout << "Unknown bundle adjustment cost function: '" << ba_cost_func << "'.\n";
        return -1;
    }
    adjuster->setConfThresh(conf_thresh);
    Mat_<uchar> refine_mask = Mat::zeros(3, 3, CV_8U);
    if (ba_refine_mask[0] == 'x') refine_mask(0,0) = 1;
    if (ba_refine_mask[1] == 'x') refine_mask(0,1) = 1;
    if (ba_refine_mask[2] == 'x') refine_mask(0,2) = 1;
    if (ba_refine_mask[3] == 'x') refine_mask(1,1) = 1;
    if (ba_refine_mask[4] == 'x') refine_mask(1,2) = 1;
    adjuster->setRefinementMask(refine_mask);
	__android_log_print(ANDROID_LOG_INFO, TAG, "adjusting bundle..");

    (*adjuster)(features, pairwise_matches, cameras);
    _progress+=ADJUSTER_STEP;


    // Find median focal length
	__android_log_print(ANDROID_LOG_INFO, TAG, "Find median focal length");

    vector<double> focals;
    for (size_t i = 0; i < cameras.size(); ++i)
    {
        LOGLN("Camera #" << indices[i]+1 << ":\n" << cameras[i].K());

        focals.push_back(cameras[i].focal);
    }

    sort(focals.begin(), focals.end());
    float warped_image_scale;
    if (focals.size() % 2 == 1)
        warped_image_scale = static_cast<float>(focals[focals.size() / 2]);
    else
        warped_image_scale = static_cast<float>(focals[focals.size() / 2 - 1] + focals[focals.size() / 2]) * 0.5f;

    if (do_wave_correct)
    {
        vector<Mat> rmats;
        for (size_t i = 0; i < cameras.size(); ++i)
            rmats.push_back(cameras[i].R);
        waveCorrect(rmats, wave_correct);
        for (size_t i = 0; i < cameras.size(); ++i)
            cameras[i].R = rmats[i];
    }
    // ================ Warping images... ==================

#if ENABLE_LOG
    LOGLN("Warping images (auxiliary)... ");
   	__android_log_print(ANDROID_LOG_INFO, TAG, "Warping images (auxiliary)... ");

    t = getTickCount();
#endif

    vector<Point> corners(_nbImages);
    vector<Mat> masks_warped(_nbImages);
    vector<Mat> images_warped(_nbImages);
    vector<Size> sizes(_nbImages);
    vector<Mat> masks(_nbImages);

    // Preapre images masks
    for (int i = 0; i < _nbImages; ++i)
    {
        masks[i].create(images[i].size(), CV_8U);
        masks[i].setTo(Scalar::all(255));
    }

    // Warp images and their masks

    Ptr<WarperCreator> warper_creator;
    /*
    if (try_ocl)
    {
        if (warp_type == "plane")
            warper_creator = Ptr<cv::PlaneWarperOcl>();
        else if (warp_type == "cylindrical")
            warper_creator = Ptr<cv::CylindricalWarperOcl>();
        else if (warp_type == "spherical")
            warper_creator = Ptr<cv::SphericalWarperOcl>();
    }*/
#ifdef HAVE_OPENCV_CUDAWARPING
    else if (try_cuda && cuda::getCudaEnabledDeviceCount() > 0)
    {
        if (warp_type == "plane")
            warper_creator = new cv::PlaneWarperGpu();
        else if (warp_type == "cylindrical")
            warper_creator = new cv::CylindricalWarperGpu();
        else if (warp_type == "spherical")
            warper_creator = new cv::SphericalWarperGpu();
    }
    else
#endif
    {
        if (warp_type == "plane")
            warper_creator = new cv::PlaneWarper();
        else if (warp_type == "cylindrical")
            warper_creator = new cv::CylindricalWarper();
        else if (warp_type == "spherical")
            warper_creator = new cv::SphericalWarper();
        else if (warp_type == "fisheye")
            warper_creator = new cv::FisheyeWarper();
        else if (warp_type == "stereographic")
            warper_creator = new cv::StereographicWarper();
        /*
        else if (warp_type == "compressedPlaneA2B1")
            warper_creator = Ptr<cv::CompressedRectilinearWarper>(2.0f, 1.0f);
        else if (warp_type == "compressedPlaneA1.5B1")
            warper_creator = Ptr<cv::CompressedRectilinearWarper>(1.5f, 1.0f);
        else if (warp_type == "compressedPlanePortraitA2B1")
            warper_creator = Ptr<cv::CompressedRectilinearPortraitWarper>(2.0f, 1.0f);
        else if (warp_type == "compressedPlanePortraitA1.5B1")
            warper_creator = Ptr<cv::CompressedRectilinearPortraitWarper>(1.5f, 1.0f);
        else if (warp_type == "paniniA2B1")
            warper_creator = Ptr<cv::PaniniWarper>(2.0f, 1.0f);
        else if (warp_type == "paniniA1.5B1")
            warper_creator = Ptr<cv::PaniniWarper>(1.5f, 1.0f);
        else if (warp_type == "paniniPortraitA2B1")
            warper_creator = Ptr<cv::PaniniPortraitWarper>(2.0f, 1.0f);
        else if (warp_type == "paniniPortraitA1.5B1")
            warper_creator = Ptr<cv::PaniniPortraitWarper>(1.5f, 1.0f);
        else if (warp_type == "mercator")
            warper_creator = Ptr<cv::MercatorWarper>();
        else if (warp_type == "transverseMercator")
            warper_creator = Ptr<cv::TransverseMercatorWarper>();*/
    }

    if (!warper_creator)
    {
        cout << "Can't create the following warper '" << warp_type << "'\n";
        return 1;
    }

    Ptr<RotationWarper> warper = warper_creator->create(static_cast<float>(warped_image_scale * seam_work_aspect));
    _progressStep = (float)WARPER_STEP/(float)_nbImages;
    for (int i = 0; i < _nbImages; ++i)
    {
        Mat_<float> K;
        cameras[i].K().convertTo(K, CV_32F);
        float swa = (float)seam_work_aspect;
        K(0,0) *= swa; K(0,2) *= swa;
        K(1,1) *= swa; K(1,2) *= swa;

        corners[i] = warper->warp(images[i], K, cameras[i].R, INTER_LINEAR, BORDER_REFLECT, images_warped[i]);
        sizes[i] = images_warped[i].size();

        warper->warp(masks[i], K, cameras[i].R, INTER_NEAREST, BORDER_CONSTANT, masks_warped[i]);
        _progress+=_progressStep;
    }

    vector<Mat> images_warped_f(_nbImages);
    for (int i = 0; i < _nbImages; ++i)
        images_warped[i].convertTo(images_warped_f[i], CV_32F);

    images_warped.clear();

    LOGLN("Warping images, time: " << ((getTickCount() - t) / getTickFrequency()) << " sec");
	__android_log_print(ANDROID_LOG_INFO, TAG, "Warping images, time: %f sec" ,((getTickCount() - t) / getTickFrequency()) );


    // ================ Compensate exposure... ==================
	__android_log_print(ANDROID_LOG_INFO, TAG, "Compensate exposure");
    Ptr<ExposureCompensator> compensator = ExposureCompensator::createDefault(expos_comp_type);
    //TODO : to remove
    //compensator->feed(corners, images_warped, masks_warped);
    compensator->feed(corners, images_warped_f, masks_warped);
    _progress+=COMPENSATOR_STEP;

    Ptr<SeamFinder> seam_finder;
    if (seam_find_type == "no")
        seam_finder = new detail::NoSeamFinder();
    else if (seam_find_type == "voronoi")
        seam_finder = new detail::VoronoiSeamFinder();
    else if (seam_find_type == "gc_color")
    {
#ifdef HAVE_OPENCV_CUDA
        if (try_cuda && cuda::getCudaEnabledDeviceCount() > 0)
            seam_finder = new detail::GraphCutSeamFinderGpu(GraphCutSeamFinderBase::COST_COLOR);
        else
#endif
            seam_finder = new detail::GraphCutSeamFinder (GraphCutSeamFinderBase::COST_COLOR);

    }
    else if (seam_find_type == "gc_colorgrad")
    {
#ifdef HAVE_OPENCV_CUDA
        if (try_cuda && cuda::getCudaEnabledDeviceCount() > 0)
            seam_finder = new detail::GraphCutSeamFinderGpu(GraphCutSeamFinderBase::COST_COLOR_GRAD);
        else
#endif
            seam_finder = new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR_GRAD);
    }
    else if (seam_find_type == "dp_color")
        seam_finder = new detail::DpSeamFinder(DpSeamFinder::COLOR);
    else if (seam_find_type == "dp_colorgrad")
        seam_finder = new detail::DpSeamFinder(DpSeamFinder::COLOR_GRAD);
    if (!seam_finder)
    {
        cout << "Can't create the following seam finder '" << seam_find_type << "'\n";
        return 1;
    }
    // ================ finding seam... ==================

	__android_log_print(ANDROID_LOG_INFO, TAG, "finding seam");

    seam_finder->find(images_warped_f, corners, masks_warped);
    _progress+=SEAM_STEP;

    // Release unused memory
    images.clear();
    images_warped_f.clear();
    masks.clear();


    // ================ Compositing... ==================
#if ENABLE_LOG
    LOGLN("Compositing...");
	__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing..." );
    t = getTickCount();
#endif

    Mat img_warped, img_warped_s;
    Mat dilated_mask, seam_mask, mask, mask_warped;
    Ptr<Blender> blender;
    //double compose_seam_aspect = 1;
    double compose_work_aspect = 1;
    _progressStep = (float)COMPOSITOR_STEP/(float)_nbImages;
    for (int img_idx = 0; img_idx < _nbImages; ++img_idx)
    {
        LOGLN("Compositing image #" << indices[img_idx]+1);
    	__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing image #%i" , _indices[img_idx]+1 );

        // Read image and resize it if necessary
        full_img = imread(_imagesPath[img_idx]);
        if (!is_compose_scale_set)
        {
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
            for (int i = 0; i < _nbImages; ++i)
            {
                // Update intrinsics
                cameras[i].focal *= compose_work_aspect;
                cameras[i].ppx *= compose_work_aspect;
                cameras[i].ppy *= compose_work_aspect;

                // Update corner and size
                Size sz = full_img_sizes[i];
                if (std::abs(compose_scale - 1) > 1e-1)
                {
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
        img.release();

        // Warp the current image mask
        mask.create(img_size, CV_8U);
        mask.setTo(Scalar::all(255));
        warper->warp(mask, K, cameras[img_idx].R, INTER_NEAREST, BORDER_CONSTANT, mask_warped);
        mask.release();

        // Compensate exposure
        compensator->apply(img_idx, corners[img_idx], img_warped, mask_warped);

        img_warped.convertTo(img_warped_s, CV_16S);
        img_warped.release();

        dilate(masks_warped[img_idx], dilated_mask, Mat());
        resize(dilated_mask, seam_mask, mask_warped.size());
        mask_warped = seam_mask & mask_warped;

        if (!blender)
        {
            blender = Blender::createDefault(blend_type, try_cuda);
            Size dst_sz = resultRoi(corners, sizes).size();
            float blend_width = sqrt(static_cast<float>(dst_sz.area())) * blend_strength / 100.f;
            if (blend_width < 1.f)
                blender = Blender::createDefault(Blender::NO, try_cuda);
            else if (blend_type == Blender::MULTI_BAND)
            {
				MultiBandBlender* mb = dynamic_cast<MultiBandBlender*>(static_cast<Blender*>(blender));
                mb->setNumBands(static_cast<int>(ceil(log(blend_width)/log(2.)) - 1.));
                LOGLN("Multi-band blender, number of bands: " << mb->numBands());
            }
            else if (blend_type == Blender::FEATHER)
            {
				FeatherBlender* fb = dynamic_cast<FeatherBlender*>(static_cast<Blender*>(blender));
                fb->setSharpness(1.f/blend_width);
                LOGLN("Feather blender, sharpness: " << fb->sharpness());
            }
            blender->prepare(corners, sizes);
        }

        // Blend the current image
        blender->feed(img_warped_s, mask_warped, corners[img_idx]);
        _progress+=_progressStep;
    }

    Mat result, result_mask;
    blender->blend(result, result_mask);

    LOGLN("Compositing, time: " << ((getTickCount() - t) / getTickFrequency()) << " sec");
	__android_log_print(ANDROID_LOG_INFO, TAG, "Compositing, time:%f sec ",((getTickCount() - t) / getTickFrequency()));


    imwrite(_resultPath, result);

    LOGLN("Finished, total time: " << ((getTickCount() - app_start_time) / getTickFrequency()) << " sec");
	__android_log_print(ANDROID_LOG_INFO, TAG, "Finished, total time:%f sec ",((getTickCount() - t) / getTickFrequency()));

    return 0;
}


#ifdef TO_REMOVE
/* Warp images. */
int warpImages()
{
	__android_log_print(ANDROID_LOG_INFO, TAG, "wraping images");

	return 0;

}

/* Compensate exposure errors and find seam masks. */
int findSeamMask()
{
	__android_log_print(ANDROID_LOG_INFO, TAG, "finding seam mask");
	return 0;
}

int findFeatures()
{
	__android_log_print(ANDROID_LOG_INFO, TAG, "finding features");
	return 0;
}

/**
 * Step 2 :
 * Math features.
 */
int matchFeatures()
{
	__android_log_print(ANDROID_LOG_INFO, TAG, "matching features");
    return 0;
}

/* Adjust parameters. */
int adjustParameters()
{
	__android_log_print(ANDROID_LOG_INFO, TAG, "adjusting camera parameters");

	return 0;
}
#endif
int getProgress()
{
	return (int)_progress;
}

