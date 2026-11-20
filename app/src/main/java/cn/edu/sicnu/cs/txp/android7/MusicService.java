package cn.edu.sicnu.cs.txp.android7;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MusicService extends Service {
    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 7;

    static final class Snapshot {
        final Songs.Track track;
        final int status;
        final int position;
        final int duration;
        final boolean canSeek;

        Snapshot(Songs.Track track, int status, int position, int duration, boolean canSeek) {
            this.track = track;
            this.status = status;
            this.position = position;
            this.duration = duration;
            this.canSeek = canSeek;
        }
    }

    final class PlaybackBinder extends Binder {
        void play(List<Songs.Track> library, int index) {
            if (index < 0 || index >= library.size()) {
                return;
            }
            queue = new ArrayList<>(library);
            open(index);
        }

        void toggle(List<Songs.Track> library) {
            if (status == R.string.playing) {
                pause();
            } else if (status == R.string.paused) {
                resume();
            } else if (status != R.string.loading) {
                if (currentTrack() != null) {
                    open(trackIndex);
                } else {
                    play(library, 0);
                }
            }
        }

        void skip(int direction) {
            if (!queue.isEmpty()) {
                open(Math.floorMod(trackIndex + direction, queue.size()));
            }
        }

        void stop() {
            endPlayback(R.string.stopped);
        }

        void seek(int position) {
            if (prepared) {
                player.seekTo(Math.max(0, Math.min(position, player.getDuration())),
                        MediaPlayer.SEEK_CLOSEST);
            }
        }

        Snapshot snapshot() {
            Songs.Track track = currentTrack();
            int duration = prepared ? player.getDuration() : track == null ? 0 : track.duration;
            return new Snapshot(track, status, prepared ? player.getCurrentPosition() : 0,
                    Math.max(0, duration), prepared);
        }
    }

    private final PlaybackBinder binder = new PlaybackBinder();
    private List<Songs.Track> queue = Collections.emptyList();
    private MediaPlayer player;
    private int trackIndex = -1;
    private int status = R.string.ready;
    private boolean prepared;
    private boolean foreground;
    private boolean bound;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private AudioAttributes attributes;

    @Override
    public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL_ID, getString(R.string.channel),
                        NotificationManager.IMPORTANCE_LOW));
        attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audio = getSystemService(AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pause();
                    }
                }).build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        bound = true;
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        bound = false;
        if (status == R.string.paused) {
            endPlayback(R.string.stopped);
        }
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        bound = true;
        super.onRebind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private Songs.Track currentTrack() {
        return trackIndex < 0 ? null : queue.get(trackIndex);
    }

    private void open(int index) {
        releasePlayer();
        trackIndex = index;
        status = R.string.loading;
        try {
            showNotification();
            Uri uri = currentTrack().uri;
            player = new MediaPlayer();
            player.setAudioAttributes(attributes);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnPreparedListener(source -> {
                if (player == source) {
                    prepared = true;
                    resume();
                }
            });
            player.setOnCompletionListener(source -> {
                if (player == source) {
                    binder.skip(1);
                }
            });
            player.setOnErrorListener((source, what, extra) -> {
                if (player == source) {
                    Log.w("MusicService", "MediaPlayer error " + what + "/" + extra);
                    endPlayback(R.string.failed);
                }
                return true;
            });
            player.setDataSource(this, uri);
            player.prepareAsync();
        } catch (IOException | RuntimeException exception) {
            Log.w("MusicService", "Could not open audio", exception);
            endPlayback(R.string.failed);
        }
    }

    private void resume() {
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            endPlayback(R.string.failed);
            return;
        }
        player.start();
        status = R.string.playing;
        showNotification();
    }

    private void pause() {
        if (status != R.string.playing) {
            return;
        }
        player.pause();
        audio.abandonAudioFocusRequest(focus);
        status = R.string.paused;
        if (!bound) {
            endPlayback(R.string.stopped);
            return;
        }
        showNotification();
    }

    private void showNotification() {
        Intent openApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(currentTrack().title)
                .setContentText(getString(status))
                .setContentIntent(content)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
        if (foreground) {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
        } else {
            startService(new Intent(this, MusicService.class));
            startForeground(NOTIFICATION_ID, notification);
            foreground = true;
        }
    }

    private void endPlayback(int finalStatus) {
        releasePlayer();
        audio.abandonAudioFocusRequest(focus);
        status = finalStatus;
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
        stopSelf();
    }

    private void releasePlayer() {
        prepared = false;
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        endPlayback(R.string.stopped);
        super.onDestroy();
    }
}
