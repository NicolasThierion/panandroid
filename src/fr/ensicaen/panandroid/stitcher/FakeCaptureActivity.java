/*
 * ENSICAEN
 * 6 Boulevard Marechal Juin
 * F-14050 Caen Cedex
 *
 * This file is owned by ENSICAEN students.
 * No portion of this code may be reproduced, copied
 * or revised without written permission of the authors.
 */

package fr.ensicaen.panandroid.stitcher;

import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import fr.ensicaen.panandroid.R;

/**
 * FakeCaptureActivity class provides a simple way to takes pictures
 * in order to stitch them in a panorama using OpenCV library features.
 * This class is devoted to disappear in favor of the other one in
 * capture package.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Feb 01 2014
 */
public class FakeCaptureActivity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "FakeCaptureActivity";
    private int mSnapshotNumber = 1;
    private Mat mSnapshot;
    private CameraBridgeViewBase mJavaCamera;
    private Context mContext = this;

    /**
     * Basic implementation of LoaderCallbackInterface.
     */
    private BaseLoaderCallback mBaseLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
            case LoaderCallbackInterface.SUCCESS:
                Log.i(TAG, "OpenCV loaded successfully");
                mJavaCamera.enableView();
                break;
            default:
                super.onManagerConnected(status);
            }
        }
    };

    /**
     * Called when FakeCaptureActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Button mSnap, mStitch;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.fakecapture);
        mJavaCamera = (CameraBridgeViewBase) findViewById(R.id.camera);
        mJavaCamera.setVisibility(SurfaceView.VISIBLE);
        mJavaCamera.setCvCameraViewListener(this);
        mSnap = (Button) findViewById(R.id.snap);
        mStitch = (Button) findViewById(R.id.stitch);
        //mStitch.setEnabled(false);

        mSnap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSnapshotNumber > 1) {
                    mStitch.setEnabled(true);
                }

                snap();
            }
        });

        mStitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, StitcherActivity.class);
                startActivity(intent);
            }
        });
    }

    /**
     * Called for your activity to start interacting with the user.
     */
    @Override
    protected void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8, this,
                mBaseLoaderCallBack);
    }

    /**
     * This method is invoked when camera preview has started.
     * After this method is invoked the frames will start to be delivered
     * to client via the onCameraFrame() callback.
     * @param width Width of the frame
     * @param height Height of the frame
     */
    @Override
    public void onCameraViewStarted(int width, int height) {}

    /**
     * This method is invoked when camera preview has been stopped
     * for some reason. No frames will be delivered via onCameraFrame()
     * callback after this method is called.
     */
    @Override
    public void onCameraViewStopped() {}

    /**
     * This method is invoked when delivery of the frame needs to be done.
     * The returned values - is a modified frame which needs to be displayed
     * on the screen.
     * @param inputFrame Current frame of the camera
     */
    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mSnapshot = inputFrame.rgba();
        return inputFrame.rgba();
    }

    /**
     * Takes a snapshot and save it in Panandroid folder.
     */
    public void snap() {
        File directory;
        Mat mStoreSnapshot = new Mat();

        directory = new File(Environment.getExternalStorageDirectory()
                + File.separator + "Panandroid");

        if (!directory.exists()) {
            directory.mkdirs();
        }

        Imgproc.cvtColor(mSnapshot, mStoreSnapshot, Imgproc.COLOR_RGBA2BGR, 3);
        Highgui.imwrite(directory + File.separator + "snap" + mSnapshotNumber++
                + ".jpg", mStoreSnapshot);
    }
}
