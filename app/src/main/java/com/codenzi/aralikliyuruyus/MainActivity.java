package com.codenzi.aralikliyuruyus;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SensorEventListener, NavigationView.OnNavigationItemSelectedListener {

    private static final long TOTAL_WORKOUT_TIME_MS = 30 * 60 * 1000;
    private static final long INTERVAL_TIME_MS = 3 * 60 * 1000;
    private static final int ACTIVITY_RECOGNITION_REQUEST_CODE = 100;

    private TextView tvInstruction, tvTimer, tvIntervalTimer, tvStepCount;
    private Button btnStartStop, btnReset;
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

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private NavigationView navigationView;
    private Toolbar toolbar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initializeTextToSpeech();

        setSupportActionBar(toolbar);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        btnStartStop.setOnClickListener(v -> {
            if (isWorkoutRunning) {
                pauseWorkout();
            } else if (isWorkoutPaused) {
                resumeWorkout();
            } else {
                checkAndStartWorkout();
            }
        });

        btnReset.setOnClickListener(v -> resetWorkout());

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
        toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
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

    private void animateBackground(int newDrawableResId) {
        Drawable oldDrawable = mainLayout.getBackground();
        Drawable newDrawable = ContextCompat.getDrawable(this, newDrawableResId);
        TransitionDrawable transitionDrawable = new TransitionDrawable(new Drawable[]{oldDrawable, newDrawable});
        mainLayout.setBackground(transitionDrawable);
        transitionDrawable.startTransition(1000);
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
        animateBackground(R.drawable.gradient_idle);
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

        animateBackground(R.drawable.gradient_paused);
        speak(getString(R.string.tts_workout_paused));
    }

    private void resumeWorkout() {
        if (!isWorkoutPaused) return;

        isWorkoutRunning = true;
        isWorkoutPaused = false;

        btnStartStop.setText(R.string.button_stop_workout);
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.stop_button_color));

        btnReset.setVisibility(View.INVISIBLE);

        int instructionResId = isHighIntensity ? R.string.instruction_go_fast : R.string.instruction_go_slow;
        tvInstruction.setText(instructionResId);
        speak(getString(instructionResId));

        int gradientResId = isHighIntensity ? R.drawable.gradient_fast : R.drawable.gradient_slow;
        animateBackground(gradientResId);

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
        if (cycleCount >= (TOTAL_WORKOUT_TIME_MS / INTERVAL_TIME_MS)) {
            finishWorkout();
            return;
        }

        isHighIntensity = !isHighIntensity;
        cycleCount++;

        int instructionResId = isHighIntensity ? R.string.instruction_go_fast : R.string.instruction_go_slow;
        animateInstructionChange(instructionResId, getString(instructionResId));

        int gradientResId = isHighIntensity ? R.drawable.gradient_fast : R.drawable.gradient_slow;
        animateBackground(gradientResId);

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
            public void onFinish() {
                startNextInterval();
            }
        }.start();
    }

    private void animateInstructionChange(int instructionResId, String ttsText) {
        final Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        final Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                tvInstruction.setText(instructionResId);
                tvInstruction.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        tvInstruction.startAnimation(fadeOut);
        speak(ttsText);
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

        animateBackground(R.drawable.gradient_idle);
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

        updateTimerText(tvTimer, TOTAL_WORKOUT_TIME_MS);
        updateTimerText(tvIntervalTimer, INTERVAL_TIME_MS);
        tvStepCount.setText(R.string.initial_step_count);
        progressBarTotal.setProgress(100);
        progressBarInterval.setProgress(100);
        mainLayout.setBackgroundResource(R.drawable.gradient_idle);
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

            PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.2f, 1.0f);
            PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.2f, 1.0f);
            ObjectAnimator.ofPropertyValuesHolder(tvStepCount, pvhX, pvhY).setDuration(200).start();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


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
        if (isWorkoutRunning) {
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

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_history) {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}