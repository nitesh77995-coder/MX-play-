package com.nplayer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private Uri videoUri;
    private PlayerView playerView;
    private boolean portraitOrientation;
    private int sourceWidth;
    private int sourceHeight;
    private TextView elapsedTime;
    private TextView durationTime;
    private SeekBar timeline;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private LinearLayout centerControls;
    private boolean controlsVisible = true;
    private boolean scrubbing;
    private final Handler controllerHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideControls = () -> setControlsVisible(false);
    private final Runnable updateTimeline = new Runnable() {
        @Override public void run() {
            updatePlaybackDisplay();
            if (controlsVisible && !scrubbing) controllerHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        videoUri = getIntent().getData();
        if (videoUri == null) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setControllerAutoShow(false);
        playerView.setShowSubtitleButton(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        View touchLayer = new View(this);
        touchLayer.setBackgroundColor(Color.TRANSPARENT);
        touchLayer.setOnClickListener(v -> toggleControls());
        root.addView(touchLayer, new FrameLayout.LayoutParams(-1, -1));

        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        topBar.setBackground(overlayBand(true));
        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra("title") == null
            ? "N Player" : getIntent().getStringExtra("title"));
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        title.setTextSize(13);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setMinWidth(0);
        topBar.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        LinearLayout toolbarActions = new LinearLayout(this);
        toolbarActions.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView actionScroll = new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.setFillViewport(false);
        actionScroll.addView(toolbarActions, new HorizontalScrollView.LayoutParams(-2, -2));
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels
            / getResources().getDisplayMetrics().density);
        int actionsWidth = Math.max(0, Math.min(330, screenWidthDp - 145));
        topBar.addView(actionScroll, new LinearLayout.LayoutParams(dp(actionsWidth), dp(44)));

        TextView fit = action("FIT");
        fit.setContentDescription("Video fit options");
        fit.setOnClickListener(v -> showFitOptions(fit));
        toolbarActions.addView(fit);
        TextView rotate = action("ROTATE");
        rotate.setContentDescription("Change playback orientation");
        rotate.setOnClickListener(v -> toggleOrientation(rotate));
        toolbarActions.addView(rotate);
        TextView tracks = action("AUDIO / CC");
        tracks.setContentDescription("Select audio or subtitle track");
        tracks.setOnClickListener(v -> showTracks());
        toolbarActions.addView(tracks);
        TextView upscale = capabilityAction("Upscale", "Show source-appropriate upscale options");
        upscale.setOnClickListener(v -> showUpscaleOptions());
        addCapabilityAction(toolbarActions, upscale);
        TextView fps = capabilityAction("FPS", "Show frame-rate options");
        fps.setOnClickListener(v -> showFpsOptions());
        addCapabilityAction(toolbarActions, fps);
        TextView ai = action("AI");
        ai.setTextColor(0xFFCDF280);
        ai.setContentDescription("AI enhancement unavailable");
        ai.setOnClickListener(v -> showEnhancementStatus());
        toolbarActions.addView(ai);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(topBar, topParams);

        centerControls = new LinearLayout(this);
        centerControls.setGravity(Gravity.CENTER);
        centerControls.setOrientation(LinearLayout.HORIZONTAL);
        centerControls.setPadding(dp(14), dp(8), dp(14), dp(8));
        centerControls.setBackground(roundedOverlay(0x66303831, 24));
        addTransportButton(centerControls, "|‹", "Previous video", this::playPrevious);
        addSkipButton(centerControls, "↶", "5", "Rewind 5 seconds", () -> seekBy(-5000));
        addTransportButton(centerControls, "▶", "Play or pause", this::togglePlayback);
        addSkipButton(centerControls, "↷", "15", "Forward 15 seconds", () -> seekBy(15000));
        addTransportButton(centerControls, "›|", "Next video", this::playNext);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(centerControls, centerParams);

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(15), dp(8), dp(13), dp(7));
        bottomBar.setBackground(overlayBand(false));
        timeline = new SeekBar(this);
        timeline.setMax(1000);
        timeline.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        timeline.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        timeline.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x88FFFFFF));
        timeline.setPadding(0, 0, 0, 0);
        bottomBar.addView(timeline, new LinearLayout.LayoutParams(-1, dp(26)));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    long duration = player.getDuration();
                    if (duration > 0) elapsedTime.setText(formatTime(duration * progress / seekBar.getMax()));
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                scrubbing = true;
                controllerHandler.removeCallbacks(updateTimeline);
                showControlsTemporarily();
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null && player.getDuration() > 0) {
                    player.seekTo(player.getDuration() * seekBar.getProgress() / seekBar.getMax());
                }
                scrubbing = false;
                updatePlaybackDisplay();
                scheduleTimelineUpdate();
            }
        });
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsedTime = action("0:00");
        elapsedTime.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        elapsedTime.setTextSize(12);
        durationTime = action("0:00");
        durationTime.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        durationTime.setTextSize(12);
        elapsedTime.setBackgroundColor(Color.TRANSPARENT);
        durationTime.setBackgroundColor(Color.TRANSPARENT);
        timeRow.addView(elapsedTime, new LinearLayout.LayoutParams(-2, dp(34)));
        timeRow.addView(durationTime, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView settings = action("⚙");
        settings.setTextSize(20);
        settings.setContentDescription("Playback settings");
        settings.setOnClickListener(v -> showPlaybackMenu(settings));
        timeRow.addView(settings, new LinearLayout.LayoutParams(dp(44), dp(38)));
        bottomBar.addView(timeRow);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottomBar, bottomParams);
        setContentView(root);

        trackSelector = new DefaultTrackSelector(this);
        player = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(videoUri));
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayIcon();
            }

            @Override public void onPlaybackStateChanged(int playbackState) {
                updatePlaybackDisplay();
            }

            @Override public void onVideoSizeChanged(VideoSize videoSize) {
                sourceWidth = videoSize.width;
                sourceHeight = videoSize.height;
            }

            @Override public void onPlayerError(PlaybackException error) {
                new AlertDialog.Builder(PlayerActivity.this)
                        .setTitle("Can't play this video")
                        .setMessage(error.getErrorCodeName() + ": " + error.getMessage())
                        .setPositiveButton("Close", (dialog, which) -> finish())
                        .show();
            }
        });
        long resume = getIntent().getLongExtra("resume", 0);
        if (resume > 0) player.seekTo(resume);
        player.prepare();
        player.play();
        updatePlayIcon();
        updatePlaybackDisplay();
        scheduleTimelineUpdate();
        scheduleControlsHide();
    }

    private GradientDrawable overlayBand(boolean top) {
        GradientDrawable drawable = new GradientDrawable(
                top ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0x88000000, 0x00000000});
        return drawable;
    }

    private GradientDrawable roundedOverlay(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), 0x44FFFFFF);
        return drawable;
    }

    private void addTransportButton(LinearLayout parent, String label, String description, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(label.length() > 3 ? 15 : 25);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setMinWidth(dp(label.length() > 3 ? 66 : 56));
        button.setMinHeight(dp(58));
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setBackground(selectableBackground());
        button.setFocusable(true);
        parent.addView(button, new LinearLayout.LayoutParams(-2, dp(58)));
        button.setOnClickListener(v -> {
            action.run();
            showControlsTemporarily();
        });
        if ("Play or pause".equals(description)) playPauseButton = button;
    }

    private void addSkipButton(LinearLayout parent, String icon, String seconds,
                               String description, Runnable action) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setMinimumWidth(dp(62));
        button.setMinimumHeight(dp(58));
        button.setBackground(selectableBackground());
        button.setFocusable(true);
        TextView glyph = new TextView(this);
        glyph.setText(icon);
        glyph.setTextColor(Color.WHITE);
        glyph.setTextSize(25);
        glyph.setGravity(Gravity.CENTER);
        button.addView(glyph, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView caption = new TextView(this);
        caption.setText(seconds);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(9);
        caption.setGravity(Gravity.CENTER);
        button.addView(caption, new LinearLayout.LayoutParams(-1, dp(13)));
        parent.addView(button, new LinearLayout.LayoutParams(-2, dp(58)));
        button.setOnClickListener(v -> {
            action.run();
            showControlsTemporarily();
        });
    }

    private TextView playPauseButton;

    private android.graphics.drawable.Drawable selectableBackground() {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            return getDrawable(value.resourceId);
        }
        return null;
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
        updatePlayIcon();
    }

    private void updatePlayIcon() {
        if (playPauseButton != null) {
            boolean playing = player != null && player.isPlaying();
            playPauseButton.setText(playing ? "Ⅱ" : "▶");
            playPauseButton.setTextSize(playing ? 28 : 25);
            playPauseButton.setContentDescription(playing ? "Pause video" : "Play video");
        }
    }

    private void seekBy(long offsetMs) {
        if (player == null) return;
        long duration = player.getDuration();
        long destination = Math.max(0, player.getCurrentPosition() + offsetMs);
        if (duration > 0) destination = Math.min(destination, duration);
        player.seekTo(destination);
        updatePlaybackDisplay();
    }

    private void playPrevious() {
        if (player != null) {
            player.seekTo(0);
            player.play();
            updatePlayIcon();
        }
    }

    private void playNext() {
        if (player == null) return;
        if (player.hasNextMediaItem()) player.seekToNextMediaItem();
        else Toast.makeText(this, "No next video in the playback queue", Toast.LENGTH_SHORT).show();
    }

    private void updatePlaybackDisplay() {
        if (player == null || timeline == null) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration < 0) duration = 0;
        if (position < 0) position = 0;
        elapsedTime.setText(formatTime(position));
        durationTime.setText(formatTime(duration));
        if (!scrubbing) {
            timeline.setProgress(duration > 0 ? (int) Math.min(1000, position * 1000 / duration) : 0);
        }
        updatePlayIcon();
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds / 1000);
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, seconds);
    }

    private void toggleControls() {
        setControlsVisible(!controlsVisible);
        if (controlsVisible) scheduleControlsHide();
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        int visibility = visible ? View.VISIBLE : View.GONE;
        topBar.setVisibility(visibility);
        centerControls.setVisibility(visibility);
        bottomBar.setVisibility(visibility);
        controllerHandler.removeCallbacks(hideControls);
        controllerHandler.removeCallbacks(updateTimeline);
        if (visible) scheduleTimelineUpdate();
    }

    private void showControlsTemporarily() {
        if (!controlsVisible) setControlsVisible(true);
        scheduleControlsHide();
    }

    private void scheduleControlsHide() {
        controllerHandler.removeCallbacks(hideControls);
        controllerHandler.postDelayed(hideControls, 4500);
    }

    private void scheduleTimelineUpdate() {
        controllerHandler.removeCallbacks(updateTimeline);
        controllerHandler.post(updateTimeline);
    }

    private void showPlaybackMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Playback speed");
        menu.getMenu().add("Audio / subtitles");
        menu.getMenu().add("AI enhancement status");
        menu.setOnMenuItemClickListener(item -> {
            if ("Audio / subtitles".contentEquals(item.getTitle())) showTracks();
            else if ("AI enhancement status".contentEquals(item.getTitle())) showEnhancementStatus();
            else showPlaybackSpeed();
            showControlsTemporarily();
            return true;
        });
        menu.show();
    }

    private void showPlaybackSpeed() {
        if (player == null) return;
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        new AlertDialog.Builder(this).setTitle("Playback speed").setItems(labels, (dialog, which) ->
                player.setPlaybackSpeed(speeds[which])).show();
    }

    private void showTracks() {
        if (player == null) return;
        ArrayList<TrackChoice> choices = new ArrayList<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            int type = group.getType();
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                String language = group.getMediaTrackGroup().getFormat(i).language;
                String label = (type == C.TRACK_TYPE_AUDIO ? "Audio" : "Subtitle") + " · "
                        + (language == null || language.isEmpty() ? "Track " + (i + 1) : language);
                choices.add(new TrackChoice(label, type, group, i));
            }
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, "No selectable audio or subtitle tracks found", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[choices.size() + 1];
        labels[0] = "Subtitles off";
        for (int i = 0; i < choices.size(); i++) labels[i + 1] = choices.get(i).label;
        new AlertDialog.Builder(this).setTitle("Audio & subtitles")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT).build());
                    } else {
                        TrackChoice choice = choices.get(which - 1);
                        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                                .setTrackTypeDisabled(choice.type, false)
                                .clearOverridesOfType(choice.type)
                                .setOverrideForType(new TrackSelectionOverride(choice.group.getMediaTrackGroup(), choice.index))
                                .build());
                    }
                }).show();
    }

    private void showEnhancementStatus() {
        new AlertDialog.Builder(this)
                .setTitle("AI enhancement unavailable")
                .setMessage("Playback is using the original video. This build has no verified AI enhancement model or inference-to-renderer pipeline. Upscaling and generated 48, 50, 60, or 120 FPS frames are unavailable. Standard playback remains available.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showUpscaleOptions() {
        if (player != null) {
            VideoSize current = player.getVideoSize();
            if (current.height > 0) {
                sourceWidth = current.width;
                sourceHeight = current.height;
            }
        }
        if (sourceHeight <= 0) {
            showEnhancementUnavailable("Source dimensions are not available yet. Normal playback is unaffected.");
            return;
        }

        ArrayList<String> outputs = new ArrayList<>();
        if (sourceHeight <= 480) {
            outputs.add("720p");
            outputs.add("1080p");
            outputs.add("4K");
        } else if (sourceHeight <= 720) {
            outputs.add("1080p");
            outputs.add("4K");
        } else {
            if (sourceHeight < 1080) outputs.add("1080p");
            if (sourceHeight < 2160) outputs.add("4K");
        }

        String source = sourceWidth > 0 ? sourceWidth + " x " + sourceHeight : sourceHeight + "p";
        if (outputs.isEmpty()) {
            showEnhancementUnavailable("Source: " + source + ". No higher preset output resolution is available.");
            return;
        }
        String[] labels = new String[outputs.size()];
        for (int i = 0; i < outputs.size(); i++) labels[i] = outputs.get(i) + "  ·  unavailable";
        new AlertDialog.Builder(this)
                .setTitle("Upscale · source " + source)
                .setMessage("These resolutions are listed for reference only. AI upscaling is not integrated; playback remains at the original resolution.")
                .setItems(labels, (dialog, which) -> showEnhancementUnavailable(
                        "AI upscaling is not supported in this build. Original playback continues."))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showFpsOptions() {
        String[] targets = {"48 FPS  ·  unavailable", "50 FPS  ·  unavailable",
                "60 FPS  ·  unavailable", "120 FPS  ·  unavailable"};
        new AlertDialog.Builder(this)
                .setTitle("FPS targets")
                .setMessage("Genuine frame interpolation is not integrated. Source frame rate and playback are unchanged.")
                .setItems(targets, (dialog, which) -> showEnhancementUnavailable(
                        "Frame interpolation is not supported in this build. Original playback continues."))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showEnhancementUnavailable(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Enhancement unavailable")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void addCapabilityAction(LinearLayout toolbar, TextView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(dp(3), 0, dp(3), 0);
        toolbar.addView(button, params);
    }

    private TextView capabilityAction(String label, String description) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(10);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(7), 0, dp(7), 0);
        view.setMinWidth(dp(45));
        view.setMinHeight(dp(34));
        view.setContentDescription(description);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x55303831);
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), 0x889AA7B5);
        view.setBackground(background);
        view.setFocusable(true);
        return view;
    }

    private void showFitOptions(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Fit to screen");
        menu.getMenu().add("Crop to fill");
        menu.setOnMenuItemClickListener(item -> {
            if ("Fit to screen".contentEquals(item.getTitle())) {
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            } else {
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            }
            return true;
        });
        menu.show();
    }

    private void toggleOrientation(TextView control) {
        portraitOrientation = !portraitOrientation;
        setRequestedOrientation(portraitOrientation
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        control.setText(portraitOrientation ? "LANDSCAPE" : "PORTRAIT");
        control.setContentDescription(portraitOrientation
                ? "Switch to landscape playback" : "Switch to portrait playback");
    }

    private TextView action(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(10);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(6), 0, dp(6), 0);
        view.setMinWidth(dp(34));
        view.setMinHeight(dp(36));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x77303831);
        background.setCornerRadius(dp(10));
        view.setBackground(background);
        view.setFocusable(true);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        controllerHandler.removeCallbacks(hideControls);
        controllerHandler.removeCallbacks(updateTimeline);
        if (player != null && videoUri != null) {
            long position = player.getPlaybackState() == Player.STATE_ENDED ? 0 : player.getCurrentPosition();
            getPreferences(MODE_PRIVATE).edit().putLong(MainActivity.resumeKey(videoUri), position).apply();
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        controllerHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private static final class TrackChoice {
        final String label;
        final int type;
        final Tracks.Group group;
        final int index;

        TrackChoice(String label, int type, Tracks.Group group, int index) {
            this.label = label;
            this.type = type;
            this.group = group;
            this.index = index;
        }
    }
}