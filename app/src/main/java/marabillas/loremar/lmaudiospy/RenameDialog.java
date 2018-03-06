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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.ViewGroup;
import android.widget.EditText;

import java.io.File;

/**
 * Created by Loremar on 18/02/2018.
 * Creates dialog for renaming a file
 */

class RenameDialog implements DialogInterface.OnClickListener{
    private EditText editBox;
    private String name;
    private String saveDirectory;
    private Context context;

    /**
     *
     * @param context context
     * @param name basename of file
     * @param saveDirectory directory where recordings are saved
     */
    RenameDialog(Context context, String name, String saveDirectory) {
        this.name = name;
        this.saveDirectory = saveDirectory;
        this.context = context;
    }

    final void show(){
        AlertDialog dialog = new AlertDialog.Builder(context).create();
        dialog.setTitle("Rename File");
        dialog.setMessage("Enter new name");
        dialog.setButton(Dialog.BUTTON_POSITIVE, "OK", this);
        dialog.setButton(Dialog.BUTTON_NEGATIVE, "CANCEL", this);
        editBox = new EditText(context);
        editBox.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        editBox.setText("");
        editBox.setHint(name);
        editBox.setBackground(context.getDrawable(R.drawable.edit_text_box));
        dialog.setView(editBox);
        dialog.show();
    }

    @Override
    public final void onClick(DialogInterface dialog, int which) {
        switch(which){
            case AlertDialog.BUTTON_POSITIVE:
                final String newName = String.valueOf(editBox.getText());
                File wavFile = new File(saveDirectory, name + ".wav");
                File m4aFile = new File(saveDirectory, name + ".m4a");
                File newWavFile = new File(saveDirectory, newName + ".wav");
                File newM4aFile = new File(saveDirectory, newName + ".m4a");
                if(newWavFile.exists() || newM4aFile.exists()){
                    PopUpText.show("Failed: File with the same name already exists", context);
                }
                else if(newName.equals("")){
                    PopUpText.show("Failed: New name should not be blank", context);
                }
                else{
                    boolean wavExists;
                    boolean m4aExists;
                    boolean wavNotFailed = true;
                    boolean m4aNotFailed = true;
                    if(wavExists = wavFile.exists()){
                        if(wavNotFailed = wavFile.renameTo(newWavFile)){
                            onFileRenamed(newName + ".wav");
                        }
                    }
                    if(m4aExists = m4aFile.exists()){
                        if(m4aNotFailed = m4aFile.renameTo(newM4aFile)){
                            onFileRenamed(newName + ".m4a");
                        }
                    }
                    if(!wavExists && !m4aExists) PopUpText.show("Failed: No files of given name exist to be renamed", context);
                    if(!wavNotFailed || !m4aNotFailed) PopUpText.show("Failed: Invalid. Try a different name", context);
                }
                break;
        }
    }

    /**
     * Override this method to do something after file is renamed
     *
     * @param newFilename new filename with a tailing extension
     */
    void onFileRenamed(String newFilename) {
    }
}
