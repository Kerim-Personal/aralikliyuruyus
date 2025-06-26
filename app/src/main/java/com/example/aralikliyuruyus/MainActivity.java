package com.example.aralikliyuruyus;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final long TOTAL_WORKOUT_TIME_MS = 4 * 60 * 1000;
    private static final long INTERVAL_TIME_MS = 3 * 60 * 1000;
    private static final int ACTIVITY_RECOGNITION_REQUEST_CODE = 100;

    private TextView tvInstruction, tvTimer, tvIntervalTimer, tvStepCount;
    private Button btnStartStop, btnReset, btnHistory;
    private ProgressBar progressBarTotal, progressBarInterval;
    private CountDownTimer workoutTimer, intervalTimer;
    private SensorManager sensorManager;
    private Sensor stepDetectorSensor;
    private int sessionStepCount = 0;
    private boolean isWorkoutRunning = false, isHighIntensity = false;
    private int cycleCount = 0;
    private TextToSpeech textToSpeech;
    private boolean isTtsInitialized = false;

    private long workoutTimeRemaining, intervalTimeRemaining;
    private boolean isWorkoutPaused = false;
    private View mainLayout;
    private int currentBackgroundColor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initializeTextToSpeech();

        btnStartStop.setOnClickListener(v -> {
            if (isWorkoutRunning) {
                pauseWorkout();
            } else if(isWorkoutPaused){
                resumeWorkout();
            }
            else {
                checkAndStartWorkout();
            }
        });

        btnReset.setOnClickListener(v -> resetWorkout());

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        updateUIToInitialState();
    }

    private void initViews() {
        tvInstruction = findViewById(R.id.tvInstruction);
        tvTimer = findViewById(R.id.tvTimer);
        tvIntervalTimer = findViewById(R.id.tvIntervalTimer);
        tvStepCount = findViewById(R.id.tvStepCount);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnReset = findViewById(R.id.btnReset);
        btnHistory = findViewById(R.id.btnHistory);
        mainLayout = findViewById(R.id.main_layout);
        progressBarTotal = findViewById(R.id.progress_bar_total);
        progressBarInterval = findViewById(R.id.progress_bar_interval);
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("tr", "TR"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, R.string.tts_not_supported_toast, Toast.LENGTH_SHORT).show();
                } else {
                    isTtsInitialized = true;
                }
            } else {
                Toast.makeText(this, R.string.tts_init_failed_toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAndStartWorkout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, ACTIVITY_RECOGNITION_REQUEST_CODE);
            } else {
                startWorkout();
            }
        } else {
            startWorkout();
        }
    }

    private void startWorkout() {
        isWorkoutRunning = true;
        isWorkoutPaused = false;
        isHighIntensity = false;
        cycleCount = 0;
        sessionStepCount = 0;

        btnStartStop.setText(R.string.button_stop_workout);
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.stop_button_color));

        btnReset.setVisibility(View.INVISIBLE);
        btnHistory.setVisibility(View.INVISIBLE);

        tvStepCount.setText(getString(R.string.initial_step_count));
        startTotalWorkoutTimer(TOTAL_WORKOUT_TIME_MS);
        startNextInterval();
    }

    private void resetWorkout() {
        if (workoutTimer != null) workoutTimer.cancel();
        if (intervalTimer != null) intervalTimer.cancel();

        isWorkoutRunning = false;
        isWorkoutPaused = false;
        sessionStepCount = 0;

        speak(getString(R.string.tts_workout_stopped));
        updateUIToInitialState();
    }

    private void pauseWorkout() {
        if (!isWorkoutRunning) return;

        isWorkoutRunning = false;
        isWorkoutPaused = true;

        if (workoutTimer != null) workoutTimer.cancel();
        if (intervalTimer != null) intervalTimer.cancel();

        tvInstruction.setText(R.string.instruction_workout_paused);
        btnStartStop.setText(R.string.button_resume_workout);
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));

        btnReset.setVisibility(View.VISIBLE);
        btnHistory.setVisibility(View.VISIBLE);

        animateBackgroundColor(ContextCompat.getColor(this, R.color.background_default));
        speak(getString(R.string.tts_workout_paused));
    }

    private void resumeWorkout(){
        if(!isWorkoutPaused) return;

        isWorkoutRunning = true;
        isWorkoutPaused = false;

        btnStartStop.setText(R.string.button_stop_workout);
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.stop_button_color));

        btnReset.setVisibility(View.INVISIBLE);
        btnHistory.setVisibility(View.INVISIBLE);

        int instructionResId = isHighIntensity ? R.string.instruction_go_fast : R.string.instruction_go_slow;
        tvInstruction.setText(instructionResId);
        speak(getString(instructionResId));

        int colorTo = isHighIntensity ? R.color.background_fast_tint : R.color.background_slow_tint;
        animateBackgroundColor(ContextCompat.getColor(this, colorTo));

        startTotalWorkoutTimer(workoutTimeRemaining);
        startIntervalTimer(intervalTimeRemaining);
    }


    private void startTotalWorkoutTimer(long duration) {
        workoutTimer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                workoutTimeRemaining = millisUntilFinished;
                updateTimerText(tvTimer, millisUntilFinished);
                int progress = (int) ((millisUntilFinished * 100) / TOTAL_WORKOUT_TIME_MS);
                progressBarTotal.setProgress(progress);
            }
            @Override
            public void onFinish() {
                finishWorkout();
            }
        }.start();
    }

    private void startNextInterval() {
        if (cycleCount >= 10) {
            finishWorkout();
            return;
        }

        isHighIntensity = !isHighIntensity;
        cycleCount++;

        int instructionResId = isHighIntensity ? R.string.instruction_go_fast : R.string.instruction_go_slow;
        animateInstructionChange(instructionResId, getString(instructionResId));

        int colorTo = isHighIntensity ? R.color.background_fast_tint : R.color.background_slow_tint;
        animateBackgroundColor(ContextCompat.getColor(this, colorTo));

        startIntervalTimer(INTERVAL_TIME_MS);
    }


    private void startIntervalTimer(long duration) {
        intervalTimer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                intervalTimeRemaining = millisUntilFinished;
                updateTimerText(tvIntervalTimer, millisUntilFinished);
                int progress = (int) ((millisUntilFinished * 100) / INTERVAL_TIME_MS);
                progressBarInterval.setProgress(progress);
            }
            @Override
            public void onFinish() { startNextInterval(); }
        }.start();
    }

    private void animateInstructionChange(int instructionResId, String ttsText) {
        final Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        final Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                tvInstruction.setText(instructionResId);
                tvInstruction.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        tvInstruction.startAnimation(fadeOut);
        speak(ttsText);
    }

    private void animateBackgroundColor(int colorTo) {
        int colorFrom = (mainLayout.getBackground() instanceof ColorDrawable) ?
                ((ColorDrawable) mainLayout.getBackground()).getColor() :
                ContextCompat.getColor(this, R.color.background);

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(500);
        colorAnimation.addUpdateListener(animator -> mainLayout.setBackgroundColor((int) animator.getAnimatedValue()));
        colorAnimation.start();
        currentBackgroundColor = colorTo;
    }


    private void finishWorkout() {
        isWorkoutRunning = false;
        isWorkoutPaused = false;

        if (workoutTimer != null) workoutTimer.cancel();
        if (intervalTimer != null) intervalTimer.cancel();
        tvInstruction.setText(R.string.instruction_workout_finished);
        btnStartStop.setText(R.string.button_start_workout);
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));

        btnReset.setVisibility(View.VISIBLE);
        btnHistory.setVisibility(View.VISIBLE);

        animateBackgroundColor(ContextCompat.getColor(this, R.color.background_default));
        speak(getString(R.string.tts_workout_complete));
        saveWorkoutSession();
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

        Toast.makeText(this, "Antrenman kaydedildi!", Toast.LENGTH_SHORT).show();
    }


    @SuppressLint("SetTextI18n")
    private void updateTimerText(TextView timerView, long millis) {
        String time = String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
        timerView.setText(time);
    }

    private void updateUIToInitialState() {
        tvInstruction.setText(R.string.instruction_ready);
        btnStartStop.setText(R.string.button_start_workout);
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));

        btnReset.setVisibility(View.INVISIBLE);
        btnHistory.setVisibility(View.VISIBLE);

        updateTimerText(tvTimer, TOTAL_WORKOUT_TIME_MS);
        updateTimerText(tvIntervalTimer, INTERVAL_TIME_MS);
        tvStepCount.setText(R.string.initial_step_count);
        progressBarTotal.setProgress(100);
        progressBarInterval.setProgress(100);
        mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.background_default));
        currentBackgroundColor = ContextCompat.getColor(this, R.color.background_default);
    }


    private void speak(String text) {
        if (isTtsInitialized) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }


    @SuppressLint("SetTextI18n")
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR && (isWorkoutRunning)) {
            sessionStepCount++;
            tvStepCount.setText(String.valueOf(sessionStepCount));

            // Adım sayacı için zıplama efekti
            PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.2f, 1.0f);
            PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.2f, 1.0f);
            ObjectAnimator.ofPropertyValuesHolder(tvStepCount, pvhX, pvhY).setDuration(200).start();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }


    @Override
    protected void onResume() {
        super.onResume();
        if (stepDetectorSensor != null) {
            sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if(isWorkoutRunning){
            pauseWorkout();
        }

        if (stepDetectorSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ACTIVITY_RECOGNITION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWorkout();
            } else {
                Toast.makeText(this, R.string.permission_denied_toast, Toast.LENGTH_LONG).show();
            }
        }
    }
}