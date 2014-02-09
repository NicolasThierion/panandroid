package fr.ensicaen.panandroid.tools;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

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
	public static int getSamplingRate() {
		return mSamplingRate;
	}
}
