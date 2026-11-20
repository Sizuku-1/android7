package cn.edu.sicnu.cs.txp.android7;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Audio.Media;

import java.util.ArrayList;
import java.util.List;

final class Songs {
    static final class Track {
        final Uri uri;
        final String title;
        final int duration;

        Track(Uri uri, String title, int duration) {
            this.uri = uri;
            this.title = title;
            this.duration = duration;
        }
    }

    private Songs() {}

    static List<Track> query(ContentResolver resolver) {
        Uri collection = Media.EXTERNAL_CONTENT_URI;
        String[] columns = {Media._ID, Media.TITLE, Media.DISPLAY_NAME, Media.DURATION};
        List<Track> tracks = new ArrayList<>();
        try (Cursor cursor = resolver.query(collection, columns, null, null,
                Media.TITLE + " COLLATE NOCASE ASC, " + Media._ID + " ASC")) {
            if (cursor == null) {
                return tracks;
            }
            while (cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(0));
                String title = cursor.getString(1);
                if (title == null || title.trim().isEmpty()) {
                    title = cursor.getString(2);
                }
                if (title == null || title.trim().isEmpty()) {
                    title = uri.getLastPathSegment();
                }
                tracks.add(new Track(uri, title, cursor.getInt(3)));
            }
        }
        return tracks;
    }
}
