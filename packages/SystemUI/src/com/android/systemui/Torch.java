/*
 * Copyright 2011 Colin McDonough
 *
 * Modified for AOKP by Mike Wilson - Zaphod-Beeblebrox
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import java.util.List;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/*
 * Torch is an LED flashlight.
 */
public class Torch extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "Torch";
    private static final String TAG_DB = "TorchDebug";
    
    private static final String WAKE_LOCK_TAG = "TORCH_WAKE_LOCK";

    private Camera mCamera;
    private boolean lightOn;
    private boolean startingTorch;
    private boolean previewOn;
    private View button;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private KeyguardManager mKeyguardManager;

    private WakeLock wakeLock;

    private boolean reallystarted = false;
    private Object padlock = new Object();
    private static final boolean DEBUG = false;
    private String lastIntent = null;
    private int startingstate = -1;
    public static final String KEY_TORCH_ON = "torch_on";
    public static final String INTENT_TORCH_ON = "com.android.systemui.INTENT_TORCH_ON";
    public static final String INTENT_TORCH_OFF = "com.android.systemui.INTENT_TORCH_OFF";
    public static final String INTENT_TORCH_TOGGLE = "com.android.systemui.INTENT_TORCH_TOGGLE";

    public Torch() {        
        super();      
    }
    
    public void db(String s)
    {
        if (DEBUG)
            Log.d(TAG_DB, s);
    }

    public static Torch getTorch() {
        return new Torch();
    }

    private boolean getCamera() {
        try {
            if (mCamera == null)
            {
                mCamera = Camera.open();
            }
            db("getCamera() succeeded");
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Camera.open() failed: " + e.getMessage());
        }        
        db("getCamera() FAILED");
        return false;
    }

    private boolean turnLightOn() {
        try {
            if (mCamera == null) {
                Log.d(TAG, "Camera not Found!");
                db("turnLightOn() FAILED");
                return false;
            }
            Parameters parameters = mCamera.getParameters();
            if (parameters == null) {
                Log.d(TAG, "Camera Params not Found!");
                db("turnLightOn() FAILED");
                return false;
            }
            List<String> flashModes = parameters.getSupportedFlashModes();
            // Check if camera flash exists
            if (flashModes == null) {
                Log.d(TAG, "Camera Flash not Found!");
                db("turnLightOn() FAILED");
                return false;
            }
            String flashMode = parameters.getFlashMode();
            // Log.i(TAG, "Flash mode: " + flashMode);
            // Log.i(TAG, "Flash modes: " + flashModes);
            if (!Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                // Turn on the flash
                if (flashModes.contains(Parameters.FLASH_MODE_TORCH)) {
                    parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
                    mCamera.setParameters(parameters);
                    lightOn = true;
                } else {
                    Log.e(TAG, "FLASH_MODE_TORCH not supported");
                }
            }
            db("turnLightOn() succeeded");
            return true;
        }
        catch(Exception e)
        {
            Log.e(TAG, e.getMessage());
        }        
        db("turnLightOn() FAILED");
        return false;
    }

    private boolean turnLightOff() {
        try{
            if (lightOn) {
                lightOn = false;
                if (mCamera == null) {
                    db("turnLightOff() FAILED");
                    return false;
                }
                Parameters parameters = mCamera.getParameters();
                if (parameters == null) {
                    db("turnLightOff() FAILED");
                    return false;
                }
                List<String> flashModes = parameters.getSupportedFlashModes();
                String flashMode = parameters.getFlashMode();
                // Check if camera flash exists
                if (flashModes == null) {
                    db("turnLightOff() FAILED");
                    return false;
                }
                //Log.i(TAG, "Flash mode: " + flashMode);
                //Log.i(TAG, "Flash modes: " + flashModes);
                if (!Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                    // Turn off the flash
                    if (flashModes.contains(Parameters.FLASH_MODE_OFF)) {
                        parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
                        mCamera.setParameters(parameters);
                    } else {
                        Log.e(TAG, "FLASH_MODE_OFF not supported");
                    }
                }                
                db("turnLightOff() succeeded");
                return true;
            }
        }
        catch(Exception e)
        {
            Log.e(TAG, e.getMessage());
        }
        db("turnLightOff() FAILED");
        return false;
    }

    private boolean startPreview() {
        try{
            if (!previewOn && mCamera != null) {
                reallystarted = false;         
                previewOn = true;   
                mCamera.startPreview();
            }       
            db("startPreview() succeeded");
            return true;
        }
        catch(Exception e)
        {
            Log.e(TAG, e.getMessage());
        }
        db("startPreview() FAILED");
        return false;
    }

    private boolean stopPreview() {
        try{
            if (previewOn && mCamera != null) {
                previewOn = false;
                mCamera.stopPreview();
                db("stopPreview() succeeded");
                return true;
            }
        }
        catch(Exception e)
        {
            Log.e(TAG, e.getMessage());
        }
        db("stopPreview() FAILED");
        return false;
    }

    private boolean startWakeLock() {
        try {
            if (wakeLock == null) {
                Log.d(TAG, "wakeLock is null, getting a new WakeLock");
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                wakeLock.acquire();
            }
            Log.d(TAG, "WakeLock acquired");
            db("startWakeLock() succeeded");
            return true;
        }
        catch(Exception e) {
            Log.e(TAG, e.getMessage());
        }
        db("startWakeLock() FAILED");
        return false;
    }

    private boolean stopWakeLock() {
        try{
            if (wakeLock != null) {
                wakeLock.release();
                wakeLock = null;
            }            
            db("stopWakeLock() succeeded");
            return true;
        }
        catch(Exception e)
        {
            Log.e(TAG, e.getMessage());
        }
        db("stopWakeLock() FAILED");
        return false;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.torch_toggle);
        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceview);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        Log.i(TAG, "onCreate");
    }

    private void toggleTorch() {
        db("--------- TOGGLING ----------");
        if (Settings.System.getBoolean(getContentResolver(), Settings.System.TORCH_STATE, false)) // find torch state
            stopTorch(); // torch is on - turn it off
        else
            startTorch(); // torch is off, turn it on
    }

    
    private void startTorch() {
        synchronized(padlock)
        {            
            startWakeLock();
            db("--- Trying to start torch");
            if (!Settings.System.getBoolean(getContentResolver(), Settings.System.TORCH_STATE, false))
            {
                getCamera();
                startPreview();
                turnLightOn();                
                brute.post(force);
                db("--- Torch started");
            }
        }        
    }
    
    private Handler brute = new Handler();
    private Runnable force = new Runnable() {
        public void run() {
            synchronized(padlock)
            {
                brute.removeCallbacks(force);
                if (!Settings.System.getBoolean(getContentResolver(), Settings.System.TORCH_STATE, false))
                {
                    Settings.System.putBoolean(getContentResolver(), Settings.System.TORCH_STATE, true);
                }
                if (!reallystarted && !isLocked())
                {
                    brute.postDelayed(force, 100);
                }
            }
        }
    };

    private void stopTorch() {
        synchronized(padlock)
        {                
            brute.removeCallbacks(force);
            db("--- Trying to stop torch -- pref="+Settings.System.getBoolean(getContentResolver(), Settings.System.TORCH_STATE, false));
            turnLightOff();
            stopPreview();
            if (mCamera != null)     
            {       
                mCamera.release();
                mCamera = null;                
            }
            if (Settings.System.getBoolean(getContentResolver(), Settings.System.TORCH_STATE, false))
            {
                if (!reallystarted)
                    db("SETTING SHAREDPREF false BECAUSE !reallystarted");
                else
                    db("SETTING SHAREDPREF false BECAUSE STOPPING AND IT WAS true");
                Settings.System.putBoolean(getContentResolver(), Settings.System.TORCH_STATE, false);
            }
            stopWakeLock();
            db("--- Torch stopped");
            if (!isFinishing())            
                this.finish();                
        }
    }

    @Override
    public void onRestart() {
        super.onRestart();
        db("--------- onRestart (action="+getIntent().getAction()+") ----------");
    }

    @Override
    public void onStart() {
        super.onStart();
        db("--------- onStart (action="+getIntent().getAction()+") ----------");
    }

    private void intelligentIntent()
    {
        String s = getIntent().getAction();
        if (s.equals("android.intent.action.MAIN") || s.equals("com.android.systemui.INTENT_TORCH_TOGGLE") || !s.equals(lastIntent))
            doSomething(s);
    }

    @Override
    public void onResume() {
        super.onResume();
        db("--------- onResume (action="+getIntent().getAction()+") ----------");   
        intelligentIntent();
    }
    
    private void doSomething(String action)
    {
        lastIntent = action;
        db("-*-*-* doSomething("+action+") *-*-*-");
        if (action == INTENT_TORCH_ON) {
            startTorch();
        } else if (action == INTENT_TORCH_OFF) {
            stopTorch();
        } else { // assume we started from MAIN
            toggleTorch();
        }
    }

    @Override
    public void onPause() {
        db("--------- onPause ----------");
        super.onPause();       
    }

    @Override
    public void onStop() {     
        db("--------- onStop ----------");
        super.onStop();         
    }

    @Override
    public void onDestroy() {
        db("--------- onDestroy ----------");
        stopTorch();
        super.onDestroy(); 
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        db("--------- onNewIntent (action="+intent.getAction()+") ----------");
        intelligentIntent();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int I, int J, int K) {
        db("         REALLY STARTED");             
        if (!reallystarted)
        {
            reallystarted = true;
        }
        moveTaskToBack(true); // once Surface is set up - we should be able to
                              // background ourselves.      
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (Exception e) {
            //sorry, we couldn't give 2 shits if this fails or not..
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private boolean isLocked() {
        if (mKeyguardManager == null) {
            mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        }
        return mKeyguardManager != null && mKeyguardManager.inKeyguardRestrictedInputMode();
    }
}
