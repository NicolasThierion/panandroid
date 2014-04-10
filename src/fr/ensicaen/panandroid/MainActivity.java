/*
 * Copyright (C) 2013 Saloua BENSEDDIK, Jean MARGUERITE, Nicolas THIERION
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package fr.ensicaen.panandroid;

import java.io.File;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.capture.CaptureFragment;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.stitcher.StitcherActivity;
import fr.ensicaen.panandroid.viewer.GalleryFragment;

/**
 * Activity through what the user could switch between capture and gallery.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 */
public class MainActivity extends FragmentActivity {
    /**************
     * ATTRIBUTES *
     **************/
    /** Capture part of the activity */
    private CaptureFragment mCapture;

    /** Gallery part of the activity */
    private GalleryFragment mGallery;

    /**
     * Fragments for each of the two primary sections of the application.
     */
    private AppSectionsAdapter mAppSectionsAdapter;

    /**
     * Display two primary sections of the application one at a time.
     */
    private ViewPager mPager;

    /******************
     * PUBLIC METHODS *
     ******************/
    /**
     * Called when MainActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPager = (ViewPager) findViewById(R.id.pager);
        mCapture = new CaptureFragment();
        mGallery = new GalleryFragment();
        mAppSectionsAdapter = new AppSectionsAdapter(getSupportFragmentManager());
        mPager.setAdapter(mAppSectionsAdapter);
    }

    /**
     * Called when the activity has detected the user's press of the back key.
     */
    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getFragments().get(mPager.getCurrentItem())
                instanceof CaptureFragment) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

            alertDialog.setMessage(getString(R.string.exit_capture)).setCancelable(false);

            alertDialog.setPositiveButton(getString(R.string.exit_yes),
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // If we have at least 2 snapshots to stitch, switch to stitcher activity.
                    if (mCapture.getSnapshotManager().getSnapshotsList().size() > 1) {
                        Intent intent = new Intent(MainActivity.this, StitcherActivity.class);

                        intent.putExtra("PROJECT_FILE", mCapture.getWorkingDirectory()
                                + File.separator + SnapshotManager.DEFAULT_JSON_FILENAME);
                        startActivity(intent);

                        // Finish this activity in order to free maximum of memory.
                        finish();
                    } else {
                        finish();
                    }
                }
            });

            alertDialog.setNegativeButton(R.string.exit_no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });

            AlertDialog alert = alertDialog.create();
            alert.show();
        } else {
            mPager.setCurrentItem(0);
        }
    }

    /**
     * Implementation of PagerAdapter that represents each page as a Fragment that is
     * persistently kept in the fragment manager as long as the user can return to the page.
     */
    private class AppSectionsAdapter extends FragmentStatePagerAdapter {
        /**
         * Default constructor of AppSectionsAdapter.
         * @param fm Used to write apps that run on platforms prior to Android 3.0.
         */
        public AppSectionsAdapter(FragmentManager fm) {
            super(fm);
        }

        /**
         * Return the Fragment associated with a specified position.
         * @return Fragment related with specified position.
         */
        @Override
        public Fragment getItem(int i) {
            return i == 0 ? mCapture : mGallery;
        }

        /**
         * Return the number of views available.
         * @return Number of views available.
         */
        @Override
        public int getCount() {
            return 2;
        }
    }
}
