/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.weatherstation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import com.google.android.things.contrib.driver.apa102.Apa102;
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.pwmspeaker.Speaker;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

/** Activity that outputs the lED at random intervals. Upon clicking the button you get awarded a positive
 * or negative score if the LED is still on. This shows up on the display and you get a high pitched buzzer
 * tone for a correct answer, or a low pitched buzzing sound for an incorrect answer
  */

public class GameDisplayActivity extends Activity {

    private static final String TAG = GameDisplayActivity.class.getSimpleName();

    private ButtonInputDriver mButtonInputDriver;
    private AlphanumericDisplay mDisplay;

    private Handler mHandler = new Handler();
    private Handler speakerHandler = new Handler();

    private Gpio mLed;

    private int SPEAKER_READY_DELAY_MS = 300;
    private Speaker mSpeaker;
    private int score = 0;
    private boolean mLedOff = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Started Game Display");

        setContentView(R.layout.activity_main);

        // GPIO button that generates 'A' keypresses (handled by onKeyUp method)
        try {
            mButtonInputDriver = new ButtonInputDriver(BoardDefaults.getButtonGpioPin(),
                    Button.LogicState.PRESSED_WHEN_LOW, KeyEvent.KEYCODE_A);
            mButtonInputDriver.register();
            Log.d(TAG, "Initialized GPIO Button that generates a keypress with KEYCODE_A");
        } catch (IOException e) {
            throw new RuntimeException("Error initializing GPIO button", e);
        }

        try {

            mDisplay = new AlphanumericDisplay("I2C1");
            mDisplay.setEnabled(true);
            mDisplay.clear();
            mDisplay.display(0);
            Log.d(TAG, "Initialized I2C Display");
        } catch (IOException e) {
            Log.e(TAG, "Error initializing display", e);
            Log.d(TAG, "Display disabled");
            mDisplay = null;
        }

        // GPIO led
        try {
            PeripheralManagerService pioService = new PeripheralManagerService();
            mLed = pioService.openGpio(BoardDefaults.getLedGpioPin());
            mLed.setEdgeTriggerType(Gpio.EDGE_NONE);
            mLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLed.setActiveType(Gpio.ACTIVE_HIGH);
            mHandler.post(mBlinkRunnable);
        } catch (IOException e) {
            throw new RuntimeException("Error initializing led", e);
        }

        // PWM speaker
        try {
            mSpeaker = new Speaker(BoardDefaults.getSpeakerPwmPin()); // PWM1
            final ValueAnimator slide = ValueAnimator.ofFloat(440, 440 * 4);
            slide.setDuration(50);
            slide.setRepeatCount(5);
            slide.setInterpolator(new LinearInterpolator());
            slide.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    try {
                        float v = (float) animation.getAnimatedValue();
                        mSpeaker.play(v);
                    } catch (IOException e) {
                        throw new RuntimeException("Error sliding speaker", e);
                    }
                }
            });
            slide.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    try {
                        mSpeaker.stop();
                    } catch (IOException e) {
                        throw new RuntimeException("Error sliding speaker", e);
                    }
                }
            });

            Handler handler = new Handler(getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    slide.start();
                }
            }, SPEAKER_READY_DELAY_MS);
        } catch (IOException e) {
            throw new RuntimeException("Error initializing speaker", e);
        }

    }

    private void configureLedGpio() {
        // GPIO led
        try {
            PeripheralManagerService pioService = new PeripheralManagerService();
            mLed = pioService.openGpio(BoardDefaults.getLedGpioPin());
            mLed.setEdgeTriggerType(Gpio.EDGE_NONE);
            mLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLed.setActiveType(Gpio.ACTIVE_HIGH);
        } catch (IOException e) {
            throw new RuntimeException("Error initializing led", e);
        }
    }

    public void stopSpeakerIn50ms(){
        speakerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try{
                    mSpeaker.stop();
                } catch (IOException e){
                    Log.e(TAG, "Could not stop speaker", e);
                }
            }
        }, 50);
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_A) {
            Log.d(TAG, "pressing button");
            try {
                if(mLed.getValue()) {
                    score++;
                    mLed.setValue(false);
                    mLedOff = true;
                    updateDisplay(score);
                    mSpeaker.play(8000);
                    stopSpeakerIn50ms();

                } else{
                    score--;
                    updateDisplay(score);
                    mSpeaker.play(700);
                    stopSpeakerIn50ms();
                }
            } catch (IOException e) {
                Log.e(TAG, "error updating LED", e);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mButtonInputDriver != null) {
            try {
                mButtonInputDriver.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mButtonInputDriver = null;
        }

        if (mDisplay != null) {
            try {
                mDisplay.clear();
                mDisplay.setEnabled(false);
                mDisplay.close();
            } catch (IOException e) {
                Log.e(TAG, "Error disabling display", e);
            } finally {
                mDisplay = null;
            }
        }

        if (mLed != null) {
            try {
                mLed.setValue(false);
                mLed.close();
            } catch (IOException e) {
                Log.e(TAG, "Error disabling led", e);
            } finally {
                mLed = null;
            }
        }

    }

    private void updateDisplay(int value) {
        if (mDisplay != null) {
            try {
                mDisplay.display(value);
            } catch (IOException e) {
                Log.e(TAG, "Error setting display", e);
            }
        }
    }


    public void deactivateLed(Gpio ledGpio){
        // Exit if the GPIO is already closed
        if (ledGpio == null) {
            return;
        }
        try{
            Log.d(TAG, "deactivating LED. Currently on? " + ledGpio.getValue());
            ledGpio.setValue(false);

        } catch (IOException e) {
            Log.e(TAG, "Error deactivating LED ", e);
        }
    }


    private Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            // Exit if the GPIO is already closed
            if (mLed == null) {
                return;
            }

            try {
                // Step 3. Toggle the LED state
//                Log.d(TAG, "LED value: " + mLedGpio2.getValue());
                if(mLedOff){
                    mLedOff = false;
                } else {
                    mLed.setValue(!mLed.getValue());
                }
                boolean mledOn = mLed.getValue();
                if(mledOn){ // if on turn off in 500ms
                    mHandler.postDelayed(mBlinkRunnable, 500);
                } else {   // if off then turn it on in between 1000ms to 3000ms
                    mHandler.postDelayed(mBlinkRunnable, (int)(Math.random()*2300 + 200));
                }

            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }

        }
    };

}
