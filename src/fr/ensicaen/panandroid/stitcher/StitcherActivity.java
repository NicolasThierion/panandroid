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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
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
    private Context mContext;
    private Handler mHandler;
    private ProgressDialog mProgressDialog;

    /**
     * Called when StitcherActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setTitle("Stitching in progress");
        mProgressDialog.setMessage("In progress");
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mProgressDialog.dismiss();
                Intent intent = new Intent(mContext, FakeCaptureActivity.class);
                startActivity(intent);
                super.handleMessage(msg);
            }
        };

        mProgressDialog.show();

        new Thread() {
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {}

                mHandler.sendEmptyMessage(0);
            }
        }.start();
    }
}
