package com.viceandroid.c64;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class DpadView extends View {
    interface Listener {
        void onDirections(boolean up, boolean down, boolean left, boolean right);
    }

    private static final int[] BASE = {0x665F6670, 0x99D6DADF};
    private static final int[] PRESSED = {0x99C5CBD3, 0xEEFFFFFF};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Listener listener;
    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;

    DpadView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void release() {
        if (up || down || left || right) {
            up = false;
            down = false;
            left = false;
            right = false;
            if (listener != null) {
                listener.onDirections(false, false, false, false);
            }
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float arm = Math.min(w, h) / 2f;
        float thick = arm * 0.66f;
        stroke.setStrokeWidth(Math.max(2f, w * 0.012f));

        drawArm(canvas, cx - thick / 2f, cy - arm, cx + thick / 2f, cy + arm, BASE);
        drawArm(canvas, cx - arm, cy - thick / 2f, cx + arm, cy + thick / 2f, BASE);
        if (up) {
            drawArm(canvas, cx - thick / 2f, cy - arm, cx + thick / 2f, cy, PRESSED);
        }
        if (down) {
            drawArm(canvas, cx - thick / 2f, cy, cx + thick / 2f, cy + arm, PRESSED);
        }
        if (left) {
            drawArm(canvas, cx - arm, cy - thick / 2f, cx, cy + thick / 2f, PRESSED);
        }
        if (right) {
            drawArm(canvas, cx, cy - thick / 2f, cx + arm, cy + thick / 2f, PRESSED);
        }
    }

    private void drawArm(Canvas canvas, float l, float t, float r, float b, int[] color) {
        rect.set(l, t, r, b);
        float radius = Math.min(rect.width(), rect.height()) * 0.22f;
        fill.setColor(color[0]);
        canvas.drawRoundRect(rect, radius, radius, fill);
        stroke.setColor(color[1]);
        canvas.drawRoundRect(rect, radius, radius, stroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                updateDirections(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
            default:
                return true;
        }
    }

    private void updateDirections(float x, float y) {
        float dx = x - getWidth() / 2f;
        float dy = y - getHeight() / 2f;
        float deadZone = getWidth() * 0.18f;
        boolean nextUp = dy < -deadZone;
        boolean nextDown = dy > deadZone;
        boolean nextLeft = dx < -deadZone;
        boolean nextRight = dx > deadZone;
        if (nextUp == up && nextDown == down && nextLeft == left && nextRight == right) {
            return;
        }
        up = nextUp;
        down = nextDown;
        left = nextLeft;
        right = nextRight;
        if (listener != null) {
            listener.onDirections(up, down, left, right);
        }
        invalidate();
    }
}
