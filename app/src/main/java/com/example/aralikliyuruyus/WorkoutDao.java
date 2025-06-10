package com.example.aralikliyuruyus;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface WorkoutDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSession(WorkoutSession session);

    // Artık bir tarih için bir liste döndürüyor. En son eklenen en üstte olacak şekilde sıraladık.
    @Query("SELECT * FROM workout_history WHERE date = :date ORDER BY id DESC")
    List<WorkoutSession> getSessionsByDate(String date);

    @Query("SELECT * FROM workout_history")
    List<WorkoutSession> getAllSessions();
}