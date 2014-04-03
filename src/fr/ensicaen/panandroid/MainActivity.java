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
package fr.ensicaen.panandroid;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.capture.CaptureFragment;

/**
 * Activity through what the user could switch between capture and
 * browse gallery.
 */
public class MainActivity extends FragmentActivity {
    /**
     * It will provide fragments for each of the two primary sections of the
     * application. We use a FragmentPagerAdapter derivative, which will keep
     * every loaded fragment in memory.
     */
    private AppSectionsAdapter mAppSectionsAdapter;

    /**
     * Display two primary sections of the application (Capture and Gallery)
     * one at a time.
     */
    private ViewPager mPager;

    /**
     * Called when MainActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPager = (ViewPager) findViewById(R.id.pager);

        mAppSectionsAdapter = new AppSectionsAdapter(getSupportFragmentManager());
        mPager.setAdapter(mAppSectionsAdapter);
    }

    /**
     * Implementation of PagerAdapter that represents each page as a Fragment that is
     * persistently kept in the fragment manager as long as the user can return to the page.
     */
    public static class AppSectionsAdapter extends FragmentPagerAdapter {
        public AppSectionsAdapter(FragmentManager fm) {
            super(fm);
        }

        /**
         * Return the Fragment associated with a specified position.
         */
        @Override
        public Fragment getItem(int i) {
            Fragment fragment;

            fragment = (i == 0) ? new CaptureFragment() : new DummyFragment();
            return fragment;
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

    // Only use for debug purpose.
    /**
     * A dummy fragment representing a section of the application,
     * but that simply displays dummy text.
     */
    public static class DummyFragment extends Fragment {
        public static final String APP_SECTION_NUMBER = "section_number";

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View root = inflater.inflate(R.layout.dummy_fragment, container, false);

            ((TextView) root.findViewById(R.id.text_dummy)).setText("Gallerie");

            return root;
        }
    }
}
