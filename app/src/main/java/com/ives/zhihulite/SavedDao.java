package com.ives.zhihulite;
import androidx.room.*;
import java.util.List;
@Dao public interface SavedDao {
 @Query("SELECT * FROM saved WHERE kind = :kind ORDER BY readTime DESC") List<SavedContent> list(String kind);
 @Insert(onConflict=OnConflictStrategy.REPLACE) void put(SavedContent value);
 @Query("DELETE FROM saved WHERE key = :key") void remove(String key);
 @Query("DELETE FROM saved WHERE kind = :kind") void clear(String kind);
 @Query("SELECT COUNT(*) FROM saved WHERE key = :key") int exists(String key);
}
