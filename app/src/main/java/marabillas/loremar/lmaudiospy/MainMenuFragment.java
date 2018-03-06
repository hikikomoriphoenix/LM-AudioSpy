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
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by Loremar Marabillas on 27/01/2018.
 * Main menu with rotating items.
 * 1.start recording
 * 2. lock mode
 * 3. change mic sensitivity
 * 4. open save directory
 * 5. change save directory
 * 6. change output format
 * 7. email recording
 * 8. other settings
 */

public class MainMenuFragment extends Fragment implements View.OnTouchListener, GestureDetector.OnGestureListener, View.OnClickListener{
    private class Button{
        ImageView image;
        int position;
        String name;
    }

    private Button[] buttons;

    private int width;
    private int height;
    private final static int MENU_ITEMS_NUM = 8;
    private final static float SELECTED_SCALE = 0.8f;
    private double rotatingMenuRadius;//in pixels
    private double[] angles;
    private String currentButtonName;
    private boolean clickReady =false;
    private GestureDetector gestureDetector;
    private TouchableImageView greyCircle;
    private TextView currentButtonNameDisplay;
    private TextView currentButtonDescription;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentManager fragmentManager = getFragmentManager();
        for(int i=0; i<fragmentManager.getBackStackEntryCount(); i++){
            fragmentManager.popBackStack();
        }
        View view =  inflater.inflate(R.layout.menu, container, false);
        view.setOnTouchListener(this);

        AudioSpy main = (AudioSpy)getActivity();
        width = main.width;
        height = main.height;
        rotatingMenuRadius = width < height ? 0.27*(double)width : 0.27*(double)height;

        double angle = 2*Math.PI/MENU_ITEMS_NUM;
        buttons = new Button[MENU_ITEMS_NUM];
        int[] buttonsId = new int[]{R.id.lock_mode_button, R.id.start_recording_button, R.id.open_save_directory_button,
                R.id.change_save_directory_button, R.id.send_recording_button, R.id.other_settings_button,
                R.id.change_output_format_button, R.id.adjust_mic_sensitivity_button};

        angles = new double[MENU_ITEMS_NUM];
        String[] buttonNames = getResources().getStringArray(R.array.buttons);
        for(int i=0; i<MENU_ITEMS_NUM; i++){
            angles[i] = i*angle;
            float x = (float) (rotatingMenuRadius * Math.cos(angles[i]));
            float y = (float) (rotatingMenuRadius * Math.sin(angles[i]));
            buttons[i] = new Button();
            ImageView button = buttons[i].image = view.findViewById(buttonsId[i]);
            button.getLayoutParams().width = lengthBasedOnDisplayWidth(0.15);
            button.getLayoutParams().height = lengthBasedOnDisplayHeight(0.15);
            buttons[i].position = i;
            buttons[i].name = buttonNames[i];
            button.animate().translationX(x).translationY(y);
            if(i==0){
                greyCircle = view.findViewById(R.id.grey_circle);
                int radius = lengthBasedOnDisplayHeight(0.15);
                greyCircle.getLayoutParams().width = radius;
                greyCircle.getLayoutParams().height = radius;
                greyCircle.setTranslationX(x);
                greyCircle.setTranslationZ(-10);
                greyCircle.setVisibility(View.INVISIBLE);
                greyCircle.setOnClickListener(this);
                greyCircle.setOnTouchListener(this);
                currentButtonName = buttons[i].name;
                button.animate().scaleXBy(SELECTED_SCALE).scaleYBy(SELECTED_SCALE);
                button.animate().withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        clickReady = true;
                        greyCircle.setVisibility(View.VISIBLE);
                    }
                });
            }
            button.animate().setDuration(500).start();
        }
        currentButtonNameDisplay = view.findViewById(R.id.currentButtonName);
        currentButtonNameDisplay.setText(buttonNames[0]);
        currentButtonDescription = view.findViewById(R.id.currentButtonDesc);
        currentButtonDescription.setText(buttons[0].image.getContentDescription());
        currentButtonDescription.getLayoutParams().width = lengthBasedOnDisplayWidth(0.8);
        int top = lengthBasedOnDisplayHeight(0.02);
        ((ConstraintLayout.LayoutParams)currentButtonDescription.getLayoutParams()).setMargins(0, top, 0, 0);

        gestureDetector = new GestureDetector(getActivity().getApplicationContext(), this);

        return view;
    }

    @Override
    public void onClick(View v) {
        if(clickReady) clickButton();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        if(event.getAction() != MotionEvent.ACTION_MOVE && event.getAction() != MotionEvent.ACTION_SCROLL
                && v.getId() == R.id.grey_circle){
            greyCircle.setScaleX(1.5f);
            greyCircle.setScaleY(1.5f);
        }
        if(event.getAction() == MotionEvent.ACTION_UP) {
            if(v.getId() == R.id.grey_circle) {
                greyCircle.setScaleX(1.0f);
                greyCircle.setScaleY(1.0f);
                greyCircle.performClick();
            }
            else v.performClick();
        }
        return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if(clickReady && distanceY !=0){
            int direction;
            clickReady = false;
            direction = (distanceY > 0) ? -1 : 1;
            greyCircle.setVisibility(View.INVISIBLE);
            for (final Button button : buttons) {
                int position = button.position;
                ImageView image = button.image;
                if (position == 0) image.animate().scaleXBy(-SELECTED_SCALE).scaleYBy(-SELECTED_SCALE);
                if (direction == 1 && position == (buttons.length - 1)) {
                    image.animate().scaleXBy(SELECTED_SCALE).scaleYBy(SELECTED_SCALE);
                    image.animate().withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            updateSelection(button.name, (String) button.image.getContentDescription());
                        }
                    });
                }
                else if (direction == -1 && position == 1) {
                    image.animate().scaleXBy(SELECTED_SCALE).scaleYBy(SELECTED_SCALE);
                    image.animate().withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            updateSelection(button.name, (String) button.image.getContentDescription());
                        }
                    });
                }
                int newPosition = (position + direction)%buttons.length;
                if(newPosition < 0 ) newPosition = buttons.length - 1;
                button.position = newPosition;
                float newX = (float) (rotatingMenuRadius * Math.cos(angles[newPosition]));
                float newY = (float) (rotatingMenuRadius * Math.sin(angles[newPosition]));
                image.animate().translationX(newX).translationY(newY);
                image.animate().setDuration(200).start();
            }
        } else return false;
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    private void updateSelection(String name, String description){
        clickReady = true;
        greyCircle.setVisibility(View.VISIBLE);
        currentButtonNameDisplay.setText(name);
        currentButtonDescription.setText(description);
        currentButtonName = name;
    }

    private void clickButton(){
        switch(currentButtonName){
            case "LOCK MODE":
                getFragmentManager().beginTransaction().replace(android.R.id.content, new AudioSpyFragment(), "LOCK MODE")
                    .addToBackStack(null).commit();
                break;
            case "START RECORDING":
                getFragmentManager().beginTransaction().replace(android.R.id.content, new StartRecordingFragment(), "START RECORDING")
                    .addToBackStack(null).commit();
                break;
            case "OPEN SAVE DIRECTORY":
                getFragmentManager().beginTransaction().replace(android.R.id.content, new OpenSaveDirectoryFragment(), "OPEN SAVE DIRECTORY")
                     .addToBackStack(null).commit();
                break;
            case "CHANGE SAVE DIRECTORY":
                getFragmentManager().beginTransaction().replace(android.R.id.content, new ChangeSaveDirectoryFragment(), "CHANGE SAVE DIRECTORY")
                    .addToBackStack(null).commit();
                break;
            case "SEND RECORDING":
                getFragmentManager().beginTransaction().replace(android.R.id.content, new SendRecordingFragment(), "SEND RECORDING")
                        .addToBackStack(null).commit();
                break;
            case "OTHER SETTINGS":
                getFragmentManager().beginTransaction().replace(android.R.id.content, new OtherSettingsFragment(), "OTHER SETTINGS")
                        .addToBackStack(null).commit();
                break;
            case "CHOOSE OUTPUT FORMAT":
                getFragmentManager().beginTransaction().replace(android.R.id.content, new ChooseOutputFormatFragment(), "CHOOSE OUTPUT FORMAT")
                    .addToBackStack(null).commit();
                break;
            case "ADJUST MIC SENSITIVITY":
                getFragmentManager().beginTransaction().replace(android.R.id.content, new AdjustMicSensitivityFragment(), "ADJUST MIC SENSITIVITY")
                    .addToBackStack(null).commit();
                break;
            default:
                break;
        }
    }

    private int lengthBasedOnDisplayWidth(double ratioToDisplayWidth){
        return (int) Math.ceil(ratioToDisplayWidth*(double)width);
    }

    private int lengthBasedOnDisplayHeight(double ratioToDisplayHeight){
        return (int) Math.ceil(ratioToDisplayHeight*(double)height);
    }
}
