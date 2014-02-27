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

package fr.ensicaen.panandroid.tools;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;


/**
 * Helper class to safe decode bitmaps. Auto subsample bitmaps when no more memory available.
 * @author Nicolas THIERION.
 * TODO : doc and refactor.
 */
public class BitmapDecoder
{

	private BitmapDecoder(){}
	private static int mSamplingRate;
	
	public static Bitmap safeDecodeBitmap(Resources res, int resId, int sampleSize)
	{
		Bitmap bmp;
		BitmapFactory.Options bitmapOptions = new BitmapFactory.Options(); 
		bitmapOptions.inSampleSize = sampleSize;
		try
		{
			 bmp = BitmapFactory.decodeResource(res, resId, bitmapOptions);
			 return bmp;
		}
		catch(OutOfMemoryError e)
		{
			//out of memory => launch garbage collector
			System.gc();
		}
		
		while (bitmapOptions.inSampleSize<32)
		{
			try
			{
				bmp = BitmapFactory.decodeResource(res, resId, bitmapOptions);
				mSamplingRate = bitmapOptions.inSampleSize;
				return bmp;
			}
			catch (OutOfMemoryError e)
			{
				bitmapOptions.inSampleSize *= 2;
			}
		}
		throw new OutOfMemoryError();
		
	}
	public static Bitmap safeDecodeBitmap(Resources res, int resId)
	{
		return safeDecodeBitmap(res, resId, 1);
	}
	
	
	public static Bitmap safeDecodeBitmap(String filename, int sampleSize)
	{
		Bitmap bmp;
		BitmapFactory.Options bitmapOptions = new BitmapFactory.Options(); 
		bitmapOptions.inSampleSize = sampleSize;
		try
		{
			 bmp = BitmapFactory.decodeFile(filename, bitmapOptions);
			 mSamplingRate = bitmapOptions.inSampleSize;
			 return bmp;
		}
		catch(OutOfMemoryError e)
		{
			//out of memory => launch garbage collector
			System.gc();
		}
		
		while (bitmapOptions.inSampleSize<32)
		{
			try
			{
				bmp = BitmapFactory.decodeFile(filename, bitmapOptions);
				mSamplingRate = bitmapOptions.inSampleSize;
				return bmp;
			}
			catch (OutOfMemoryError e)
			{
				bitmapOptions.inSampleSize *= 2;
			}
		}
		throw new OutOfMemoryError();
		
	}

	public static Bitmap safeDecodeBitmap(String filename)
	{
		return safeDecodeBitmap(filename, 1);
	}
	
	
	
	
	
	
	public static Bitmap safeDecodeBitmap(byte[] byteArray, int sampleSize)
	{
		Bitmap bmp;
		BitmapFactory.Options bitmapOptions = new BitmapFactory.Options(); 
		bitmapOptions.inSampleSize = sampleSize;
		try
		{
			 bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length, bitmapOptions);
			 mSamplingRate = bitmapOptions.inSampleSize;
			 return bmp;
		}
		catch(OutOfMemoryError e)
		{
			//out of memory => launch garbage collector
			System.gc();
		}
		
		
		while (bitmapOptions.inSampleSize<32)
		{
			try
			{
				bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length, bitmapOptions);
				mSamplingRate = bitmapOptions.inSampleSize;
				return bmp;
			}
			catch (OutOfMemoryError e)
			{
				bitmapOptions.inSampleSize *= 2;
			}
		}
		throw new OutOfMemoryError();
		
	}

	


	/**
	 * get the last sampling rate used
	 * @return
	 */
	public static int getSampleRate() {
		return mSamplingRate;
	}
}
