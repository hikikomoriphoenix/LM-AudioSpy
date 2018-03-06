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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Loremar Marabillas on 09/02/2018.
 * Opens save directory to view a list of all audio recordings from this app. This fragment also allows you to play a selected recording
 * or rename it with a new filename.
 */

public class OpenSaveDirectoryFragment extends Fragment implements View.OnClickListener, View.OnTouchListener {
    private RecyclerView recordingsView;
    private List<AudioSpy.AudioFile> recordingsList;
    private AudioSpy main;
    private SelectedItem selectedItem;
    private TouchableImageView playButton;
    private File saveDirectory;
    private Button renameButton;
    private Button sendButton;
    private Button deleteButton;

    class SelectedItem{
        View highlightedView = null;
        int index = -1;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.open_save_directory, container, false);
        main = (AudioSpy)getActivity();
        recordingsView = view.findViewById(R.id.recordingsView);

        SharedPreferences prefs = main.getSharedPreferences("settings", 0);
        File saveDir = new File(Environment.getExternalStorageDirectory(), "LM AudioSpy");
        saveDirectory = new File(prefs.getString("save_directory", saveDir.getAbsolutePath()));
        if(!AudioSpy.createValidFile(saveDirectory.getAbsolutePath())){
            PopUpText.show("Failed to create directory", main.getApplicationContext());
            getFragmentManager().popBackStack();
            return null;
        }
        String openedDirectory = "Save Directory: " + saveDirectory.getAbsolutePath();
        ((TextView)view.findViewById(R.id.opened_directory)).setText(openedDirectory);
        recordingsList = new ArrayList<>();
        AudioSpy.filesList(recordingsList, saveDirectory, main.getApplicationContext());

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

        selectedItem = new SelectedItem();

        playButton = view.findViewById(R.id.play_button);
        playButton.setOnClickListener(this);
        playButton.setOnTouchListener(this);

        renameButton = view.findViewById(R.id.rename_button_in_open_save_directory);
        renameButton.setOnClickListener(this);

        sendButton = view.findViewById(R.id.send_button_in_open_save_directory);
        sendButton.setOnClickListener(this);

        deleteButton = view.findViewById(R.id.delete_button_in_open_save_directory);
        deleteButton.setOnClickListener(this);

        return view;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(v == playButton){
            switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    playButton.setScaleX(1.5f);
                    playButton.setScaleY(1.5f);
                    break;
                case MotionEvent.ACTION_UP:
                    playButton.setScaleX(1);
                    playButton.setScaleY(1);
                    v.performClick();
                    break;
            }
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        if(selectedItem.index != -1) {
            if (v == playButton) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                File file = new File(saveDirectory, recordingsList.get(selectedItem.index).filename);
                intent.setDataAndType(Uri.fromFile(file), "audio/*");
                startActivity(intent);
            }
            else if (v == renameButton) {
                String selectedItemFilename = recordingsList.get(selectedItem.index).filename;
                final String name = selectedItemFilename.substring(0, selectedItemFilename.lastIndexOf("."));
                RenameDialog renameDialog = new RenameDialog(main, name, saveDirectory.getAbsolutePath()){
                    @Override
                    void onFileRenamed(String newFilename) {
                        TextView renamedFileFilename = selectedItem.highlightedView.findViewById(R.id.filename);
                        renamedFileFilename.setText(newFilename);
                        recordingsList.get(selectedItem.index).filename = newFilename;
                        for(int i=0; i<recordingsList.size(); i++){
                            String iFilename = recordingsList.get(i).filename;
                            String iName = iFilename.substring(0, iFilename.lastIndexOf("."));
                            if(iName.equals(name)){
                                recordingsList.get(i).filename = newFilename;
                                recordingsView.getAdapter().notifyItemChanged(i);
                            }
                        }
                    }
                };
                renameDialog.show();
            }
            else if (v == sendButton) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("audio/*");
                File file = new File(saveDirectory, recordingsList.get(selectedItem.index).filename);
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                startActivity(Intent.createChooser(intent, "Send Audio File"));
            }
            else if(v == deleteButton){
                AlertDialog confirmDelete = new AlertDialog.Builder(main).create();
                confirmDelete.setMessage("Delete file?");
                confirmDelete.setButton(DialogInterface.BUTTON_POSITIVE, "YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selectedItemFilename = recordingsList.get(selectedItem.index).filename;
                        File file = new File(saveDirectory.getAbsolutePath(), selectedItemFilename);
                        if(file.exists()){
                            if(file.delete()) {
                                recordingsList.remove(selectedItem.index);
                                selectedItem.index = -1;
                                selectedItem.highlightedView = null;
                                recordingsView.getAdapter().notifyDataSetChanged();
                            }
                        }
                    }
                });
                confirmDelete.setButton(DialogInterface.BUTTON_NEGATIVE, "NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                confirmDelete.show();
            }
        }
    }

    class RecordingAdapter extends RecyclerView.Adapter<ItemHolder>{

        @Override
        public ItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(main.getApplicationContext());
            return (new ItemHolder(inflater.inflate(R.layout.recording_item, parent, false)));
        }

        @Override
        public void onBindViewHolder(ItemHolder holder, int position) {
            holder.bind(recordingsList.get(position), position);
        }

        @Override
        public int getItemCount() {
            return recordingsList.size();
        }


    }

    class ItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
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

        void bind(AudioSpy.AudioFile file, int index){
            filename.setText(file.filename);
            duration.setText(file.duration);
            timeCreated.setText(file.timeCreated);
            size.setText(file.size);
            if(selectedItem.index !=  -1) {
                if (selectedItem.index == index)
                    highlightSelectedItem(itemView);
                else itemView.setBackgroundColor(Color.BLACK);
            }
            recordingsListIndex = index;
        }

        @Override
        public void onClick(View v) {
            highlightSelectedItem(v);
            selectedItem.index = recordingsListIndex;
        }

        private void highlightSelectedItem(View view){
            if(selectedItem.highlightedView != null) selectedItem.highlightedView.setBackgroundColor(Color.BLACK);
            selectedItem.highlightedView = view;
            selectedItem.highlightedView.setBackgroundColor(getResources().getColor(R.color.highlight));
        }
    }


}
