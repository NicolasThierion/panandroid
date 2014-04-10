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

import android.app.Activity;
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

    private static Fragment[] mFragments = new Fragment[2];
    /**
     * Called when MainActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPager = (ViewPager) findViewById(R.id.pager);

        mPager.setAdapter(new AppSectionsAdapter(getSupportFragmentManager()));
        mPager.setOnPageChangeListener(new PageChangeListener());
        
        mFragments[0] = new CaptureFragment() ;
        mFragments[1] = new DummyFragment() ;
    }

    /**
     * Implementation of PagerAdapter that represents each page as a Fragment that is
     * persistently kept in the fragment manager as long as the user can return to the page.
     */
    private static class AppSectionsAdapter extends FragmentPagerAdapter {
        public AppSectionsAdapter(FragmentManager fm) {
            super(fm);
        }

        /**
         * Return the Fragment associated with a specified position.
         */
        @Override
        public Fragment getItem(int i) {
            return mFragments[i];
        }

        /**
         * Return the number of views available.
         * @return Number of views available.
         */
        @Override
        public int getCount() {
            return mFragments.length;
        }

    }

    private static class PageChangeListener implements ViewPager.OnPageChangeListener
    {

		private int mSelectedFragment = 0;

		@Override
		public void onPageScrollStateChanged(int state)
		{
			if(state==ViewPager.SCROLL_STATE_DRAGGING)
			{
				for(int i=0; i<mFragments.length; ++i)
				{
					if(i!=mSelectedFragment)
						
						mFragments[i].onResume();
					
				}
			}
			else if (state == ViewPager.SCROLL_STATE_IDLE)
			{
				for(int i=0; i<mFragments.length; ++i)
				{
					if(i!=mSelectedFragment)
						mFragments[i].onPause();
					
				}
			}
		}

		@Override
		public void onPageScrolled(int position, float positionOffset, int positionOffsetPixel) {
			
			//System.out.println("position "+position+" positionOffset "+positionOffset+ "positionOffsetPixel "+positionOffsetPixel);
			
			/*for(int i=0; i<mFragments.length; ++i)
			{
				if(i==id)
				{
					mFragments[i].onResume();
				}
				else
					mFragments[i].onPause();
			}*/			
		}

		@Override
		public void onPageSelected(int id) {
			mSelectedFragment  = id;
		}
    	
    }
    
    // Only use for debug purpose.
    /**
     * A dummy fragment representing a section of the application,
     * but that simply displays dummy text.
     */
    public static class DummyFragment extends Fragment {
        public static final String APP_SECTION_NUMBER = "section_number";
		private View mRoot;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            mRoot = inflater.inflate(R.layout.dummy_fragment, container, false);
            ((TextView) mRoot.findViewById(R.id.text_dummy)).setText("Gallerie");

            return mRoot;
        }
        @Override
        public void onResume()
        {
        	super.onResume();
        	mRoot.requestLayout();

        }
    }
}
