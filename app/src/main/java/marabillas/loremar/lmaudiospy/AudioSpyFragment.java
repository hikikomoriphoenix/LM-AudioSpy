/*
 *     LM AudioSpy is an audio recording app for Android version 5.1
 *     Copyright (C) 2017-2018 Loremar Marabillas
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package marabillas.loremar.lmaudiospy;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Created by Loremar Marabillas on 24/12/2017.
 * This fragment allows the user to record audio by double tapping on screen. A second double-tap stops the recording
 * and starts converting audio data to be saved as an M4A file, by encoding the raw PCM data into AAC and creating an MPEG container for it.
 * Important Android API classes used:
 * AudioRecord - reads audio input as PCM data.
 * MediaCodec - encodes PCM to AAC.
 * MediaMuxer - creates MPEG container.
 */

public final class AudioSpyFragment extends Fragment implements View.OnTouchListener, GestureDetector.OnDoubleTapListener, AudioProcessingTools.OnEncodingFinishedListener{
    private AudioSpy main;

    //UI-specific fields
    private GestureDetector gestureDetector;
    private View view =null;
    private TextView doubleTapText;
    private TextView recordingText;
    private ImageView redCircle;
    private Runnable redCircleBlinking;
    private final Handler blinkHandler = new Handler(Looper.getMainLooper());
    private TextView logText;
    private TextView convertProgressText;
    private TouchableImageView key;
    private float keyXInitial;
    private TextView swipeText;
    private ImageView lock;
    private float prevX;
    private boolean unlocked=false;

    //IO-specific fields
    private SharedPreferences prefs;
    private String saveDirectory;
    private String recordFilenameBase;
    private FileOutputStream audioOut;
    private FileChannel fileChannel;
    private ByteArrayOutputStream bytesOut;
    private WritableByteChannel writeChannel;
    private boolean saveToWAV;
    private boolean saveToM4A;

    //Audio recording and processing-specific fields
    private int sampleRate;
    private int channelConfig;
    private int encodingFormat;
    private int gain;
    private int bitRate;
    private int channelCount;
    private AudioRecord audioRec;
    private ByteBuffer audioBuffer;
    private int readBufferSize;
    boolean isRecording = false;
    private Thread recordingThread;
    private ThreadGroup encoderThreads;
    private int whenToConvertAudioId;
    
    private static final String LOG = "Loremar_Logs";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(view ==null) {
            main = (AudioSpy)getActivity();
            prefs = main.getSharedPreferences("settings", 0);
            view = inflater.inflate(R.layout.main, container, false);
            view.setOnTouchListener(this);

            gestureDetector = new GestureDetector(main.getApplicationContext(), new GestureDetector.OnGestureListener() {
                @Override
                public boolean onDown(MotionEvent motionEvent) {
                    return false;
                }
                @Override
                public void onShowPress(MotionEvent motionEvent) {
                }
                @Override
                public boolean onSingleTapUp(MotionEvent motionEvent) {
                    return false;
                }
                @Override
                public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
                    return false;
                }
                @Override
                public void onLongPress(MotionEvent motionEvent) {
                }
                @Override
                public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
                    return false;
                }
            });
            gestureDetector.setOnDoubleTapListener(this);

            gain = prefs.getInt("gain", 20);
            sampleRate = Integer.parseInt(prefs.getString("sample_rate", "44100"));
            channelConfig = AudioSpy.getPrefChannelConfig(prefs);
            encodingFormat = AudioSpy.getPrefEncodingFormat(prefs);
            bitRate = Integer.parseInt(prefs.getString("bit_rate", "256000"));
            switch(channelConfig){
                case AudioFormat.CHANNEL_IN_MONO: channelCount = 1; break;
                case AudioFormat.CHANNEL_IN_STEREO: channelCount = 2; break;
            }

            readBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encodingFormat);
            Log.i(LOG, "Buffer Size=" + readBufferSize);
            if (readBufferSize <= 0){
                Log.e(LOG, "Device doesn't support specified parameters.");
                AlertDialog dialog = new AlertDialog.Builder(main.getApplicationContext()).create();
                dialog.setTitle("Parameters not supported");
                dialog.setMessage("This device does not support the given parameters. Change the parameters in the settings.");
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FragmentManager fragmentManager = getFragmentManager();
                        fragmentManager.popBackStack();
                        fragmentManager.beginTransaction().replace(android.R.id.content, new OtherSettingsFragment(), "OTHER SETTINGS")
                                .addToBackStack(null).commit();
                    }
                });
            }

            doubleTapText = view.findViewById(R.id.doubletapText);
            recordingText = view.findViewById(R.id.recordingText);
            redCircle = view.findViewById(R.id.redCircle);
            logText = view.findViewById(R.id.logText);
            recordingText.setVisibility(View.INVISIBLE);
            redCircle.setVisibility(View.INVISIBLE);

            redCircleBlinking = new Runnable() {
                @Override
                public void run() {
                    if(isRecording) {
                        if (redCircle.getVisibility() == View.INVISIBLE)
                            redCircle.setVisibility(View.VISIBLE);
                        else redCircle.setVisibility(View.INVISIBLE);
                        blinkHandler.postDelayed(this, 500);
                    }
                }
            };

            convertProgressText = view.findViewById(R.id.convertProgressText);
            encoderThreads = new ThreadGroup("Encoders");

            key = view.findViewById(R.id.key);
            key.setOnTouchListener(this);
            keyXInitial = key.getX();
            swipeText = view.findViewById(R.id.swipe_instruction);
            lock = view.findViewById(R.id.lock);

            int prefOutformat = prefs.getInt("output_format", R.id.wavAndM4a);
            AudioSpy.identifyIfSavetoWavOrM4a(prefOutformat);
            saveToWAV = AudioSpy.isSaveToWav();
            saveToM4A = AudioSpy.isSaveToM4a();
            whenToConvertAudioId = prefs.getInt("when_to_convert", R.id.afterRecording);
        }

        setRetainInstance(true);
        return view;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        gestureDetector.onTouchEvent(motionEvent);
        switch(motionEvent.getAction()){
            case MotionEvent.ACTION_UP:
                if(view.getId() == R.id.key){
                    if(unlocked) {
                        isRecording = false;
                        if(audioRec != null) {
                            audioRec.stop();
                            audioRec.release();
                            audioRec = null;
                        }
                        blinkHandler.removeCallbacks(redCircleBlinking);
                        getFragmentManager().popBackStack();
                    }
                    key.setX(keyXInitial);
                    swipeText.setVisibility(View.VISIBLE);
                    key.performClick();
                }
                else view.performClick();
                break;
            case MotionEvent.ACTION_DOWN:
                if(view.getId() == R.id.key){
                    prevX = motionEvent.getRawX();
                    swipeText.setVisibility(View.INVISIBLE);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if(view.getId() == R.id.key && !unlocked){
                    float moveX = motionEvent.getRawX() - prevX;
                    key.setX(key.getX() + moveX);
                    prevX = motionEvent.getRawX();

                    if((lock.getX() - key.getX()) < 0.12f * (float)main.width) {
                        unlocked = true;
                        lock.setImageDrawable(getResources().getDrawable(R.drawable.unlocked));
                        lock.setScaleX(2.0f);
                        lock.setScaleY(2.0f);
                        lock.setTranslationY(-0.05f * (float)main.height);
                        key.setVisibility(View.INVISIBLE);
                    }
                }
                break;
        }
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent motionEvent) {
        if(!isRecording){
            doubleTapText.setText(R.string.doubletap_stop);
            recordingText.setVisibility(View.VISIBLE);
            redCircle.setVisibility(View.VISIBLE);

            if(saveToM4A && whenToConvertAudioId == R.id.whileRecording) {
                AudioProcessingTools.prepareCodec(bitRate, sampleRate, channelCount);
                AudioProcessingTools.setOnEncodingFinishedListener(this);
            }

            Log.i(LOG, "screen is double tapped for recording");
            audioRec = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, encodingFormat, readBufferSize);
            /*final int audioSessionId = audioRec.getAudioSessionId();
            if(NoiseSuppressor.isAvailable()) NoiseSuppressor.create(audioSessionId).setEnabled(true);
            if(AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(audioSessionId).setEnabled(true);
            if(AutomaticGainControl.isAvailable()) AutomaticGainControl.create(audioSessionId).setEnabled(true);*/
            audioRec.startRecording();

            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        recordFilenameBase = String.valueOf(System.currentTimeMillis());
                        File saveDir = new File(Environment.getExternalStorageDirectory(), "LM AudioSpy");
                        saveDirectory = prefs.getString("save_directory", saveDir.getAbsolutePath());
                        if(!AudioSpy.createValidFile(saveDirectory)){
                            isRecording = false;
                            PopUpText.show("Failed to create directory", main.getApplicationContext());
                            getFragmentManager().popBackStack();
                            return;
                        }
                        if(saveToWAV) {
                            File audioFile = new File(saveDirectory, recordFilenameBase + ".wav");
                            audioOut = new FileOutputStream(audioFile);
                            fileChannel = audioOut.getChannel();

                            AudioProcessingTools.writeWavHeader(fileChannel, channelConfig, sampleRate, encodingFormat);
                        }

                        if(saveToM4A) {
                            bytesOut = new ByteArrayOutputStream();
                            if(whenToConvertAudioId == R.id.afterRecording){
                                writeChannel = Channels.newChannel(bytesOut);
                            }
                            else if(whenToConvertAudioId == R.id.whileRecording){
                                AudioProcessingTools.initAudioConversion(saveDirectory, recordFilenameBase);
                            }
                        }

                        while (isRecording) {
                            if(audioRec==null) {
                                audioRec = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, encodingFormat, readBufferSize);
                                audioRec.startRecording();
                            }
                            audioBuffer = ByteBuffer.allocateDirect(readBufferSize);
                            audioBuffer.order(ByteOrder.LITTLE_ENDIAN);
                            AudioProcessingTools.MaxAmplitude maxAmplitude = new AudioProcessingTools.MaxAmplitude(0);
                            AudioProcessingTools.readAudioApplyGain(audioBuffer, audioRec, readBufferSize, gain, maxAmplitude);
                            if(saveToWAV) fileChannel.write(audioBuffer);
                            if(saveToM4A) {
                                audioBuffer.rewind();
                                if(whenToConvertAudioId == R.id.afterRecording) {
                                    writeChannel.write(audioBuffer);
                                }
                                else if(whenToConvertAudioId == R.id.whileRecording){
                                    AudioProcessingTools.addData(audioBuffer);
                                }
                            }
                        }
                        if(saveToWAV) {
                            fileChannel.close();
                            updateLog("\nAudio successfully recorded and saved as "+ recordFilenameBase + ".wav");
                        }
                        if(saveToM4A) {
                            if (whenToConvertAudioId == R.id.whileRecording) {
                                AudioProcessingTools.notifyRecordingEnded();
                            }
                            if (whenToConvertAudioId == R.id.afterRecording) {
                                writeChannel.close();
                                AudioProcessingTools.AudioConverterThread audioConverterThread = new AudioProcessingTools.AudioConverterThread(encoderThreads, recordFilenameBase, bytesOut.toByteArray(), sampleRate, bitRate, channelCount, saveDirectory) {
                                    @Override
                                    void updateConversionProgress() {
                                        main.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                final StringBuilder logString = new StringBuilder();
                                                logString.append("");
                                                Thread[] threads = new Thread[encoderThreads.activeCount()];
                                                encoderThreads.enumerate(threads);
                                                for (Thread thread : threads) {
                                                    if (((AudioProcessingTools.AudioConverterThread) thread).lastProgress < 100) {
                                                        logString.append("Converting audio to M4A...").append(((AudioProcessingTools.AudioConverterThread) thread).progressRate).append("%\n");
                                                    }
                                                }
                                                convertProgressText.setText(logString.toString());
                                            }
                                        });
                                    }

                                    @Override
                                    void onFinished() {
                                        updateLog("\nAudio successfully converted and saved as " + recordFilenameBase + ".m4a");
                                    }
                                };
                                audioConverterThread.start();
                            }
                        }

                    }catch(IOException e){
                        Log.e(LOG,"IOException in recordingThread", e);
                        isRecording = false;
                    }
                }
            });
            isRecording = true;
            recordingThread.start();
            redCircleBlinking.run();
        }
        else {
            doubleTapText.setText(R.string.doubletap_start);
            recordingText.setVisibility(View.INVISIBLE);
            blinkHandler.removeCallbacks(redCircleBlinking);
            redCircle.setVisibility(View.INVISIBLE);

            Log.i(LOG, "Screen is double tapped to stop recording");
            audioRec.stop();
            audioRec.release();
            audioRec = null;
            isRecording = false;
        }
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onStop(){
        recordingThread = null;
        if(AudioProcessingTools.codec != null) {
            AudioProcessingTools.codec.release();
            AudioProcessingTools.codec = null;
        }
        super.onStop();
    }

    private void updateLog(final String message){
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logText.append(message);
            }
        });
    }

    @Override
    public void onEncodingFinished() {
        updateLog("\nAudio successfully converted and saved as " + recordFilenameBase + ".m4a");
    }
}