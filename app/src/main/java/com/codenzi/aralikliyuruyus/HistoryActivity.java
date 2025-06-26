package com.codenzi.aralikliyuruyus;

import android.os.Bundle;
import android.widget.CalendarView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private TextView tvHistoryDetails;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_activity_history);
        }

        calendarView = findViewById(R.id.calendarView);
        tvHistoryDetails = findViewById(R.id.tvHistoryDetails);
        db = AppDatabase.getDatabase(getApplicationContext());

        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, dayOfMonth);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String selectedDate = sdf.format(calendar.getTime());

                loadDataForDate(selectedDate);
            }
        });
    }

    private void loadDataForDate(String date) {
        Executors.newSingleThreadExecutor().execute(() -> {
            // Veritabanından o tarihe ait tüm seansları liste olarak çekiyoruz
            List<WorkoutSession> sessions = db.workoutDao().getSessionsByDate(date);

            runOnUiThread(() -> {
                if (sessions != null && !sessions.isEmpty()) {
                    // Birden fazla antrenman varsa, hepsini göstermek için bir metin oluşturuyoruz
                    StringBuilder detailsBuilder = new StringBuilder();
                    detailsBuilder.append("Antrenmanlar:\n\n");
                    int workoutNumber = sessions.size();
                    for (WorkoutSession session : sessions) {
                        detailsBuilder.append("Antrenman ").append(workoutNumber--).append(":\n");
                        detailsBuilder.append("  Adım: ").append(session.steps).append("\n");
                        detailsBuilder.append("  Süre: ").append(session.duration).append(" saniye\n\n");
                    }
                    tvHistoryDetails.setText(detailsBuilder.toString());
                } else {
                    tvHistoryDetails.setText("Bu tarihe ait antrenman kaydı bulunamadı.");
                }
            });
        });
    }
}