package fr.ensicaen.panandroid.capture;

import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.capture.CameraManager;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;


public class ShutterButton extends ImageView
{
	/* ******
	 * PARAMETERS
	 * ******/
    @SuppressWarnings("unused")
	private final String TAG = ShutterButton.class.getSimpleName();
	private static final int SHUTTER_BUTTON_ID= R.drawable.btn_shutter_photo;

	/* ******
	 * ATTRIBUTES
	 * ******/
	private CameraManager mCameraManager;
	
	
	/* ******
	 * CONSTRUCTORS
	 * ******/
    public ShutterButton(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        initShutterButton(context);
    }

    public ShutterButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initShutterButton(context);
    }

    public ShutterButton(Context context) 
    {
        super(context);
        initShutterButton(context);
    }
    
    
    private void initShutterButton(Context context)
    {
        super.setImageDrawable(getResources().getDrawable(SHUTTER_BUTTON_ID));
        mCameraManager=CameraManager.getInstance(context);
    }
    
    /**
     * When the shutterButton is pressed, it tells the CameraManager to take a snapshot. 
     * If it is a long press, it pops-up an exit window
     */
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
    	
    	if (event.getActionMasked() == MotionEvent.ACTION_UP)
        {
        	mCameraManager.takeSnapshot();
        } 

        return (super.onTouchEvent(event));
    }
}