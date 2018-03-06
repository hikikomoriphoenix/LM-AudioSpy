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
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

/**
 * Created by Loremar on 22/02/2018.
 * This fragment allows to change the GAIN in audio data being read.
 */

public class AdjustMicSensitivityFragment extends Fragment implements SeekBar.OnSeekBarChangeListener {
    private SharedPreferences prefs;
    private static final int MAX_PROGRESS = 40;
    private static  final int DEFAULT_PROGRESS = 20;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        AudioSpy main = (AudioSpy) getActivity();
        View view = inflater.inflate(R.layout.adjust_mic_sensitivity, container, false);
        SeekBar gainSlider = view.findViewById(R.id.gainSlider);

        gainSlider.setOnSeekBarChangeListener(this);
        prefs = main.getSharedPreferences("settings", 0);
        gainSlider.setMax(MAX_PROGRESS);
        gainSlider.setProgress(prefs.getInt("gain", DEFAULT_PROGRESS));

        return view;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(fromUser) {
            prefs.edit().putInt("gain", progress).apply();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}

}
