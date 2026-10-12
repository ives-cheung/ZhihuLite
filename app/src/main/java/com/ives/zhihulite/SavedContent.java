package com.ives.zhihulite;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
@Entity(tableName="saved")
public class SavedContent {
 @PrimaryKey @NonNull public String key = "";
 @NonNull public String kind = "";
 @NonNull public String contentType = "";
 @NonNull public String contentId = "";
 @NonNull public String title = "";
 @NonNull public String author = "";
 @NonNull public String url = "";
 public long readTime;
}
