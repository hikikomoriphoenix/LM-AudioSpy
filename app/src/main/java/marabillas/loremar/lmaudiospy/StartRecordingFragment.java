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
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.concurrent.TimeUnit;

/**
 * Created by Loremar on 14/02/2018.
 * This fragment immediately starts recording audio. This will not attempt to lock the app on screen.
 * It allows you to do other things in your device while recording audio at the same time.
 */

public class StartRecordingFragment extends Fragment implements View.OnClickListener{
    private AudioRecord record;
    private int sampleRate;
    private int channelConfig;
    private int channelCount;
    private int encodingFormat;
    private int gain;
    private int readBufferSize;
    private ByteBuffer audioBuffer;
    private boolean isRecording = false;
    private int whenToConvertAudioId;

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

    //UI-specific feilds
    private Handler mainHandler;
    private TextView header;
    private TouchableImageView stopButton;
    private long startingTime;
    private long elapsedTime;
    private Runnable timerDisplay;
    private TextView timerView;
    private AudioVisualizerView graphView;
    private AudioProcessingTools.MaxAmplitude maxAmplitude;
    private Runnable graphUpdate;

    private PostRecordingFragment postRecordingFragment;

    private static final String LOG = "Loremar_Logs";

    View view;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(view == null) {
            view = inflater.inflate(R.layout.start_recording, container, false);

            prefs = getActivity().getSharedPreferences("settings", 0);

            header = view.findViewById(R.id.recordingName);
            graphView = view.findViewById(R.id.visualizer);
            timerView = view.findViewById(R.id.time_elapsed);
            stopButton = view.findViewById(R.id.white_circle_stop);
            stopButton.setOnClickListener(this);

            gain = prefs.getInt("gain", 20);
            sampleRate = Integer.parseInt(prefs.getString("sample_rate", "44100"));
            channelConfig = AudioSpy.getPrefChannelConfig(prefs);
            encodingFormat = AudioSpy.getPrefEncodingFormat(prefs);
            int bitRate = Integer.parseInt(prefs.getString("bit_rate", "256000"));
            switch (channelConfig){
                case AudioFormat.CHANNEL_IN_MONO: channelCount = 1; break;
                case AudioFormat.CHANNEL_IN_STEREO: channelCount = 2; break;
            }

            readBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encodingFormat);
            Log.i(LOG, "Buffer Size=" + readBufferSize);
            if (readBufferSize <= 0) {
                Log.e(LOG, "Device doesn't support specified parameters.");
                AlertDialog dialog = new AlertDialog.Builder(getActivity().getApplicationContext()).create();
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

            int prefOutFormat = prefs.getInt("output_format", R.id.wavAndM4a);
            AudioSpy.identifyIfSavetoWavOrM4a(prefOutFormat);
            saveToWAV = AudioSpy.isSaveToWav();
            saveToM4A = AudioSpy.isSaveToM4a();
            postRecordingFragment = new PostRecordingFragment();
            if(saveToM4A) {
                whenToConvertAudioId = prefs.getInt("when_to_convert", R.id.afterRecording);
                if (whenToConvertAudioId == R.id.whileRecording) {
                    AudioProcessingTools.prepareCodec(bitRate, sampleRate, channelCount);
                    AudioProcessingTools.setOnEncodingFinishedListener(postRecordingFragment);
                }
            }
        }
        mainHandler = new Handler(Looper.getMainLooper());
        setRetainInstance(true);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if(record == null) {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, encodingFormat, readBufferSize);
            /*int audioSessionId = record.getAudioSessionId();
            if (NoiseSuppressor.isAvailable())
                NoiseSuppressor.create(audioSessionId).setEnabled(true);
            else Log.e(LOG, "noise suppressor is not supported");
            if (AcousticEchoCanceler.isAvailable())
                AcousticEchoCanceler.create(audioSessionId).setEnabled(true);
            else Log.e(LOG, "acoustic echo canceler is not supported");
            if (AutomaticGainControl.isAvailable())
                AutomaticGainControl.create(audioSessionId).setEnabled(true);
            else Log.e(LOG, "automatic gain control is not supported");*/
            record.startRecording();
            recordFilenameBase = String.valueOf(System.currentTimeMillis());
            String headerText;
            if(saveToWAV) headerText = recordFilenameBase + ".wav";
            else headerText = recordFilenameBase + ".m4a";
            header.setText(headerText);

            Thread recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File saveDir = new File(Environment.getExternalStorageDirectory(), "LM AudioSpy");
                        saveDirectory = prefs.getString("save_directory", saveDir.getAbsolutePath());
                        if (!AudioSpy.createValidFile(saveDirectory)) {
                            isRecording = false;
                            PopUpText.show("Failed to create directory", getActivity().getApplicationContext());
                            getFragmentManager().popBackStack();
                            return;
                        }
                        if (saveToWAV) {
                            File audioFile = new File(saveDirectory, recordFilenameBase + ".wav");
                            audioOut = new FileOutputStream(audioFile);
                            fileChannel = audioOut.getChannel();
                            AudioProcessingTools.writeWavHeader(fileChannel, channelConfig, sampleRate, encodingFormat);
                        }
                        if (saveToM4A) {
                            bytesOut = new ByteArrayOutputStream();
                            if (whenToConvertAudioId == R.id.afterRecording) {
                                writeChannel = Channels.newChannel(bytesOut);
                            } else if (whenToConvertAudioId == R.id.whileRecording) {
                                AudioProcessingTools.initAudioConversion(saveDirectory, recordFilenameBase);
                            }
                        }

                        startingTime = SystemClock.elapsedRealtime();
                        timerDisplay = new Runnable() {
                            @Override
                            public void run() {
                                elapsedTime = SystemClock.elapsedRealtime() - startingTime;
                                long elapsedTimeHours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
                                long elapsedTimeMins = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) - TimeUnit.HOURS.toMinutes(elapsedTimeHours);
                                long elapsedTimeSecs = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) - TimeUnit.MINUTES.toSeconds(elapsedTimeMins);
                                String elapsedTimeHoursText, elapsedTimeMinsText, elapsedTimeSecsText;
                                if (elapsedTimeHours < 10)
                                    elapsedTimeHoursText = "0" + elapsedTimeHours;
                                else elapsedTimeHoursText = String.valueOf(elapsedTimeHours);
                                if (elapsedTimeMins < 10)
                                    elapsedTimeMinsText = "0" + elapsedTimeMins;
                                else elapsedTimeMinsText = String.valueOf(elapsedTimeMins);
                                if (elapsedTimeSecs < 10)
                                    elapsedTimeSecsText = "0" + elapsedTimeSecs;
                                else elapsedTimeSecsText = String.valueOf(elapsedTimeSecs);
                                String timerText = elapsedTimeHoursText + ":" + elapsedTimeMinsText + ":" + elapsedTimeSecsText;
                                timerView.setText(timerText);

                                mainHandler.postDelayed(this, 1000);
                            }
                        };
                        mainHandler.post(timerDisplay);
                        maxAmplitude = new AudioProcessingTools.MaxAmplitude(0);
                        graphUpdate = new Runnable() {
                            @Override
                            public void run() {
                                graphView.addValuetoGraph(maxAmplitude.value);
                                maxAmplitude.value = 0;
                                graphView.postInvalidate();
                                mainHandler.postDelayed(this, 30);
                            }
                        };
                        graphUpdate.run();

                        while (isRecording) {
                            if (record == null) {
                                record = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, encodingFormat, readBufferSize);
                                record.startRecording();
                            }
                            audioBuffer = ByteBuffer.allocateDirect(readBufferSize);
                            audioBuffer.order(ByteOrder.LITTLE_ENDIAN);
                            AudioProcessingTools.readAudioApplyGain(audioBuffer, record, readBufferSize, gain, maxAmplitude);
                            if (saveToWAV) fileChannel.write(audioBuffer);
                            if (saveToM4A) {
                                audioBuffer.rewind();
                                if (whenToConvertAudioId == R.id.afterRecording) {
                                    writeChannel.write(audioBuffer);
                                } else if (whenToConvertAudioId == R.id.whileRecording) {
                                    AudioProcessingTools.addData(audioBuffer);
                                }
                            }
                        }
                        record.stop();
                        record.release();
                        if (saveToWAV) {
                            fileChannel.close();
                            Log.i(LOG, "Raw Audio is successfully saved as wav file.");
                        }
                        if (saveToM4A) {
                            if (whenToConvertAudioId == R.id.afterRecording) {
                                writeChannel.close();
                            } else if (whenToConvertAudioId == R.id.whileRecording) {
                                AudioProcessingTools.notifyRecordingEnded();
                            }
                        }

                        mainHandler.removeCallbacks(timerDisplay);
                        mainHandler.removeCallbacks(graphUpdate);

                        Bundle data = new Bundle();
                        if (saveToM4A) data.putByteArray("audio data", bytesOut.toByteArray());
                        data.putString("name", recordFilenameBase);
                        postRecordingFragment.setArguments(data);
                        FragmentManager fragmentManager = getFragmentManager();
                        fragmentManager.popBackStack();
                        fragmentManager.beginTransaction().replace(android.R.id.content, postRecordingFragment).addToBackStack(null).commit();
                    } catch (IOException e) {
                        Log.e(LOG, "IOException in recordingThread", e);
                        isRecording = false;
                    }
                }
            });
            isRecording = true;
            recordingThread.start();
        }
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onClick(View v) {
        if(v == stopButton){
            isRecording = false;
        }
    }
}
