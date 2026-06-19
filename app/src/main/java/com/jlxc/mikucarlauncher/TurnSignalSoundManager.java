package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;

public class TurnSignalSoundManager {
    public static final int STATE_NONE = 0;
    public static final int STATE_LEFT = 1;
    public static final int STATE_RIGHT = 2;
    public static final int STATE_BOTH = 3;

    private final Context context;
    private MediaPlayer player;
    private int playingState = STATE_NONE;
    private String playingUri = "";

    public TurnSignalSoundManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void update(int turnState) {
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        boolean enabled = sp.getBoolean(VehicleDataProvider.PREF_TURN_SOUND_ENABLED, false);
        String uri = sp.getString(VehicleDataProvider.PREF_TURN_SOUND_URI, "");

        if (!enabled || turnState == STATE_NONE || TextUtils.isEmpty(uri)) {
            stop();
            return;
        }

        if (player != null && playingState == turnState && uri.equals(playingUri) && player.isPlaying()) {
            return;
        }

        stop();

        try {
            player = new MediaPlayer();
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            player.setDataSource(context, Uri.parse(uri));
            player.setLooping(true);
            player.prepare();
            player.start();
            playingState = turnState;
            playingUri = uri;
        } catch (Throwable ignored) {
            stop();
        }
    }

    public void stop() {
        if (player != null) {
            try {
                player.stop();
            } catch (Throwable ignored) {
            }
            try {
                player.release();
            } catch (Throwable ignored) {
            }
        }
        player = null;
        playingState = STATE_NONE;
        playingUri = "";
    }

    public void release() {
        stop();
    }
}
