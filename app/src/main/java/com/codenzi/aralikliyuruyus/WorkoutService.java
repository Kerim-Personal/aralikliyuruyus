package com.codenzi.aralikliyuruyus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WorkoutService extends Service implements SensorEventListener {

    // Service Actions
    public static final String ACTION_START_WORKOUT = "com.codenzi.aralikliyuruyus.ACTION_START_WORKOUT";
    public static final String ACTION_PAUSE_WORKOUT = "com.codenzi.aralikliyuruyus.ACTION_PAUSE_WORKOUT";
    public static final String ACTION_RESUME_WORKOUT = "com.codenzi.aralikliyuruyus.ACTION_RESUME_WORKOUT";
    public static final String ACTION_RESET_WORKOUT = "com.codenzi.aralikliyuruyus.ACTION_RESET_WORKOUT";

    // Notification
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "WorkoutServiceChannel";

    // Workout Constants
    private static final long TOTAL_WORKOUT_TIME_MS = 30 * 60 * 1000;
    private static final long INTERVAL_TIME_MS = 3 * 60 * 1000;

    // State Variables
    public boolean isWorkoutRunning = false, isHighIntensity = false, isWorkoutPaused = false;
    private int cycleCount = 0;
    private int sessionStepCount = 0;
    private long workoutTimeRemaining, intervalTimeRemaining;

    // Components
    private final IBinder binder = new LocalBinder();
    private CountDownTimer workoutTimer, intervalTimer;
    private SensorManager sensorManager;
    private Sensor stepDetectorSensor;
    private TextToSpeech textToSpeech;
    private boolean isTtsInitialized = false;
    private WorkoutListener listener;

    // GETTER METODLARI EKLENDİ
    public long getWorkoutTimeRemaining() {
        return workoutTimeRemaining;
    }

    public long getIntervalTimeRemaining() {
        return intervalTimeRemaining;
    }

    public int getSessionStepCount() {
        return sessionStepCount;
    }

    public class LocalBinder extends Binder {
        WorkoutService getService() {
            return WorkoutService.this;
        }
    }

    public interface WorkoutListener {
        void onTimerUpdate(long totalMillis, long intervalMillis);
        void onStepUpdate(int steps);
        void onInstructionUpdate(int instructionResId, int backgroundResId);
        void onStateChange(boolean isRunning, boolean isPaused);
        void onWorkoutFinished();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        initializeTextToSpeech();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START_WORKOUT:
                    startWorkout();
                    break;
                case ACTION_PAUSE_WORKOUT:
                    pauseWorkout();
                    break;
                case ACTION_RESUME_WORKOUT:
                    resumeWorkout();
                    break;
                case ACTION_RESET_WORKOUT:
                    resetWorkout();
                    break;
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setListener(WorkoutListener listener) {
        this.listener = listener;
    }

    // --- WORKOUT LOGIC ---

    private void startWorkout() {
        isWorkoutRunning = true;
        isWorkoutPaused = false;
        isHighIntensity = false;
        cycleCount = 0;
        sessionStepCount = 0;

        if (listener != null) {
            listener.onStateChange(isWorkoutRunning, isWorkoutPaused);
            listener.onStepUpdate(sessionStepCount);
        }

        sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        startForeground(NOTIFICATION_ID, createNotification("Antrenman Başladı..."));
        startTotalWorkoutTimer(TOTAL_WORKOUT_TIME_MS);
        startNextInterval();
    }

    private void pauseWorkout() {
        if (!isWorkoutRunning) return;

        isWorkoutRunning = false;
        isWorkoutPaused = true;

        if (workoutTimer != null) workoutTimer.cancel();
        if (intervalTimer != null) intervalTimer.cancel();

        speak(getString(R.string.tts_workout_paused));
        if (listener != null) {
            listener.onInstructionUpdate(R.string.instruction_workout_paused, R.drawable.gradient_paused);
            listener.onStateChange(isWorkoutRunning, isWorkoutPaused);
        }
        updateNotification("Antrenman Duraklatıldı");
    }

    private void resumeWorkout() {
        if (!isWorkoutPaused) return;

        isWorkoutRunning = true;
        isWorkoutPaused = false;

        if (listener != null) {
            listener.onStateChange(isWorkoutRunning, isWorkoutPaused);
        }

        int instructionResId = isHighIntensity ? R.string.instruction_go_fast : R.string.instruction_go_slow;
        int gradientResId = isHighIntensity ? R.drawable.gradient_fast : R.drawable.gradient_slow;
        if (listener != null) {
            listener.onInstructionUpdate(instructionResId, gradientResId);
        }
        speak(getString(instructionResId));
        updateNotification(getString(instructionResId));

        startTotalWorkoutTimer(workoutTimeRemaining);
        startIntervalTimer(intervalTimeRemaining);
    }

    private void resetWorkout() {
        if (workoutTimer != null) workoutTimer.cancel();
        if (intervalTimer != null) intervalTimer.cancel();

        isWorkoutRunning = false;
        isWorkoutPaused = false;
        sessionStepCount = 0;

        speak(getString(R.string.tts_workout_stopped));
        sensorManager.unregisterListener(this);
        if (listener != null) {
            listener.onInstructionUpdate(R.string.instruction_ready, R.drawable.gradient_idle);
            listener.onStateChange(isWorkoutRunning, isWorkoutPaused);
            listener.onTimerUpdate(TOTAL_WORKOUT_TIME_MS, INTERVAL_TIME_MS);
            listener.onStepUpdate(0);
        }

        stopForeground(true);
        stopSelf();
    }

    private void finishWorkout() {
        if (workoutTimer != null) workoutTimer.cancel();
        if (intervalTimer != null) intervalTimer.cancel();

        saveWorkoutSession();

        isWorkoutRunning = false;
        isWorkoutPaused = false;
        speak(getString(R.string.tts_workout_complete));
        sensorManager.unregisterListener(this);
        if (listener != null) {
            listener.onWorkoutFinished();
        }

        stopForeground(true);
        stopSelf();
    }


    private void startTotalWorkoutTimer(long duration) {
        workoutTimer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                workoutTimeRemaining = millisUntilFinished;
                if (listener != null) listener.onTimerUpdate(workoutTimeRemaining, intervalTimeRemaining);
                updateNotification(formatTime(workoutTimeRemaining) + " | " + getString(isHighIntensity ? R.string.instruction_go_fast : R.string.instruction_go_slow));
            }
            @Override
            public void onFinish() {
                finishWorkout();
            }
        }.start();
    }

    private void startNextInterval() {
        if (cycleCount >= (TOTAL_WORKOUT_TIME_MS / INTERVAL_TIME_MS)) {
            finishWorkout();
            return;
        }

        isHighIntensity = !isHighIntensity;
        cycleCount++;

        int instructionResId = isHighIntensity ? R.string.instruction_go_fast : R.string.instruction_go_slow;
        int gradientResId = isHighIntensity ? R.drawable.gradient_fast : R.drawable.gradient_slow;

        if (listener != null) {
            listener.onInstructionUpdate(instructionResId, gradientResId);
        }
        speak(getString(instructionResId));
        startIntervalTimer(INTERVAL_TIME_MS);
    }

    private void startIntervalTimer(long duration) {
        intervalTimer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                intervalTimeRemaining = millisUntilFinished;
                if (listener != null) listener.onTimerUpdate(workoutTimeRemaining, intervalTimeRemaining);
            }
            @Override
            public void onFinish() {
                startNextInterval();
            }
        }.start();
    }

    // --- SENSOR ---

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR && isWorkoutRunning) {
            sessionStepCount++;
            if (listener != null) listener.onStepUpdate(sessionStepCount);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // --- HELPERS ---

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("tr", "TR"));
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsInitialized = true;
                }
            }
        });
    }

    private void speak(String text) {
        if (isTtsInitialized) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void saveWorkoutSession() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        long durationInSeconds = (TOTAL_WORKOUT_TIME_MS - workoutTimeRemaining) / 1000;
        WorkoutSession session = new WorkoutSession(currentDate, sessionStepCount, durationInSeconds);
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            db.workoutDao().insertSession(session);
        });
    }

    private String formatTime(long millis) {
        return String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }

    // --- NOTIFICATION ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Workout Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification(String text) {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Aralıklı Yürüyüş Aktif")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_history) // Kendi ikonunuzu kullanın
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification(String text) {
        Notification notification = createNotification(text);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        resetWorkout();
        super.onDestroy();
    }
}