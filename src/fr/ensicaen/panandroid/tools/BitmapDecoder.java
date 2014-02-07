package fr.ensicaen.panandroid.tools;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class BitmapDecoder
{

	private BitmapDecoder(){}
	
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
	
}
