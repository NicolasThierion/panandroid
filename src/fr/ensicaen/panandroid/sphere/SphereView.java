/*
 * ENSICAEN
 * 6 Boulevard Marechal Juin
 * F-14050 Caen Cedex
 *
 * This file is owned by ENSICAEN students.
 * No portion of this code may be reproduced, copied
 * or revised without written permission of the authors.
 */

package fr.ensicaen.panandroid.sphere;

import java.util.Stack;

import fr.ensicaen.panandroid.sensor.SensorFusionManager;
import junit.framework.Assert;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Creates the GL view.
 * @author Nicolas Thierion <nicolas.thierion@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Jan 04 2014
 */
public class SphereView extends GLSurfaceView implements SensorEventListener
{
    private static final String TAG = SphereView.class.getSimpleName();

    private static final long INERTIA_TIME_INTERVAL = 200; // milliseconds
    private static final int REST_THRESHOLD = 5; // pixels

    /** GL renderer. **/
    private SphereRenderer mRenderer;

    /** List of all touch events captured. **/
    private Stack<EventInfo> mMotionEvents;

    /** If touch event are enabled. **/
    private boolean mIsTouchScrollEnabled = false;

    /** If inertia is enabled. **/
    private boolean mIsInertiaEnabled = false;

    /** If sensory rotation is enabled. **/
    private boolean mIsSensorialRotationEnabled;

    /** The sensorFusion manager for sensory rotation. **/
    private SensorFusionManager mSensorFusionManager = null;

    public SphereView(Context context, Sphere sphere) {
        super(context);

        mRenderer = new SphereRenderer(context, sphere);
        setRenderer(mRenderer);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (!isTouchScrollEnabled()) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_CANCEL :
                mMotionEvents = null;
                return true;
            case MotionEvent.ACTION_DOWN :
                stopInertialScrolling();

                mMotionEvents = new Stack<EventInfo>();
                mMotionEvents.push(new EventInfo(event.getX(), event.getY(),
                        event.getEventTime()));

                return true;
            case MotionEvent.ACTION_MOVE :
                Assert.assertTrue(mMotionEvents != null);

                if (mMotionEvents.size() > 0) {
                    EventInfo lastEvent = mMotionEvents.lastElement();

                    float distX = event.getX() - lastEvent.x;
                    float distY = event.getY() - lastEvent.y;

                    rotate(distX, distY);
                }

                mMotionEvents.push(new EventInfo(event.getX(), event.getY(),
                        event.getEventTime()));

    		return true;
            case MotionEvent.ACTION_UP :
                Assert.assertTrue(mMotionEvents != null);

                mMotionEvents.push(new EventInfo(event.getX(), event.getY(),
                        event.getEventTime()));
    		startInertialScrolling();

    		return true;
        }

        return false;
    }

    @Override
    public void onResume() {
        if (this.isSensorialRotationEnabled()) {
            mSensorFusionManager.onResume();
        }
    }

    @Override
    public void onPause() {
        if(this.isSensorialRotationEnabled()) {
            this.mSensorFusionManager.onPauseOrStop();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float pitch = mSensorFusionManager.getPitch();
        float yaw = mSensorFusionManager.getYaw();

        mRenderer.setRotation(pitch, yaw);
    }

    /**
     * Set the sphere rotation
     * @param deltaYaw How many degree to rotate horizontally.
     * @param deltaPitch How many degrees to rotate vertically.
     */
    public void rotate(float deltaYaw, float deltaPitch) {
        int surfaceWidth = mRenderer.getSurfaceWidth();
        int surfaceHeight = mRenderer.getSurfaceHeight();
        float aspect = (float) surfaceWidth/ (float) surfaceHeight;
	float rotationLatitudeDeg = mRenderer.getPitch();
	float rotationLongitudeDeg = mRenderer.getYaw();
	float hFovDeg = mRenderer.getHFovDeg();

	float deltaLongitute = deltaYaw / surfaceWidth * hFovDeg;
	rotationLongitudeDeg -= deltaLongitute;

	float fovYDeg = hFovDeg / aspect;
	float deltaLatitude = deltaPitch / surfaceHeight * fovYDeg;
	rotationLatitudeDeg -= deltaLatitude;

	mRenderer.setRotation(rotationLatitudeDeg, rotationLongitudeDeg);
    }

    public void enableInertialRotation(boolean enabled) {
        mIsInertiaEnabled = enabled;
    }

    public boolean isInertialScrollEnabled() {
        return mIsInertiaEnabled;
    }

    public void setInertiaFriction(float coef) {
        mRenderer.setRotationFriction(coef);
    }

    public void enableTouchRotation(boolean enable) {
        mIsTouchScrollEnabled = enable;
    }

    public boolean isTouchScrollEnabled() {
        return mIsTouchScrollEnabled;
    }

    public boolean enableSensorialRotation(boolean enable) {
        if (enable) {
            // Creates a new sensor manager.
            mSensorFusionManager = new SensorFusionManager(this.getContext());

            // Register it to the system.
            boolean initialized = mSensorFusionManager.registerListener();

            if (!initialized) {
                mSensorFusionManager = null;
            } else {
                mSensorFusionManager.addSensorEventListener(this);
            }

            mIsSensorialRotationEnabled = initialized;

            return initialized;
        } else if (mSensorFusionManager != null) {
            mIsSensorialRotationEnabled = false;
            mSensorFusionManager.removeSensorEventListener(this);
            mSensorFusionManager.onPauseOrStop();
            mSensorFusionManager = null;
        }

        return true;
    }

    public boolean isSensorialRotationEnabled() {
        return mIsSensorialRotationEnabled;
    }

    private void stopInertialScrolling() {
        mRenderer.stopInertialScrolling();
    }

    private void startInertialScrolling() {
        if (!isInertialScrollEnabled()) {
            Log.w(TAG, "Abording inertial scrolling cause it is disabled");
            return;
        }

        Assert.assertTrue(mMotionEvents != null);

        if (mMotionEvents.size() < 2) {
            return;
        }

        EventInfo event1 = mMotionEvents.pop();
        long tEnd = event1.time;
        float directionX = 0.0f;
        float directionY = 0.0f;
        EventInfo event2 = mMotionEvents.pop();
        long tStart = tEnd;

        while (event2 != null && tEnd-event2.time < INERTIA_TIME_INTERVAL) {
            tStart = event2.time;
            directionX += event1.x - event2.x;
            directionY += event1.y-event2.y;
            event1 = event2;

            if (mMotionEvents.size() > 0) {
                event2 = mMotionEvents.pop();
            } else {
                event2 = null;
            }
        }

        float dist = (float) Math.sqrt(directionX*directionX + directionY
                *directionY);

        if (dist <= REST_THRESHOLD) {
            return;
        }

        // The pointer was moved by more than REST_THRESHOLD pixels in the last
        // INERTIA_TIME_INTERVAL seconds (or less). We have a inertial scroll event.
        float deltaT = (tEnd - tStart) / 1000.0f;

        if (deltaT == 0.0f) {
            return;
        }

        int surfaceWidth = mRenderer.getSurfaceWidth();
        int surfaceHeight = mRenderer.getSurfaceHeight();

        float hFovDeg = mRenderer.getHFovDeg();
        float vFovDeg = mRenderer.getVFovDeg();

        float deltaYaw = directionX / surfaceWidth * hFovDeg;
        float scrollSpeedX = deltaYaw / deltaT;

        float deltaPitch = directionY / surfaceHeight * vFovDeg;
        float scrollSpeedY = deltaPitch / deltaT;

        if (scrollSpeedX == 0.0f && scrollSpeedY == 0.0f) {
            return;
        }

        mRenderer.startInertialScroll(-1.0f * scrollSpeedY,
                -1.0f * scrollSpeedX);
    }

    private class EventInfo {
        public float x;
        public float y;
        public long time;

        public EventInfo(float x, float y, long time) {
            this.x = x;
            this.y = y;
            this.time = time;
        }
    }
}
