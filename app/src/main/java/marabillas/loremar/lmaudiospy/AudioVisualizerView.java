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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Loremar on 16/02/2018.
 * AudioVisualizerView
 */

public class AudioVisualizerView extends View {
    private Paint graphPaint;
    private List<Integer> graphData;
    private static final int VALUE_TO_HEIGHT_SCALE = 100;
    public AudioVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        graphPaint = new Paint();
        graphPaint.setColor(Color.GREEN);
        graphPaint.setStrokeWidth(1);
        graphData = new ArrayList<>();
    }

    void addValuetoGraph(int value){
        value /= VALUE_TO_HEIGHT_SCALE;
        graphData.add(value);
        if(graphData.size() >= getWidth()) graphData.remove(0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int graphHeight = getHeight()/2;
        canvas.drawLine(0, graphHeight, getWidth(), graphHeight, graphPaint);
        if(!graphData.isEmpty()) {
            for (int i = 0; i < graphData.size() - 1; i++) {
                canvas.drawLine(
                        getWidth() - graphData.size() + i,
                        graphHeight - graphData.get(i),
                        getWidth() - graphData.size() + i,
                        graphHeight + graphData.get(i),
                        graphPaint);
            }
        }
    }
}
