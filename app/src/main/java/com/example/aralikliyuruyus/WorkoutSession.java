package com.example.aralikliyuruyus;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "workout_history")
public class WorkoutSession {

    // Her kaydı benzersiz yapan, otomatik artan birincil anahtar
    @PrimaryKey(autoGenerate = true)
    public long id;

    // Tarihe göre sorgulamayı hızlandırmak için index ekliyoruz
    @ColumnInfo(index = true)
    @NonNull
    public String date; // YYYY-MM-DD formatında tarih

    public int steps;

    public long duration; // Saniye cinsinden

    // Room bu constructor'ı veritabanına kayıt eklemek için kullanacak
    public WorkoutSession(@NonNull String date, int steps, long duration) {
        this.date = date;
        this.steps = steps;
        this.duration = duration;
    }
}