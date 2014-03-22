package fr.ensicaen.panandroid;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;


public class CaptureButton extends ImageView {
    public CaptureButton(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        initCaptureButton(context);
    }

    public CaptureButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initCaptureButton(context);
    }

    public CaptureButton(Context context) {
        super(context);
        initCaptureButton(context);
    }

    public void initCaptureButton(Context context) {
        super.setImageDrawable(getResources().getDrawable(
                R.id.btn_capture));
    }
}
