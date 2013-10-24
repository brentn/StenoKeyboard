package com.brentandjody.stenokeyboard;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by brent on 20/10/13.
 */
public class TouchLayer extends LinearLayout {

    private static final int TOUCH_RADIUS = 25;
    private List<Button> keys = new ArrayList<Button>();
    private Button fKey;


    public TouchLayer(Context context) {
        super(context);
    }

    public TouchLayer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public TouchLayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        keys.clear();
        enumerateKeys(this);
        // all this is to set the height of the keyboard
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int screen_height = size.y;
        this.getLayoutParams().height = screen_height / 3;
    }

    private void enumerateKeys(View v) {
        //list steno keys IN ORDER
        fKey = (Button) v.findViewById(R.id._F);
        keys.add((Button) v.findViewById(R.id.number_bar));
        keys.add((Button) v.findViewById(R.id.S));
        keys.add((Button) v.findViewById(R.id.T));
        keys.add((Button) v.findViewById(R.id.K));
        keys.add((Button) v.findViewById(R.id.P));
        keys.add((Button) v.findViewById(R.id.W));
        keys.add((Button) v.findViewById(R.id.H));
        keys.add((Button) v.findViewById(R.id.R));
        keys.add((Button) v.findViewById(R.id.A));
        keys.add((Button) v.findViewById(R.id.O));
        keys.add((Button) v.findViewById(R.id.star));
        keys.add((Button) v.findViewById(R.id._E));
        keys.add((Button) v.findViewById(R.id._U));
        keys.add((Button) v.findViewById(R.id._F));
        keys.add((Button) v.findViewById(R.id._R));
        keys.add((Button) v.findViewById(R.id._P));
        keys.add((Button) v.findViewById(R.id._B));
        keys.add((Button) v.findViewById(R.id._L));
        keys.add((Button) v.findViewById(R.id._G));
        keys.add((Button) v.findViewById(R.id._T));
        keys.add((Button) v.findViewById(R.id._S));
        keys.add((Button) v.findViewById(R.id._D));
        keys.add((Button) v.findViewById(R.id._Z));
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // iterate over all keys, and toggle all those within TOUCH_RADIUS
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            checkKeyPressed(event, event.getX(), event.getY());
        }
//        switch (event.getAction() & MotionEvent.ACTION_MASK) {
//            case MotionEvent.ACTION_DOWN:
//                checkKeyPressed(event, event.getX(), event.getY());
//                break;
//            case MotionEvent.ACTION_POINTER_DOWN:
//                for (int i=0; i < event.getPointerCount(); i++) {
//                    checkKeyPressed(event, event.getX(i), event.getY(i));
//                }
//        }
        return true;
    }

    private void checkKeyPressed(MotionEvent event, float X, float Y) {
        float top, left, bottom, right;
        int[] location = new int[2];
        this.getLocationOnScreen(location);
        int Xoffset = location[0];
        int Yoffset = location[1];
        float minX = X + Xoffset - TOUCH_RADIUS;
        float maxX = X + Xoffset + TOUCH_RADIUS;
        float minY = Y + Yoffset - TOUCH_RADIUS;
        float maxY = Y + Yoffset + TOUCH_RADIUS;
        for (Button key:keys) {

            key.getLocationOnScreen(location);
            left = location[0];
            top = location[1];
            right = left+key.getWidth();
            bottom = top+key.getHeight();
            if ((maxX > left) && (minX < right)) {
                if ((maxY > top) && (minY < bottom)) {
                    key.setSelected(! key.isSelected());
                }
            }
        }
    }

    public String getStroke() {
        String result = "";
        Boolean hasVowels = false;
        for (Button key : keys) {
            if (key == fKey && (! hasVowels)) {
                result += "-";
            }
            if (key.isSelected()) {
                if ("*AOEU".contains(key.getText())) hasVowels = true;
                result += key.getText();
                key.setSelected(false);
            }
        }
        return result;
    }


}
