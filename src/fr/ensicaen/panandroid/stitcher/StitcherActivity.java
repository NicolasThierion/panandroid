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

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * StitcherActivity class provides a view on the progress of the building
 * of the panorama.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Feb 01 2014
 */
public class StitcherActivity extends Activity {
    private final MyHandler mHandler = new MyHandler(this);
    private ProgressDialog mProgressDialog;

    /**
     * Called when StitcherActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Stitching in progress");
        mProgressDialog.setMessage("In progress");
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.show();

        new Thread() {
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {}

                mProgressDialog.dismiss();
                mHandler.sendEmptyMessage(0);
            }
        }.start();
    }

    /**
     * Called for your activity to start interacting with the user.
     */
    @Override
    protected void onResume() {
        super.onResume();
        System.loadLibrary("ocvstitcher");
    }

    /**
     * Instances of static inner classes do not hold an implicit
     * reference to their outer class.
     */
    private static class MyHandler extends Handler {
        private final WeakReference<StitcherActivity> mActivity;

        public MyHandler(StitcherActivity activity) {
            mActivity = new WeakReference<StitcherActivity>(activity);
        }

        /**
         * Subclasses must implement this to receive messages.
         */
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            StitcherActivity activity = mActivity.get();
            Intent intent = new Intent(activity, FakeCaptureActivity.class);
            activity.startActivity(intent);
        }
    }
}
