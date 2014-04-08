package fr.ensicaen.panandroid.viewer;

import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.capture.CameraManager;
import fr.ensicaen.panandroid.insideview.Inside3dView;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.ImageView;


public class SensorialRotationButton extends ImageView
{
	/* ******
	 * PARAMETERS
	 * ******/
    @SuppressWarnings("unused")
	private final String TAG = SensorialRotationButton.class.getSimpleName();
	private static final int[] SHUTTER_BUTTON_IDS= new int[]{R.drawable.btn_sensorial_rotation_inactive, R.drawable.btn_sensorial_rotation_active};;

	
	/* ******
	 * CONSTRUCTORS
	 * ******/
	Inside3dView mView;
	
    public SensorialRotationButton(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    public SensorialRotationButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);

    }

    public SensorialRotationButton(Context context ) 
    {
        super(context);
        

    }
    
    public void setParentView(Inside3dView parentView)
    {
    	mView = parentView;
    	boolean sensorialRotation = mView.isSensorialRotationEnabled();

    	setImageResource(SHUTTER_BUTTON_IDS[(sensorialRotation?1:0)]);
    }
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
    	if(mView==null)
    		return false;

		switch (event.getAction()) 
		{
    		case MotionEvent.ACTION_UP :
    			boolean sensorialRotation = !mView.isSensorialRotationEnabled();
    	    	mView.setEnableSensorialRotation(sensorialRotation);
    	    	mView.setEnableTouchRotation(!sensorialRotation);
    	    	
    	    	setImageResource(SHUTTER_BUTTON_IDS[(sensorialRotation?1:0)]);
    	    	break;

		}
    	
    	
    	return (super.onTouchEvent(event));
    }
}