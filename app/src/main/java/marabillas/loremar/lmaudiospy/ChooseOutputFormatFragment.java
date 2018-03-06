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
import android.widget.RadioGroup;

/**
 * Created by Loremar on 23/02/2018.
 * This fragment allows to change output format for encoding audio
 */

public class ChooseOutputFormatFragment extends Fragment implements RadioGroup.OnCheckedChangeListener {
    private SharedPreferences prefs;
    RadioGroup outputFormatChoices;
    RadioGroup whenToConvertAudio;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_output_format, container, false);
        prefs = getActivity().getSharedPreferences("settings",0);
        outputFormatChoices = view.findViewById(R.id.outputFormatChoices);
        outputFormatChoices.setOnCheckedChangeListener(this);
        whenToConvertAudio = view.findViewById(R.id.whenToConvertAudioRadioGroup);
        whenToConvertAudio.setOnCheckedChangeListener(this);

        outputFormatChoices.check(prefs.getInt("output_format", R.id.wavAndM4a));

        return view;
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if(group == outputFormatChoices) {
            prefs.edit().putInt("output_format", checkedId).apply();
            whenToConvertAudioEnabledUpdate();
        }
        else {
            if(group == whenToConvertAudio && checkedId != -1) prefs.edit().putInt("when_to_convert", checkedId).apply();
        }
    }

    private void whenToConvertAudioEnabledUpdate(){
        if(outputFormatChoices.getCheckedRadioButtonId() == R.id.wavOnly){
            whenToConvertAudio.clearCheck();
            for(int i=0; i<whenToConvertAudio.getChildCount(); i++){
                whenToConvertAudio.getChildAt(i).setEnabled(false);
            }
        }
        else{
            for(int i=0; i<whenToConvertAudio.getChildCount(); i++){
                whenToConvertAudio.getChildAt(i).setEnabled(true);
            }
            whenToConvertAudio.check(prefs.getInt("when_to_convert", R.id.afterRecording));
        }
    }
}
