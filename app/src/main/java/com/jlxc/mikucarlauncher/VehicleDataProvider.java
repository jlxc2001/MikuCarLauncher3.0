package com.jlxc.mikucarlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;

import java.util.Arrays;

public class VehicleDataProvider {
    private static final String DESCRIPTOR_CAR_INFO = "com.ts.can.carinfo.ICarInfoService";
    private static final String SERVICE_PACKAGE = "com.ts.MainUI";
    private static final String CAR_INFO_ACTION = "com.ts.can.carinfo.CarInfoService";
    private static final String CAR_INFO_CLASS = "com.ts.can.carinfo.CarInfoService";

    private static final String SPEECH_ACTION = "com.ts.tsspeechlib.car.TsCarService";
    private static final String SPEECH_CLASS = "com.ts.tsspeechlib.car.TsCarService";
    private static final String TOKEN_SPEECH_CAR = "com.ts.tsspeechlib.car.ITsSpeechCar";

    public static final String PREF_POLL_INTERVAL_MS = "vehicle_hook_poll_interval_ms";
    public static final String PREF_TURN_SOUND_ENABLED = "turn_sound_enabled";
    public static final String PREF_TURN_SOUND_URI = "turn_sound_uri";
    public static final String PREF_TURN_SOUND_NAME = "turn_sound_name";
    public static final String PREF_TURN_DEBUG_OVERLAY = "turn_debug_overlay";
    public static final int DEFAULT_POLL_INTERVAL_MS = 650;

    private final Context context;
    private final Object lock = new Object();

    private HandlerThread workerThread;
    private Handler workerHandler;

    private IBinder carInfoBinder;
    private IBinder speechBinder;
    private boolean carInfoBound;
    private boolean speechBound;
    private boolean started;
    private int baseInfoTransactionCode = -1;
    private int[] previousBaseInfo;

    private volatile Snapshot snapshot = Snapshot.empty();

    public VehicleDataProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void start() {
        synchronized (lock) {
            if (started) return;
            started = true;
        }

        if (workerThread == null) {
            workerThread = new HandlerThread("MikuCarLauncher-VehicleHookData");
            workerThread.start();
            workerHandler = new Handler(workerThread.getLooper());
        }

        bindAllServices();
        workerHandler.removeCallbacks(pollRunnable);
        workerHandler.post(pollRunnable);
    }

    public void stop() {
        synchronized (lock) {
            started = false;
        }

        try {
            if (carInfoBound) context.unbindService(carInfoConnection);
        } catch (Throwable ignored) {
        }
        try {
            if (speechBound) context.unbindService(speechConnection);
        } catch (Throwable ignored) {
        }

        carInfoBound = false;
        speechBound = false;
        carInfoBinder = null;
        speechBinder = null;
        baseInfoTransactionCode = -1;
        previousBaseInfo = null;

        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
        }
        workerHandler = null;
    }

    private void bindAllServices() {
        bindCarInfoService();
        bindSpeechService();
    }

    private void bindCarInfoService() {
        if (carInfoBound) return;

        try {
            Intent intent = new Intent(CAR_INFO_ACTION);
            intent.setPackage(SERVICE_PACKAGE);
            intent.setClassName(SERVICE_PACKAGE, CAR_INFO_CLASS);
            carInfoBound = context.bindService(intent, carInfoConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) {
            carInfoBound = false;
        }
    }

    private void bindSpeechService() {
        if (speechBound) return;

        try {
            Intent intent = new Intent(SPEECH_ACTION);
            intent.setPackage(SERVICE_PACKAGE);
            intent.setClassName(SERVICE_PACKAGE, SPEECH_CLASS);
            speechBound = context.bindService(intent, speechConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) {
            speechBound = false;
        }
    }

    private final ServiceConnection carInfoConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            carInfoBinder = service;
            carInfoBound = true;
            baseInfoTransactionCode = -1;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            carInfoBinder = null;
            carInfoBound = false;
            baseInfoTransactionCode = -1;
            previousBaseInfo = null;
        }
    };

    private final ServiceConnection speechConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            speechBinder = service;
            speechBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            speechBinder = null;
            speechBound = false;
        }
    };

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                pollOnce();
            } catch (Throwable ignored) {
            }

            Handler handler = workerHandler;
            synchronized (lock) {
                if (started && handler != null) {
                    handler.postDelayed(this, getPollIntervalMs());
                }
            }
        }
    };

    private int getPollIntervalMs() {
        int v = DEFAULT_POLL_INTERVAL_MS;
        try {
            SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
            v = sp.getInt(PREF_POLL_INTERVAL_MS, DEFAULT_POLL_INTERVAL_MS);
        } catch (Throwable ignored) {
        }
        if (v < 500) v = 500;
        if (v > 10000) v = 10000;
        return v;
    }

    private void pollOnce() {
        if (carInfoBinder == null || !carInfoBinder.isBinderAlive()) {
            carInfoBinder = null;
            carInfoBound = false;
            bindCarInfoService();
        }
        if (speechBinder == null || !speechBinder.isBinderAlive()) {
            speechBinder = null;
            speechBound = false;
            bindSpeechService();
        }

        int[] baseInfo = requestBaseInfo();
        SpeechValues speech = readSpeechValues();

        if ((baseInfo == null || baseInfo.length == 0) && speech == null) {
            return;
        }

        snapshot = Snapshot.fromHookData(baseInfo, previousBaseInfo, speech, carInfoBound, speechBound);
        previousBaseInfo = baseInfo;
    }

    private int[] requestBaseInfo() {
        if (carInfoBinder == null) return null;

        if (baseInfoTransactionCode > 0) {
            int[] result = transactIntArray(carInfoBinder, DESCRIPTOR_CAR_INFO, baseInfoTransactionCode);
            if (isBaseInfo(result)) return result;
            baseInfoTransactionCode = -1;
        }

        for (int code = 1; code <= 50; code++) {
            int[] result = transactIntArray(carInfoBinder, DESCRIPTOR_CAR_INFO, code);
            if (isBaseInfo(result)) {
                baseInfoTransactionCode = code;
                return result;
            }
        }

        return null;
    }

    private boolean isBaseInfo(int[] data) {
        return data != null && data.length >= 82;
    }

    private SpeechValues readSpeechValues() {
        IBinder binder = speechBinder;
        if (binder == null) return null;

        SpeechValues s = new SpeechValues();
        s.totalMileageKm = transactInt(binder, TOKEN_SPEECH_CAR, 17);
        s.oilLeftover = transactInt(binder, TOKEN_SPEECH_CAR, 18);
        s.leftTurn = transactInt(binder, TOKEN_SPEECH_CAR, 19);
        s.rightTurn = transactInt(binder, TOKEN_SPEECH_CAR, 20);
        s.hazard = transactInt(binder, TOKEN_SPEECH_CAR, 21);
        s.speed = transactInt(binder, TOKEN_SPEECH_CAR, 22);
        s.lineEps = transactInt(binder, TOKEN_SPEECH_CAR, 23);
        s.frontRadar = transactIntArray(binder, TOKEN_SPEECH_CAR, 25);
        s.rearRadar = transactIntArray(binder, TOKEN_SPEECH_CAR, 26);
        s.mcuPowerState = transactInt(binder, TOKEN_SPEECH_CAR, 28);
        return s;
    }

    private Integer transactInt(IBinder binder, String token, int code) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            boolean ok = binder.transact(code, data, reply, 0);
            if (!ok) return null;
            reply.readException();
            return reply.readInt();
        } catch (Throwable ignored) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private int[] transactIntArray(IBinder binder, String token, int code) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            boolean ok = binder.transact(code, data, reply, 0);
            if (!ok) return null;
            reply.readException();
            return reply.createIntArray();
        } catch (Throwable ignored) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static final class SpeechValues {
        Integer totalMileageKm;
        Integer oilLeftover;
        Integer leftTurn;
        Integer rightTurn;
        Integer hazard;
        Integer speed;
        Integer lineEps;
        int[] frontRadar;
        int[] rearRadar;
        Integer mcuPowerState;
    }

    public static class Snapshot {
        public final boolean valid;
        public final int rangeKm;
        public final int fuelLevel;
        public final int speed;
        public final int rpm;
        public final boolean driverSeatbelt;
        public final boolean passengerSeatbelt;
        public final boolean frontLeftDoorOpen;
        public final boolean frontRightDoorOpen;
        public final boolean rearLeftDoorOpen;
        public final boolean rearRightDoorOpen;
        public final boolean trunkOpen;
        public final boolean hoodOpen;
        public final boolean leftTurnOn;
        public final boolean rightTurnOn;
        public final boolean highBeam;
        public final boolean hazard;
        public final int totalMileageKm;
        public final int[] frontRadar;
        public final int[] rearRadar;
        public final int[] rawBaseInfo;
        public final String dataSource;
        public final String debugText;
        public final String readableText;
        public final long updateElapsedMs;

        private Snapshot(
                boolean valid,
                int rangeKm,
                int fuelLevel,
                int speed,
                int rpm,
                boolean driverSeatbelt,
                boolean passengerSeatbelt,
                boolean frontLeftDoorOpen,
                boolean frontRightDoorOpen,
                boolean rearLeftDoorOpen,
                boolean rearRightDoorOpen,
                boolean trunkOpen,
                boolean hoodOpen,
                boolean leftTurnOn,
                boolean rightTurnOn,
                boolean highBeam,
                boolean hazard,
                int totalMileageKm,
                int[] frontRadar,
                int[] rearRadar,
                int[] rawBaseInfo,
                String dataSource,
                String debugText,
                String readableText,
                long updateElapsedMs
        ) {
            this.valid = valid;
            this.rangeKm = rangeKm;
            this.fuelLevel = fuelLevel;
            this.speed = speed;
            this.rpm = rpm;
            this.driverSeatbelt = driverSeatbelt;
            this.passengerSeatbelt = passengerSeatbelt;
            this.frontLeftDoorOpen = frontLeftDoorOpen;
            this.frontRightDoorOpen = frontRightDoorOpen;
            this.rearLeftDoorOpen = rearLeftDoorOpen;
            this.rearRightDoorOpen = rearRightDoorOpen;
            this.trunkOpen = trunkOpen;
            this.hoodOpen = hoodOpen;
            this.leftTurnOn = leftTurnOn;
            this.rightTurnOn = rightTurnOn;
            this.highBeam = highBeam;
            this.hazard = hazard;
            this.totalMileageKm = totalMileageKm;
            this.frontRadar = frontRadar;
            this.rearRadar = rearRadar;
            this.rawBaseInfo = rawBaseInfo;
            this.dataSource = dataSource;
            this.debugText = debugText;
            this.readableText = readableText;
            this.updateElapsedMs = updateElapsedMs;
        }

        public static Snapshot empty() {
            return new Snapshot(false, -1, -1, -1, -1,
                    false, false, false, false, false, false, false, false,
                    false, false, false, false, -1, null, null, null,
                    "none", "", "暂无有效车辆数据", 0L);
        }

        public static Snapshot fromHookData(int[] base, int[] previousBase, SpeechValues speech, boolean carInfoBound, boolean speechBound) {
            boolean valid = base != null && base.length > 0 && base[0] == 1;
            int speed = get(base, 2, speech != null && speech.speed != null ? speech.speed : -1);
            int rpm = get(base, 3, -1);
            int range = get(base, 13, -1);
            int fuel = get(base, 30, -1);

            boolean leftTurn = bool(base, 17);
            boolean rightTurn = bool(base, 18);
            boolean driverSeatbelt = bool(base, 19);
            boolean highBeam = bool(base, 20);
            boolean passengerSeatbelt = bool(base, 36);
            boolean fl = bool(base, 61);
            boolean fr = bool(base, 62);
            boolean rl = bool(base, 63);
            boolean rr = bool(base, 64);
            boolean trunk = bool(base, 65);
            boolean hood = bool(base, 66);

            int totalMileage = -1;
            boolean hazard = false;
            int[] frontRadar = null;
            int[] rearRadar = null;

            if (speech != null) {
                if (speech.leftTurn != null) leftTurn = speech.leftTurn == 1;
                if (speech.rightTurn != null) rightTurn = speech.rightTurn == 1;
                if (speech.hazard != null) hazard = speech.hazard == 1;
                if (speech.speed != null && (speed < 0 || speed == 0)) speed = speech.speed;
                if (speech.oilLeftover != null && fuel < 0) fuel = speech.oilLeftover;
                if (speech.totalMileageKm != null) totalMileage = speech.totalMileageKm;
                frontRadar = speech.frontRadar;
                rearRadar = speech.rearRadar;
            }

            String source = "CarInfo=" + (carInfoBound ? "OK" : "--")
                    + " / TsCar=" + (speechBound ? "OK" : "--")
                    + " / turn=" + (speech != null && (speech.leftTurn != null || speech.rightTurn != null) ? "TsCarService" : "baseInfo[17/18]");

            String debug = buildDebugText(base, previousBase, speech, source, leftTurn, rightTurn);
            String readable = buildReadableText(valid, speed, rpm, range, fuel, totalMileage,
                    driverSeatbelt, passengerSeatbelt, fl, fr, rl, rr, trunk, hood,
                    leftTurn, rightTurn, highBeam, hazard, frontRadar, rearRadar, source);

            return new Snapshot(valid, range, fuel, speed, rpm,
                    driverSeatbelt, passengerSeatbelt, fl, fr, rl, rr, trunk, hood,
                    leftTurn, rightTurn, highBeam, hazard, totalMileage, frontRadar, rearRadar,
                    base == null ? null : base.clone(), source, debug, readable, SystemClock.elapsedRealtime());
        }

        private static String buildReadableText(
                boolean valid, int speed, int rpm, int range, int fuel, int totalMileage,
                boolean driverSeatbelt, boolean passengerSeatbelt,
                boolean fl, boolean fr, boolean rl, boolean rr, boolean trunk, boolean hood,
                boolean leftTurn, boolean rightTurn, boolean highBeam, boolean hazard,
                int[] frontRadar, int[] rearRadar, String source
        ) {
            StringBuilder b = new StringBuilder();
            b.append("有效: ").append(valid).append("\n");
            b.append("车速: ").append(speed).append(" km/h\n");
            b.append("转速: ").append(rpm).append(" rpm\n");
            b.append("续航: ").append(range).append(" km\n");
            b.append("油量: ").append(fuel).append("\n");
            b.append("总里程: ").append(totalMileage).append(" km\n\n");
            b.append("主驾安全带: ").append(driverSeatbelt).append("\n");
            b.append("副驾安全带: ").append(passengerSeatbelt).append("\n\n");
            b.append("左前门: ").append(fl).append("\n");
            b.append("右前门: ").append(fr).append("\n");
            b.append("左后门: ").append(rl).append("\n");
            b.append("右后门: ").append(rr).append("\n");
            b.append("后备箱: ").append(trunk).append("\n");
            b.append("机盖: ").append(hood).append("\n\n");
            b.append("左转向: ").append(leftTurn).append("\n");
            b.append("右转向: ").append(rightTurn).append("\n");
            b.append("远光灯: ").append(highBeam).append("\n");
            b.append("双闪: ").append(hazard).append("\n\n");
            b.append("前雷达: ").append(frontRadar == null ? "null" : Arrays.toString(frontRadar)).append("\n");
            b.append("后雷达: ").append(rearRadar == null ? "null" : Arrays.toString(rearRadar)).append("\n\n");
            b.append("数据源: ").append(source);
            return b.toString();
        }

        private static String buildDebugText(int[] base, int[] prev, SpeechValues speech, String source, boolean leftTurn, boolean rightTurn) {
            StringBuilder sb = new StringBuilder();
            sb.append(source);
            sb.append(" | parsed L=").append(leftTurn).append(" R=").append(rightTurn);
            if (speech != null) {
                sb.append(" | speech: total=").append(value(speech.totalMileageKm));
                sb.append(" oil=").append(value(speech.oilLeftover));
                sb.append(" L=").append(value(speech.leftTurn));
                sb.append(" R=").append(value(speech.rightTurn));
                sb.append(" hazard=").append(value(speech.hazard));
                sb.append(" speed=").append(value(speech.speed));
            }
            sb.append(" | base key:");
            appendIndex(sb, base, 0);
            appendIndex(sb, base, 2);
            appendIndex(sb, base, 3);
            appendIndex(sb, base, 13);
            appendIndex(sb, base, 17);
            appendIndex(sb, base, 18);
            appendIndex(sb, base, 19);
            appendIndex(sb, base, 20);
            appendIndex(sb, base, 30);
            appendIndex(sb, base, 36);
            appendIndex(sb, base, 61);
            appendIndex(sb, base, 62);
            appendIndex(sb, base, 63);
            appendIndex(sb, base, 64);
            appendIndex(sb, base, 65);
            appendIndex(sb, base, 66);

            if (prev != null && base != null) {
                sb.append(" | changed:");
                int count = 0;
                int max = Math.min(Math.min(base.length, prev.length), 120);
                for (int i = 0; i < max && count < 18; i++) {
                    if (base[i] != prev[i]) {
                        if (count > 0) sb.append(",");
                        sb.append(i).append(":").append(prev[i]).append(">").append(base[i]);
                        count++;
                    }
                }
                if (count == 0) sb.append("none");
            }
            return sb.toString();
        }

        private static void appendIndex(StringBuilder sb, int[] b, int index) {
            sb.append(" [").append(index).append("]=");
            if (b == null || index < 0 || index >= b.length) {
                sb.append("null");
            } else {
                sb.append(b[index]);
            }
        }

        private static String value(Object v) {
            return v == null ? "null" : String.valueOf(v);
        }

        private static int get(int[] base, int index, int def) {
            return base != null && base.length > index ? base[index] : def;
        }

        private static boolean bool(int[] base, int index) {
            return base != null && base.length > index && base[index] == 1;
        }
    }
}
