package com.codenzi.aralikliyuruyus;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, WorkoutService.WorkoutListener {

    private static final int PERMISSIONS_REQUEST_CODE = 101; // Tek bir istek kodu kullanacağız
    private static final long TOTAL_WORKOUT_TIME_MS = 30 * 60 * 1000;
    private static final long INTERVAL_TIME_MS = 3 * 60 * 1000;

    private TextView tvInstruction, tvTimer, tvIntervalTimer, tvStepCount;
    private Button btnStartStop, btnReset;
    private ProgressBar progressBarTotal, progressBarInterval;
    private View mainLayout;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    private WorkoutService workoutService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            WorkoutService.LocalBinder binder = (WorkoutService.LocalBinder) service;
            workoutService = binder.getService();
            isBound = true;
            workoutService.setListener(MainActivity.this);
            updateUIFromServiceState();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            workoutService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();

        setSupportActionBar(toolbar);
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        btnStartStop.setOnClickListener(v -> handleStartStopClick());
        btnReset.setOnClickListener(v -> handleResetClick());

        updateUIToInitialState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to WorkoutService
        Intent intent = new Intent(this, WorkoutService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            workoutService.setListener(null); // prevent memory leaks
            unbindService(connection);
            isBound = false;
        }
    }

    private void handleStartStopClick() {
        if (!isBound) return;

        if (workoutService.isWorkoutRunning) {
            // PAUSE
            Intent intent = new Intent(this, WorkoutService.class);
            intent.setAction(WorkoutService.ACTION_PAUSE_WORKOUT);
            startService(intent);
        } else if (workoutService.isWorkoutPaused) {
            // RESUME
            Intent intent = new Intent(this, WorkoutService.class);
            intent.setAction(WorkoutService.ACTION_RESUME_WORKOUT);
            startService(intent);
        } else {
            // START
            checkAndStartWorkout();
        }
    }

    private void handleResetClick() {
        if (isBound && (workoutService.isWorkoutPaused || !workoutService.isWorkoutRunning)) {
            Intent intent = new Intent(this, WorkoutService.class);
            intent.setAction(WorkoutService.ACTION_RESET_WORKOUT);
            startService(intent);
            updateUIToInitialState();
        }
    }

    private void checkAndStartWorkout() {
        // Gerekli izinlerin listesi oluşturuluyor
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        // Android 10 (Q) ve üzeri için ACTIVITY_RECOGNITION izni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }

        // Android 13 (TIRAMISU) ve üzeri için POST_NOTIFICATIONS izni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // İstenecek izin varsa, kullanıcıdan istenir
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        } else {
            // Tüm izinler zaten verilmiş, servis başlatılabilir
            startWorkoutService();
        }
    }


    private void startWorkoutService() {
        Intent intent = new Intent(this, WorkoutService.class);
        intent.setAction(WorkoutService.ACTION_START_WORKOUT);
        ContextCompat.startForegroundService(this, intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
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

    private void animateBackground(int newDrawableResId) {
        Drawable oldDrawable = mainLayout.getBackground();
        Drawable newDrawable = ContextCompat.getDrawable(this, newDrawableResId);
        if (oldDrawable != null && newDrawable != null) {
            TransitionDrawable transitionDrawable = new TransitionDrawable(new Drawable[]{oldDrawable, newDrawable});
            mainLayout.setBackground(transitionDrawable);
            transitionDrawable.startTransition(1000);
        } else if (newDrawable != null) {
            mainLayout.setBackground(newDrawable);
        }
    }

    private void animateInstructionChange(int instructionResId) {
        final Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        final Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) { }
            @Override public void onAnimationEnd(Animation animation) {
                tvInstruction.setText(instructionResId);
                tvInstruction.startAnimation(fadeIn);
            }
            @Override public void onAnimationRepeat(Animation animation) { }
        });
        tvInstruction.startAnimation(fadeOut);
    }

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

    private void updateUIFromServiceState() {
        if (!isBound || workoutService == null) return;

        onStateChange(workoutService.isWorkoutRunning, workoutService.isWorkoutPaused);
        onTimerUpdate(workoutService.getWorkoutTimeRemaining(), workoutService.getIntervalTimeRemaining());
        onStepUpdate(workoutService.getSessionStepCount());

        int instructionResId;
        int backgroundResId;
        if(workoutService.isWorkoutPaused){
            instructionResId = R.string.instruction_workout_paused;
            backgroundResId = R.drawable.gradient_paused;
        } else if (workoutService.isWorkoutRunning) {
            instructionResId = workoutService.isHighIntensity ? R.string.instruction_go_fast : R.string.instruction_go_slow;
            backgroundResId = workoutService.isHighIntensity ? R.drawable.gradient_fast : R.drawable.gradient_slow;
        } else {
            instructionResId = R.string.instruction_ready;
            backgroundResId = R.drawable.gradient_idle;
        }
        tvInstruction.setText(instructionResId);
        mainLayout.setBackgroundResource(backgroundResId);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            if (grantResults.length == 0) { // Kullanıcı hiçbir şey seçmeden dialog'u kapatırsa
                allGranted = false;
            } else {
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            }

            if (allGranted) {
                // Tüm izinler verildi, servisi başlat
                startWorkoutService();
            } else {
                // En az bir izin reddedildi
                Toast.makeText(this, "Antrenmanı başlatmak için gerekli izinler verilmedi.", Toast.LENGTH_LONG).show();
            }
        }
    }


    // --- Service Listener Callbacks ---

    @Override
    public void onTimerUpdate(long totalMillis, long intervalMillis) {
        runOnUiThread(() -> {
            updateTimerText(tvTimer, totalMillis);
            updateTimerText(tvIntervalTimer, intervalMillis);
            progressBarTotal.setProgress((int) ((totalMillis * 100) / TOTAL_WORKOUT_TIME_MS));
            progressBarInterval.setProgress((int) ((intervalMillis * 100) / INTERVAL_TIME_MS));
        });
    }

    @Override
    public void onStepUpdate(int steps) {
        runOnUiThread(() -> {
            tvStepCount.setText(String.valueOf(steps));
            PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.2f, 1.0f);
            PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.2f, 1.0f);
            ObjectAnimator.ofPropertyValuesHolder(tvStepCount, pvhX, pvhY).setDuration(200).start();
        });
    }

    @Override
    public void onInstructionUpdate(int instructionResId, int backgroundResId) {
        runOnUiThread(() -> {
            animateInstructionChange(instructionResId);
            animateBackground(backgroundResId);
        });
    }

    @Override
    public void onStateChange(boolean isRunning, boolean isPaused) {
        runOnUiThread(() -> {
            if (isPaused) {
                btnStartStop.setText(R.string.button_resume_workout);
                btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
                btnReset.setVisibility(View.VISIBLE);
            } else if (isRunning) {
                btnStartStop.setText(R.string.button_stop_workout);
                btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.stop_button_color));
                btnReset.setVisibility(View.INVISIBLE);
            } else {
                btnStartStop.setText(R.string.button_start_workout);
                btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
                btnReset.setVisibility(View.INVISIBLE);
            }
        });
    }

    @Override
    public void onWorkoutFinished() {
        runOnUiThread(()-> {
            Toast.makeText(this, "Antrenman kaydedildi!", Toast.LENGTH_SHORT).show();
            tvInstruction.setText(R.string.instruction_workout_finished);
            btnStartStop.setText(R.string.button_start_workout);
            btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
            btnReset.setVisibility(View.VISIBLE);
            animateBackground(R.drawable.gradient_idle);
        });
    }

    // --- Navigation ---

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