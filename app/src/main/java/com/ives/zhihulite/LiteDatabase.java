package com.ives.zhihulite;
import androidx.room.*;
@Database(entities={SavedContent.class},version=1,exportSchema=false)
public abstract class LiteDatabase extends RoomDatabase { public abstract SavedDao saved(); }
