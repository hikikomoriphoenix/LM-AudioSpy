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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Loremar on 04/03/2018.
 *
 */

public class SendRecordingFragment extends Fragment {
    AudioSpy main;
    Context context;
    File saveDirectory;
    private List<AudioSpy.AudioFile> recordingsList;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.send_recording, container, false);
        TextView currentDirectoryView = view.findViewById(R.id.opened_directory_in_send_recording);
        RecyclerView recordingsView = view.findViewById(R.id.recordings_view_in_send_recording);

        main = (AudioSpy)getActivity();
        context = main.getApplicationContext();

        SharedPreferences prefs = main.getSharedPreferences("settings", 0);
        File saveDir = new File(Environment.getExternalStorageDirectory(), "LM AudioSpy");
        saveDirectory = new File(prefs.getString("save_directory", saveDir.getAbsolutePath()));
        if(!AudioSpy.createValidFile(saveDirectory.getAbsolutePath())){
            PopUpText.show("Failed to create directory", context);
            getFragmentManager().popBackStack();
            return null;
        }

        currentDirectoryView.setText(saveDirectory.getAbsolutePath());

        recordingsList = new ArrayList<>();
        AudioSpy.filesList(recordingsList, saveDirectory, context);

        recordingsView.setLayoutManager(new LinearLayoutManager(main.getApplicationContext()));
        recordingsView.setAdapter(new RecordingAdapter());
        DividerItemDecoration divider = new DividerItemDecoration(recordingsView.getContext(), DividerItemDecoration.VERTICAL){
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int verticalSpacing = (int) Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 4, getResources().getDisplayMetrics()));
                outRect.top = verticalSpacing;
                outRect.bottom = verticalSpacing;
            }
        };
        divider.setDrawable(getResources().getDrawable(R.drawable.bottom_divider));
        recordingsView.addItemDecoration(divider);
        recordingsView.setHasFixedSize(true);

        return view;
    }

    class RecordingAdapter extends RecyclerView.Adapter<ItemHolder>{

        @NonNull
        @Override
        public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return (new ItemHolder(inflater.inflate(R.layout.recording_item, parent, false)));
        }

        @Override
        public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
            holder.bind(recordingsList.get(position), position);
        }

        @Override
        public int getItemCount() {
            return recordingsList.size();
        }


    }

    class ItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView filename;
        private TextView duration;
        private TextView timeCreated;
        private TextView size;
        private int recordingsListIndex;

        ItemHolder(View itemView) {
            super(itemView);
            filename = itemView.findViewById(R.id.filename);
            duration = itemView.findViewById(R.id.duration);
            timeCreated = itemView.findViewById(R.id.timeCreated);
            size = itemView.findViewById(R.id.size);
            itemView.setOnClickListener(this);
        }

        void bind(AudioSpy.AudioFile file, int index) {
            filename.setText(file.filename);
            duration.setText(file.duration);
            timeCreated.setText(file.timeCreated);
            size.setText(file.size);
            recordingsListIndex = index;
        }

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            File file = new File(saveDirectory, recordingsList.get(recordingsListIndex).filename);
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            startActivity(Intent.createChooser(intent, "Send Audio File"));
        }
    }
}
