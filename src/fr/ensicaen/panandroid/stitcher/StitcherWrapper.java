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

package fr.ensicaen.panandroid.stitcher;

/**
 * StitcherWrapper class provides a wrapper between Java and JNI class.
 * @version 0.0.1 - Fri Mar 21 2014
 */
public class StitcherWrapper {
    /**
     * Load JNI library.
     */
    static {
        System.loadLibrary("ocvstitcher");
    }

    /**
     * Store images path for OpenCV.
     * @param files Path to all images in the current folder.
     * @return Result of images storage.
     */
    public native int storeImagesPath(Object[] files, float[][] orientations);

    /**
     * Find features in all bunch of images.
     * @return Result of finding features.
     */
    public native int findFeatures();

    /**
     * Match features.
     * @return Result of match features.
     */
    public native int matchFeatures();

    /**
     * Adjust different kinds of parameters.
     * @return Result of adjust parameters.
     */
    public native int adjustParameters();

    /**
     * Warp images.
     * @return Result of warp images.
     */
    public native int warpImages();

    /**
     * Find seam masks.
     * @return Result of find seam masks.
     */
    public native int findSeamMasks();

    /**
     * Compose final panorama.
     * @return Result of compose panorama.
     */
    public native int composePanorama();

	
}
