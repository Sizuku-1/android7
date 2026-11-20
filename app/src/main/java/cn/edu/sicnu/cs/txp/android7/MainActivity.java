package cn.edu.sicnu.cs.txp.android7;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 7;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Runnable refreshProgress = this::render;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            playback = (MusicService.PlaybackBinder) service;
            render();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playback = null;
            render();
        }
    };

    private MusicService.PlaybackBinder playback;
    private boolean bound;
    private boolean dragging;
    private int queryGeneration;
    private ScheduledFuture<?> progressTask;
    private List<Songs.Track> library = Collections.emptyList();
    private ArrayAdapter<Songs.Track> adapter;
    private ListView tracks;
    private TextView title, status, time, count, empty;
    private Button previous, playPause, stop, next;
    private SeekBar progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int margin = getResources().getDimensionPixelSize(R.dimen.screen_margin);
                view.setPadding(bars.left + margin, bars.top, bars.right + margin, bars.bottom);
            }
            return insets;
        });
        title = findViewById(R.id.title);
        status = findViewById(R.id.status);
        time = findViewById(R.id.time);
        count = findViewById(R.id.count);
        empty = findViewById(R.id.empty);
        previous = findViewById(R.id.previous);
        playPause = findViewById(R.id.play_pause);
        stop = findViewById(R.id.stop);
        next = findViewById(R.id.next);
        progress = findViewById(R.id.progress);
        tracks = findViewById(R.id.tracks);
        tracks.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        tracks.setEmptyView(empty);
        adapter = new ArrayAdapter<Songs.Track>(this, android.R.layout.simple_list_item_activated_2,
                android.R.id.text1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                Songs.Track track = getItem(position);
                row.<TextView>findViewById(android.R.id.text1).setText(track.title);
                row.<TextView>findViewById(android.R.id.text2).setText(track.duration > 0 ? formatTime(track.duration) : "--:--");
                return row;
            }
        };
        tracks.setAdapter(adapter);
        tracks.setOnItemClickListener((parent, view, position, id) -> {
            if (playback != null) {
                playback.play(library, position);
                render();
            }
        });
        findViewById(R.id.refresh).setOnClickListener(view -> {
            if (!requestMissingPermissions()) {
                loadLibrary();
            }
        });
        previous.setOnClickListener(view -> skip(-1));
        next.setOnClickListener(view -> skip(1));
        playPause.setOnClickListener(view -> {
            if (playback != null) {
                playback.toggle(library);
                render();
            }
        });
        stop.setOnClickListener(view -> {
            if (playback != null) {
                dragging = false;
                playback.stop();
                render();
            }
        });
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) {
                    showTime(value, bar.getMax());
                    if (!dragging && playback != null) {
                        playback.seek(value);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                if (playback != null) {
                    playback.seek(bar.getProgress());
                }
            }
        });
        render();
        if (state == null) {
            requestMissingPermissions();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
        loadLibrary();
        progressTask = worker.scheduleWithFixedDelay(
                () -> main.post(refreshProgress), 0, 250, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void onStop() {
        progressTask.cancel(false);
        main.removeCallbacks(refreshProgress);
        queryGeneration++;
        dragging = false;
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        playback = null;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(audioPermission()) != PackageManager.PERMISSION_GRANTED) {
            missing.add(audioPermission());
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (missing.isEmpty()) {
            return false;
        }
        requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_PERMISSIONS) {
            loadLibrary();
        }
    }

    private void loadLibrary() {
        int generation = ++queryGeneration;
        if (checkSelfPermission(audioPermission()) != PackageManager.PERMISSION_GRANTED) {
            applyLibrary(Collections.emptyList(), R.string.permission_required);
            return;
        }
        if (library.isEmpty()) {
            empty.setText(R.string.loading);
        }
        worker.execute(() -> {
            try {
                List<Songs.Track> found = Songs.query(getContentResolver());
                main.post(() -> {
                    if (generation == queryGeneration) {
                        applyLibrary(found, R.string.empty);
                    }
                });
            } catch (RuntimeException exception) {
                main.post(() -> {
                    if (generation == queryGeneration) {
                        applyLibrary(Collections.emptyList(), R.string.query_failed);
                    }
                });
            }
        });
    }

    private void applyLibrary(List<Songs.Track> found, int message) {
        library = found;
        empty.setText(message);
        adapter.setNotifyOnChange(false);
        adapter.clear();
        adapter.addAll(found);
        adapter.notifyDataSetChanged();
        count.setText(getString(R.string.count, found.size()));
        render();
    }

    private void skip(int direction) {
        if (playback != null) {
            playback.skip(direction);
            render();
        }
    }

    private void render() {
        MusicService.Snapshot state = playback == null ? null : playback.snapshot();
        Songs.Track track = state == null ? null : state.track;
        int playbackStatus = state == null ? R.string.ready : state.status;
        boolean active = playbackStatus == R.string.playing || playbackStatus == R.string.paused;
        boolean loading = playbackStatus == R.string.loading;

        setText(title, track == null ? getString(R.string.no_track) : track.title);
        setText(status, getString(playbackStatus));
        setText(playPause, getString(playbackStatus == R.string.playing ? R.string.pause : R.string.play));
        playPause.setEnabled(playback != null && !loading && (track != null || !library.isEmpty()));
        previous.setEnabled(track != null);
        next.setEnabled(track != null);
        stop.setEnabled(active || loading);
        progress.setEnabled(state != null && state.canSeek);
        if (!dragging) {
            int duration = state == null ? 0 : state.duration;
            int position = state == null ? 0 : state.position;
            progress.setMax(Math.max(1, duration));
            progress.setProgress(position);
            showTime(position, duration);
        }
        int checked = tracks.getCheckedItemPosition();
        if (checked != ListView.INVALID_POSITION
                && (checked >= library.size() || track == null || !library.get(checked).uri.equals(track.uri))) {
            tracks.setItemChecked(checked, false);
            checked = ListView.INVALID_POSITION;
        }
        if (track != null && checked == ListView.INVALID_POSITION) {
            for (int i = 0; i < library.size(); i++) {
                if (library.get(i).uri.equals(track.uri)) {
                    tracks.setItemChecked(i, true);
                    break;
                }
            }
        }
    }

    private void showTime(int position, int duration) {
        setText(time, formatTime(position) + " / " + formatTime(duration));
    }

    private String formatTime(int milliseconds) {
        int seconds = Math.max(0, milliseconds) / 1000;
        return getString(R.string.time, seconds / 60, seconds % 60);
    }

    private void setText(TextView view, CharSequence value) {
        if (!android.text.TextUtils.equals(view.getText(), value)) {
            view.setText(value);
        }
    }
}
