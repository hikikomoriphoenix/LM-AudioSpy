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

import android.app.Fragment;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

/**
 * Created by Loremar Marabillas on 18/02/2018.
 * This fragment encodes audio after recording and presents option to rename file.
 */

public class PostRecordingFragment extends Fragment implements View.OnClickListener,AudioProcessingTools.OnEncodingFinishedListener{
    private Button button;
    private Button rename;
    private String name;
    private String saveDirectory;
    private boolean renamed;
    private TextView textView;
    private Handler mainHandler;
    private boolean saveToWav;
    private Runnable converttoM4aBlinkingText;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        SharedPreferences prefs = getActivity().getSharedPreferences("settings", 0);
        int prefOutFormat = prefs.getInt("output_format", R.id.wavAndM4a);
        int channelConfig = AudioSpy.getPrefChannelConfig(prefs);
        int channelCount = 1;
        switch(channelConfig){
            case AudioFormat.CHANNEL_IN_MONO: channelCount = 1; break;
            case AudioFormat.CHANNEL_IN_STEREO: channelCount = 2; break;
        }
        AudioSpy.identifyIfSavetoWavOrM4a(prefOutFormat);
        final boolean saveToM4a = AudioSpy.isSaveToM4a();
        saveToWav = AudioSpy.isSaveToWav();
        byte[] data = null;
        if(saveToM4a) data = getArguments().getByteArray("audio data");

        name = getArguments().getString("name");
        View view = inflater.inflate(R.layout.post_recording, container, false);
        button = view.findViewById(R.id.postRecordingButton);
        button.setVisibility(View.GONE);
        button.setOnClickListener(this);
        rename = view.findViewById(R.id.postRecordingRename);
        rename.setVisibility(View.GONE);
        rename.setOnClickListener(this);
        textView = view.findViewById(R.id.postRecordingText);

        File saveDir = new File(Environment.getExternalStorageDirectory(), "LM AudioSpy");
        saveDirectory = prefs.getString("save_directory", saveDir.getAbsolutePath());
        if(!AudioSpy.createValidFile(saveDirectory)){
            PopUpText.show("Failed to create directory", getActivity().getApplicationContext());
            getFragmentManager().popBackStack();
            return null;
        }

        int whenToConvertAudio = prefs.getInt("when_to_convert", R.id.afterRecording);

        mainHandler = new Handler(Looper.getMainLooper());

        if(saveToM4a && whenToConvertAudio == R.id.afterRecording) {
            final int SAMPLE_RATE = Integer.parseInt(prefs.getString("sample_rate", "44100"));
            final int BIT_RATE = Integer.parseInt(prefs.getString("bit_rate", "256000"));
            new AudioProcessingTools.AudioConverterThread(new ThreadGroup("encoders"), name, data, SAMPLE_RATE, BIT_RATE, channelCount, saveDirectory) {
                @Override
                void updateConversionProgress() {
                    final StringBuilder logString = new StringBuilder();
                    if (this.lastProgress < 100) {
                        logString.append("Converting audio to M4A...").append(this.progressRate).append("%\n");
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText(logString.toString());
                        }
                    });
                }

                @Override
                void onFinished() {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String text;
                            if(saveToWav) {
                                text = "Audio saved as " + name + ".wav, " + name + ".m4a";
                            }
                            else{
                                text = "Audio saved as " + name + ".m4a";
                            }
                            textView.setText(text);
                            button.setVisibility(View.VISIBLE);
                            rename.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }.start();
        }
        else if(saveToWav){
            String text = "Audio saved as " + name + ".wav";
            textView.setText(text);
            button.setVisibility(View.VISIBLE);
            rename.setVisibility(View.VISIBLE);
        }
        else{
            final String visible = "Converting audio to M4a. Please wait.";
            final String notVisible = "";
            textView.setText(notVisible);
            converttoM4aBlinkingText = new Runnable() {
                @Override
                public void run() {
                    String text = (String) textView.getText();
                    if (text.equals(notVisible)) {
                        textView.setText(visible);
                    } else if (text.equals(visible)) {
                        textView.setText(notVisible);
                    }
                    mainHandler.postDelayed(this, 500);
                }
            };
            converttoM4aBlinkingText.run();
        }
        return view;
    }

    @Override
    public void onClick(View v) {
        if(v == button) getFragmentManager().popBackStack();
        else if(v == rename){
            renamed = false;
            RenameDialog dialog = new RenameDialog(getActivity(), name, saveDirectory){
                @Override
                void onFileRenamed(String newFilename) {
                    name = newFilename.substring(0, newFilename.lastIndexOf("."));
                    if(renamed){
                        textView.append(", " + newFilename);
                    }
                    else{
                        String text = "Audio renamed to " + newFilename;
                        textView.setText(text);
                        renamed = true;
                    }
                }
            };
            dialog.show();
        }
    }

    @Override
    public void onEncodingFinished() {
        mainHandler.removeCallbacks(converttoM4aBlinkingText);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String text;
                if(saveToWav) {
                    text = "Audio saved as " + name + ".wav, " + name + ".m4a";
                }
                else{
                    text = "Audio saved as " + name + ".m4a";
                }
                textView.setText(text);
                button.setVisibility(View.VISIBLE);
                rename.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onStop() {
        if(AudioProcessingTools.codec != null) {
            AudioProcessingTools.codec.release();
            AudioProcessingTools.codec = null;
        }
        super.onStop();
    }
}
