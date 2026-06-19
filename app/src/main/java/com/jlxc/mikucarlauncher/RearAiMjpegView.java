package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RearAiMjpegView extends View {
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean running = false;
    private volatile String streamUrl = "";
    private Thread streamThread;
    private Bitmap currentFrame;
    private String message = "等待后置 AI 视频流";

    public RearAiMjpegView(Context context) {
        super(context);
        bgPaint.setColor(Color.rgb(8, 12, 18));
        textPaint.setColor(Color.rgb(210, 225, 240));
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setBackgroundColor(Color.BLACK);
    }

    public synchronized void startStream(String url) {
        if (url == null) url = "";
        url = url.trim();
        if (url.length() == 0) {
            setMessage("未设置后置手机 IP");
            stopStream();
            return;
        }
        if (running && url.equals(streamUrl)) {
            return;
        }
        stopStream();
        streamUrl = url;
        running = true;
        setMessage("正在连接后置 AI 视频流…");
        streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "Miku-RearAI-MJPEG");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    public synchronized void stopStream() {
        running = false;
        Thread t = streamThread;
        streamThread = null;
        if (t != null) {
            try {
                t.interrupt();
            } catch (Throwable ignored) {
            }
        }
        currentFrame = null;
        setMessage("等待后置 AI 视频流");
    }

    public void setMessage(final String msg) {
        message = msg == null ? "" : msg;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });
    }

    private void readLoop() {
        while (running) {
            HttpURLConnection conn = null;
            InputStream input = null;
            try {
                URL url = new URL(streamUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1200);
                conn.setReadTimeout(1800);
                conn.setUseCaches(false);
                conn.setRequestProperty("Connection", "close");
                input = new BufferedInputStream(conn.getInputStream(), 64 * 1024);
                setMessage("");
                readJpegFrames(input);
            } catch (Throwable t) {
                if (running) {
                    setMessage("后置 AI 视频流连接中断，正在重连…");
                    sleepQuietly(700L);
                }
            } finally {
                try {
                    if (input != null) input.close();
                } catch (Throwable ignored) {
                }
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private void readJpegFrames(InputStream input) throws Exception {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(256 * 1024);
        boolean inJpeg = false;
        int prev = -1;
        int b;
        while (running && (b = input.read()) != -1) {
            if (!inJpeg) {
                if (prev == 0xFF && b == 0xD8) {
                    inJpeg = true;
                    frame.reset();
                    frame.write(0xFF);
                    frame.write(0xD8);
                }
            } else {
                frame.write(b);
                if (prev == 0xFF && b == 0xD9) {
                    byte[] data = frame.toByteArray();
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bmp != null) {
                        currentFrame = bmp;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                invalidate();
                            }
                        });
                    }
                    inJpeg = false;
                    frame.reset();
                } else if (frame.size() > 4 * 1024 * 1024) {
                    // 防止异常流导致内存持续上涨。
                    inJpeg = false;
                    frame.reset();
                }
            }
            prev = b;
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        Bitmap bmp = currentFrame;
        if (bmp != null && !bmp.isRecycled()) {
            RectF dst = new RectF(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(bmp, null, dst, bitmapPaint);
        } else if (message != null && message.length() > 0) {
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(message, getWidth() / 2f, y, textPaint);
        }
    }
}
