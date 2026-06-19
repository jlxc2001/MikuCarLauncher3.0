package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class Live2DGuideOverlayView extends View {
    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sidebarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Live2DGuideOverlayView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);

        cardPaint.setColor(Color.argb(92, 255, 255, 255));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.5f);
        strokePaint.setColor(Color.argb(170, 255, 255, 255));
        sidebarPaint.setColor(Color.argb(100, 40, 45, 55));
        textPaint.setColor(Color.argb(210, 255, 255, 255));
        textPaint.setTextSize(20f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float sx = getWidth() / DESIGN_W;
        float sy = getHeight() / DESIGN_H;
        canvas.save();
        canvas.scale(sx, sy);

        canvas.drawRect(0, 0, 190f, DESIGN_H, sidebarPaint);
        drawHintText(canvas, "左侧按钮栏", 42f, 360f);

        drawCard(canvas, new RectF(210f, 35.5f, 1140f, 528.5f), "1 导航小组件");
        drawCard(canvas, new RectF(1158f, 35.5f, 1550f, 350.5f), "2 音乐");
        drawCard(canvas, new RectF(1158f, 368.5f, 1550f, 528.5f), "3 蓝牙");
        drawCard(canvas, new RectF(210f, 546.5f, 1140f, 684.5f), "4 常用软件");
        drawCard(canvas, new RectF(1158f, 546.5f, 1952f, 684.5f), "5 车辆状态");
        drawCard(canvas, new RectF(1970f, 546.5f, 2396f, 684.5f), "6 天气");

        // 用户提到的模型放置区域：用蓝色虚线框提示。
        Paint liveArea = new Paint(Paint.ANTI_ALIAS_FLAG);
        liveArea.setStyle(Paint.Style.STROKE);
        liveArea.setStrokeWidth(3f);
        liveArea.setColor(Color.argb(220, 80, 170, 255));
        liveArea.setPathEffect(new android.graphics.DashPathEffect(new float[]{16f, 12f}, 0));

        RectF suggested = new RectF(1188f, 246f, 1708f, 546f);
        canvas.drawRoundRect(suggested, 18f, 18f, liveArea);
        textPaint.setColor(Color.argb(230, 140, 210, 255));
        textPaint.setTextSize(22f);
        canvas.drawText("建议模型区域：拖动人物到这里，双指捏合调大小", suggested.left + 14f, suggested.top - 12f, textPaint);

        canvas.restore();
    }

    private void drawCard(Canvas c, RectF rect, String label) {
        c.drawRoundRect(rect, 18f, 18f, cardPaint);
        c.drawRoundRect(rect, 18f, 18f, strokePaint);
        drawHintText(c, label, rect.left + 18f, rect.top + 34f);
    }

    private void drawHintText(Canvas c, String text, float x, float y) {
        textPaint.setColor(Color.argb(210, 255, 255, 255));
        textPaint.setTextSize(20f);
        c.drawText(text, x, y, textPaint);
    }
}
