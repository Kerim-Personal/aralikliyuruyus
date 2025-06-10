package com.example.aralikliyuruyus;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// Sürüm numarasını 2'ye yükselttik
@Database(entities = {WorkoutSession.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {

    public abstract WorkoutDao workoutDao();

    private static volatile AppDatabase INSTANCE;

    static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "workout_database")
                            // Şema değiştiğinde eski veritabanını silip yenisini oluşturur.
                            // Uygulama verilerinin kaybolmasına neden olur ama geliştirme aşamasında en kolay yoldur.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}