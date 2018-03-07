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
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Loremar on 19/02/2018.
 * This fragment allows you to change save directory for recordings
 */

public class ChangeSaveDirectoryFragment extends Fragment implements View.OnClickListener {
    private TextView currentDirectoryView;
    private Button makeNewFolder;
    private Button setSaveDirectory;
    private AudioSpy main;
    private Context context;
    private SharedPreferences prefs;

    private List<String> directoryList;
    private String currentDirectoryPath;
    private CurrentDirectoryAdapter adapter;

    private final static String LOG = "Loremar_Logs";

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.change_save_directory, container, false);
        currentDirectoryView = view.findViewById(R.id.currentDirectory);
        RecyclerView directoryListView = view.findViewById(R.id.currenDirectoryList);
        makeNewFolder = view.findViewById(R.id.makeNewFolder);
        setSaveDirectory = view.findViewById(R.id.save_current_directory);

        main = (AudioSpy) getActivity();
        context = main.getApplicationContext();

        prefs = main.getSharedPreferences("settings", 0);
        File saveDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "LM AudioSpy");
        currentDirectoryPath = prefs.getString("save_directory", saveDir.getAbsolutePath());
        if(!AudioSpy.createValidFile(currentDirectoryPath)){
            PopUpText.show("Failed to create directory", context);
            getFragmentManager().popBackStack();
            return null;
        }
        currentDirectoryView.setText(currentDirectoryPath);

        directoryListView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CurrentDirectoryAdapter();
        directoryListView.setAdapter(adapter);
        DividerItemDecoration divider = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL){
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int verticalSpacing = (int) Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));
                outRect.top = verticalSpacing;
                outRect.bottom = verticalSpacing;
            }
        };
        divider.setDrawable(getResources().getDrawable(R.drawable.bottom_divider));
        directoryListView.addItemDecoration(divider);
        directoryListView.setHasFixedSize(true);

        makeNewFolder.setOnClickListener(this);
        setSaveDirectory.setOnClickListener(this);

        directoryList = new ArrayList<>();
        upDateDirectoryList();

        return view;
    }

    @Override
    public void onClick(View v) {
        if(v == makeNewFolder){
            AlertDialog dialog = new AlertDialog.Builder(main).create();
            dialog.setTitle("Make New Folder");
            dialog.setMessage("Enter name of new folder");
            final EditText text = new EditText(context);
            text.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            text.setText("");
            dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    File file = new File(currentDirectoryPath, String.valueOf(text.getText()));
                    if(file.mkdir()){
                        upDateDirectoryList();
                        adapter.notifyDataSetChanged();
                    }
                    else PopUpText.show("Failed: Can't create directory. Try a different name.", context);
                }
            });
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "CANCEL", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {}
            });
            dialog.setView(text);
            dialog.show();
        }
        else if(v == setSaveDirectory){
            AlertDialog dialog = new AlertDialog.Builder(main).create();
            dialog.setTitle("Set Save Directory");
            dialog.setMessage("New recordings will be saved here. Set current directory as the new save directory?");
            dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    prefs.edit().putString("save_directory", currentDirectoryPath).apply();
                    PopUpText.show("New save directory is set", context);
                }
            });
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "CANCEL", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {}
            });
            dialog.show();
        }

    }

    class CurrentDirectoryAdapter extends RecyclerView.Adapter<ItemHolder>{
        @NonNull
        @Override
        public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return new ItemHolder(inflater.inflate(R.layout.simple_text_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
            holder.bind(directoryList.get(position));
        }

        @Override
        public int getItemCount() {
            return directoryList.size();
        }
    }

    class ItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView directory;
        ItemHolder(View itemView) {
            super(itemView);
            directory = itemView.findViewById(R.id.directory);
            itemView.setOnClickListener(this);
        }

        void bind(String name){
            Log.i(LOG, name);
            directory.setText(name);
        }

        @Override
        public void onClick(View v) {
            String selectedDirectoryName = (String) directory.getText();
            if(selectedDirectoryName.equals("...")){
                currentDirectoryPath = (new File(currentDirectoryPath)).getParentFile().getAbsolutePath();
                upDateDirectoryList();
                currentDirectoryView.setText(currentDirectoryPath);
                adapter.notifyDataSetChanged();
            }
            else{
                currentDirectoryPath = (new File(currentDirectoryPath, (String) directory.getText())).getAbsolutePath();
                upDateDirectoryList();
                currentDirectoryView.setText(currentDirectoryPath);
                adapter.notifyDataSetChanged();
            }
        }
    }

    private void upDateDirectoryList(){
        directoryList.clear();
        if(!currentDirectoryPath.equals(Environment.getExternalStorageDirectory().getAbsolutePath())){
            directoryList.add("...");
        }
        File[] files = (new File(currentDirectoryPath)).listFiles();
        for(File file: files){
            if(file.isDirectory()){
                directoryList.add(file.getName());
            }
        }
    }
}
