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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AudioSpy extends Activity{
    private static final String LOG_TAG = "Loremar_Logs";
    int width;
    int height;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "AudioSpy has started");
        if(getFragmentManager().findFragmentById(android.R.id.content) == null){
            try{
                getFragmentManager().beginTransaction().add(android.R.id.content, new MainMenuFragment()).commit();
            }catch(Exception e){
                Log.e(LOG_TAG, "Exception in creating new MainMenuFragment", e);
            }
        }
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        width = displayMetrics.widthPixels;
        height = displayMetrics.heightPixels;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return getFragmentManager().findFragmentByTag("LOCK MODE") == null && super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        Log.i(LOG_TAG, "main activity onstop is called");
        if(getFragmentManager().findFragmentByTag("LOCK MODE") != null){
            Log.i(LOG_TAG, "restarting mainActivity");
            Intent intent = (new Intent(getApplicationContext(), this.getClass()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        super.onStop();
    }

    /**
     * Creates file or directory of given path if nonexistent
     */
    static boolean createValidFile(String path){
        List<String> paths = new ArrayList<>();
        do{
            paths.add(path);
            path = path.substring(0, path.lastIndexOf("/"));
        }while(!path.equals(Environment.getExternalStorageDirectory().getAbsolutePath()));
        for(int i=paths.size()-1; i>=0; i--){
            path = paths.get(i);
            File file = new File(path);
            if(!file.exists())
                if(!file.mkdir()) return false;
        }
        return true;
    }

    private static boolean saveToWav;
    private static boolean saveToM4a;

    static void identifyIfSavetoWavOrM4a(int prefOutFormat){
        switch(prefOutFormat){
            case R.id.wavOnly:
                saveToWav = true;
                saveToM4a = false;
                break;
            case R.id.m4aOnly:
                saveToWav = false;
                saveToM4a = true;
                break;
            case R.id.wavAndM4a:
                saveToWav = true;
                saveToM4a = true;
                break;
            default:
                saveToWav = true;
                saveToM4a = true;
                break;
        }
    }

    static boolean isSaveToWav() {
        return saveToWav;
    }

    static  boolean isSaveToM4a() {
        return saveToM4a;
    }

    static int getPrefChannelConfig(SharedPreferences prefs){
        switch(prefs.getString("channel_config", "mono")){
            case "mono":
                return AudioFormat.CHANNEL_IN_MONO;
            case "stereo":
                return AudioFormat.CHANNEL_IN_STEREO;
            default:
                return AudioFormat.CHANNEL_IN_MONO;
        }
    }

    static int getPrefEncodingFormat(SharedPreferences prefs){
        switch(prefs.getString("encoding", "PCM 16-BIT")){
            case "PCM 8-BIT":
                return AudioFormat.ENCODING_PCM_8BIT;
            case "PCM 16-BIT":
                return AudioFormat.ENCODING_PCM_16BIT;
            case "PC 32_BIT":
                return AudioFormat.ENCODING_PCM_FLOAT;
            default:
                return AudioFormat.ENCODING_PCM_16BIT;
        }
    }

    static class AudioFile{
        String filename;
        String timeCreated;
        String duration;
        String size;
    }

    /**
     * gets a list of audio files from the given save directory and adds each item to fileList including
     * information about thee recording.
     * @param fileList arraylist where each file and its information is to be added to
     * @param saveDirectory the save directory to get the list of files from
     * @param context context to use in formatting filesizes
     */
    static void filesList(List <AudioFile> fileList, File saveDirectory, Context context){
        String[] filesList = saveDirectory.list();
        for(String filename: filesList){
            File file = new File(saveDirectory.getAbsolutePath(), filename);
            if(file.isFile()) {
                AudioFile audioFile = new AudioFile();
                audioFile.filename = filename;
                SimpleDateFormat formatter = (SimpleDateFormat)SimpleDateFormat.getDateTimeInstance();
                audioFile.timeCreated = formatter.format(new Date(file.lastModified()));
                audioFile.size = android.text.format.Formatter.formatShortFileSize(context, file.length());
                String extension = filename.substring(filename.lastIndexOf("."));
                if(!extension.equals(".wav")) {
                    MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                    try {
                        metadataRetriever.setDataSource(file.getAbsolutePath());
                    } catch (Exception e) {
                        Log.e(LOG_TAG, filename + " doesn't have the desired metadata");
                    }
                    String durationMillsString = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (durationMillsString != null) {
                        long durationMills = Long.parseLong(durationMillsString);
                        if (durationMills < TimeUnit.SECONDS.toMillis(60))
                            audioFile.duration = TimeUnit.MILLISECONDS.toSeconds(durationMills) + "s";
                        else {
                            long durationMins = TimeUnit.MILLISECONDS.toMinutes(durationMills);
                            long durationSecs = TimeUnit.MILLISECONDS.toSeconds(durationMills) - TimeUnit.MINUTES.toSeconds(durationMins);
                            if (durationMins < 60)
                                audioFile.duration = durationMins + ":" + durationSecs + "m";
                            else {
                                long durationHours = TimeUnit.MINUTES.toHours(durationMins);
                                durationMins = durationMins - TimeUnit.HOURS.toMinutes(durationHours);
                                audioFile.duration = durationHours + ":" + durationMins + ":" + durationSecs+"h";
                            }
                        }
                    } else audioFile.duration = "";
                } else audioFile.duration = "";
                fileList.add(audioFile);
            }
        }
    }
}
