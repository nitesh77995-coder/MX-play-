package com.nplayer.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_MEDIA = 41;
    private static final int REQUEST_FOLDER = 42;
    private static final int BG = Color.rgb(16, 21, 29);
    private static final int SURFACE = Color.rgb(26, 34, 45);
    private static final int SURFACE_RAISED = Color.rgb(35, 45, 58);
    private static final int BORDER = Color.rgb(42, 54, 68);
    private static final int MUTED = Color.rgb(155, 168, 184);
    private static final int ACCENT = Color.rgb(91, 169, 255);

    private final ArrayList<MediaEntry> videos = new ArrayList<>();
    private final ArrayList<DocumentFile> folderStack = new ArrayList<>();
    private final HashMap<String, MediaEntry> knownEntries = new HashMap<>();
    private LinearLayout list;
    private EditText search;
    private TextView sortButton;
    private TextView sectionTitle;
    private TextView sectionCount;
    private TextView toolbarTitle;
    private TextView layoutButton;
    private TextView floatingPlay;
    private String activeTab = "Folders";
    private String query = "";
    private String selectedMediaPath;
    private boolean gridMode;
    private int sortMode;
    private final String[] sortNames = {"Newest", "Name", "Longest"};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildScreen();
        refreshVideos();
    }

    private void buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(17), dp(9), dp(13), dp(9));
        header.setBackgroundColor(SURFACE);
        ImageView mark = new ImageView(this);
        mark.setImageResource(com.nplayer.app.R.drawable.ic_n_mark);
        mark.setContentDescription("N Player logo");
        header.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));
        toolbarTitle = text("Videos", 20, Color.WHITE, true);
        toolbarTitle.setPadding(dp(10), 0, 0, 0);
        toolbarTitle.setSingleLine(true);
        toolbarTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(toolbarTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView searchAction = toolbarAction("⌕", "Search videos");
        searchAction.setOnClickListener(v -> {
            search.requestFocus();
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        header.addView(searchAction);
        layoutButton = toolbarAction("▦", "Switch list or grid layout");
        layoutButton.setOnClickListener(v -> {
            gridMode = !gridMode;
            layoutButton.setText(gridMode ? "☷" : "▦");
            layoutButton.setContentDescription(gridMode ? "Switch to list layout" : "Switch to grid layout");
            renderItems();
        });
        header.addView(layoutButton);
        TextView settingsAction = toolbarAction("⚙", "Library options");
        settingsAction.setOnClickListener(this::showLibraryMenu);
        header.addView(settingsAction);
        page.addView(header);

        HorizontalScrollView quickActions = new HorizontalScrollView(this);
        quickActions.setHorizontalScrollBarEnabled(false);
        quickActions.setClipToPadding(false);
        quickActions.setPadding(dp(13), dp(2), dp(13), dp(10));
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        addQuickChip(chips, "♫  Music", v -> showUnavailable("Music library",
            "Audio browsing is not enabled. N Player only requests video access."));
        addQuickChip(chips, "◉  Privacy", v -> showUnavailable("Private folder",
            "Private-folder protection is not implemented in this build."));
        addQuickChip(chips, "↗  Share", v -> showSharePicker());
        addQuickChip(chips, "⌁  Video tools", v -> showVideoTools(v));
        quickActions.addView(chips);
        page.addView(quickActions);

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(15), 0, dp(13), 0);
        searchBox.setBackground(background(SURFACE, 14));
        TextView searchIcon = text("⌕", 23, MUTED, false);
        searchBox.addView(searchIcon, new LinearLayout.LayoutParams(dp(28), -2));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(MUTED);
        search.setHint("Search your library");
        search.setTextSize(14);
        search.setPadding(0, 0, dp(8), 0);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.setMargins(dp(15), 0, dp(15), dp(8));
        page.addView(searchBox, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                renderItems();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout section = new LinearLayout(this);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setPadding(dp(17), dp(1), dp(15), dp(8));
        sectionTitle = text("Folders", 18, Color.WHITE, true);
        sectionTitle.setSingleLine(true);
        sectionTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1));
        sortButton = text("Newest  ▾", 12, MUTED, true);
        sortButton.setGravity(Gravity.CENTER);
        sortButton.setContentDescription("Sort videos, currently " + sortNames[sortMode]);
        section.addView(sortButton, new LinearLayout.LayoutParams(-2, dp(36)));
        sortButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, sortButton);
            for (int i = 0; i < sortNames.length; i++) {
                menu.getMenu().add(0, i, i, sortNames[i]);
            }
            menu.setOnMenuItemClickListener(item -> {
                sortMode = item.getItemId();
                updateSortLabel();
                renderItems();
                return true;
            });
            menu.show();
        });
        sectionCount = text("", 12, MUTED, false);
        sectionCount.setPadding(dp(7), 0, 0, 0);
        section.addView(sectionCount);
        page.addView(section);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(15), dp(3), dp(15), dp(100));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout screen = new FrameLayout(this);
        screen.addView(page, new FrameLayout.LayoutParams(-1, -1));
        floatingPlay = text("▶", 21, Color.WHITE, true);
        floatingPlay.setGravity(Gravity.CENTER);
        floatingPlay.setContentDescription("Play a video");
        floatingPlay.setBackground(background(ACCENT, 28));
        floatingPlay.setElevation(dp(8));
        floatingPlay.setOnClickListener(v -> playFirstAvailable());
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.END);
        playParams.setMargins(0, 0, dp(20), dp(22));
        screen.addView(floatingPlay, playParams);
        setContentView(screen);
        updateSortLabel();
        updateToolbar();
    }

    private void updateSortLabel() {
        if (sortButton != null) {
            sortButton.setText(sortNames[sortMode] + "  ▾");
            sortButton.setContentDescription("Sort videos, currently " + sortNames[sortMode]);
        }
    }

    private void updateToolbar() {
        if (toolbarTitle != null) {
            String title = activeTab;
            if ("Folders".equals(activeTab)) {
                if (!folderStack.isEmpty() && folderStack.get(folderStack.size() - 1) != null) {
                    title = folderStack.get(folderStack.size() - 1).getName();
                } else if (selectedMediaPath != null) {
                    title = folderName(selectedMediaPath);
                }
            }
            toolbarTitle.setText(title == null ? "Folders" : title);
        }
        if (floatingPlay != null) floatingPlay.setVisibility("Folders".equals(activeTab) ? View.VISIBLE : View.GONE);
    }

    private TextView toolbarAction(String glyph, String description) {
        TextView action = text(glyph, 22, Color.WHITE, false);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription(description);
        action.setBackground(selectableForeground());
        action.setFocusable(true);
        action.setMinWidth(dp(42));
        action.setMinHeight(dp(44));
        return action;
    }

    private void addQuickChip(LinearLayout parent, String label, View.OnClickListener listener) {
        TextView chip = text(label, 12, Color.WHITE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(13), 0, dp(13), 0);
        chip.setBackground(background(SURFACE_RAISED, 18));
        chip.setOnClickListener(listener);
        chip.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
        params.setMargins(0, 0, dp(8), 0);
        parent.addView(chip, params);
    }

    private void showUnavailable(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private void showLibraryMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Rescan videos");
        menu.getMenu().add("Choose folder");
        menu.getMenu().add(gridMode ? "List layout" : "Grid layout");
        menu.setOnMenuItemClickListener(item -> {
            if ("Rescan videos".contentEquals(item.getTitle())) refreshVideos();
            else if ("Choose folder".contentEquals(item.getTitle())) openFolderPicker();
            else {
                gridMode = !gridMode;
                layoutButton.setText(gridMode ? "☷" : "▦");
                renderItems();
            }
            return true;
        });
        menu.show();
    }

    private void showVideoTools(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Rescan library");
        menu.getMenu().add("Sort videos");
        menu.getMenu().add("Choose folder");
        menu.setOnMenuItemClickListener(item -> {
            if ("Rescan library".contentEquals(item.getTitle())) refreshVideos();
            else if ("Choose folder".contentEquals(item.getTitle())) openFolderPicker();
            else if (sortButton != null) sortButton.performClick();
            return true;
        });
        menu.show();
    }

    private void showSharePicker() {
        if (videos.isEmpty()) {
            Toast.makeText(this, "No scanned videos to share", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[videos.size()];
        for (int i = 0; i < videos.size(); i++) names[i] = videos.get(i).title;
        new AlertDialog.Builder(this).setTitle("Share a video")
                .setItems(names, (dialog, which) -> {
                    MediaEntry entry = videos.get(which);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("video/*");
                    share.putExtra(Intent.EXTRA_STREAM, entry.uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "Share video"));
                }).show();
    }

    private void playFirstAvailable() {
        if ("Folders".equals(activeTab) && selectedMediaPath != null) {
            for (MediaEntry entry : videos) {
                String path = entry.relativePath == null ? "" : entry.relativePath;
                if (path.equals(selectedMediaPath)) {
                    play(entry);
                    return;
                }
            }
        }
        if ("Folders".equals(activeTab) && !folderStack.isEmpty()) {
            MediaEntry entry = findFirstVideo(folderStack.get(folderStack.size() - 1), 0);
            if (entry != null) {
                play(entry);
                return;
            }
        }
        if (!videos.isEmpty()) {
            play(videos.get(0));
            return;
        }
        Toast.makeText(this, "Choose a video to play", Toast.LENGTH_SHORT).show();
    }

    private MediaEntry findFirstVideo(DocumentFile directory, int depth) {
        if (directory == null || depth > 3) return null;
        DocumentFile[] children = directory.listFiles();
        if (children == null) return null;
        for (DocumentFile child : children) {
            if (child.isFile() && child.getType() != null && child.getType().startsWith("video/")) {
                String title = child.getName() == null ? "Video" : child.getName();
                return new MediaEntry(child.getUri(), title, 0, 0, 0, 0, null, -1, false);
            }
        }
        for (DocumentFile child : children) {
            if (child.isDirectory()) {
                MediaEntry found = findFirstVideo(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    public void onBackPressed() {
        if (folderStack.size() > 1) {
            folderStack.remove(folderStack.size() - 1);
        } else if (!folderStack.isEmpty()) {
            folderStack.clear();
        } else if (selectedMediaPath != null) {
            selectedMediaPath = null;
        } else {
            super.onBackPressed();
            return;
        }
        renderItems();
    }

    private void refreshVideos() {
        if (!hasMediaPermission()) {
            requestMediaPermission();
            return;
        }
        videos.clear();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        ArrayList<String> projection = new ArrayList<>();
        projection.add(MediaStore.Video.Media._ID);
        projection.add(MediaStore.Video.Media.DISPLAY_NAME);
        projection.add(MediaStore.Video.Media.DURATION);
        projection.add(MediaStore.Video.Media.WIDTH);
        projection.add(MediaStore.Video.Media.HEIGHT);
        projection.add(MediaStore.Video.Media.DATE_ADDED);
        if (Build.VERSION.SDK_INT >= 29) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH);
            projection.add(MediaStore.MediaColumns.SIZE);
        }
        try (Cursor cursor = getContentResolver().query(collection, projection.toArray(new String[0]), null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
                int heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                int pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                int sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                while (cursor.moveToNext()) {
                    Uri uri = Uri.withAppendedPath(collection, cursor.getString(idCol));
                    MediaEntry entry = new MediaEntry(uri, cursor.getString(nameCol),
                            cursor.getLong(durationCol), cursor.getInt(widthCol), cursor.getInt(heightCol),
                            cursor.getLong(dateCol), pathCol >= 0 ? cursor.getString(pathCol) : null,
                            sizeCol >= 0 ? cursor.getLong(sizeCol) : -1, false);
                    videos.add(entry);
                    knownEntries.put(uri.toString(), entry);
                }
            }
        } catch (SecurityException exception) {
            requestMediaPermission();
        }
        renderItems();
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}, REQUEST_MEDIA);
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_MEDIA);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_MEDIA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_MEDIA) {
            if (hasMediaPermission()) refreshVideos();
            else Toast.makeText(this, "Allow video access to scan your library", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderItems() {
        if (list == null) return;
        list.removeAllViews();
        updateToolbar();
        if ("Folders".equals(activeTab) && !folderStack.isEmpty()) {
            String folderTitle = folderStack.get(folderStack.size() - 1).getName();
            sectionTitle.setText(folderTitle == null ? "Selected folder" : folderTitle);
            sectionCount.setText("BROWSE");
            renderFolderContents();
            return;
        }

        if ("Folders".equals(activeTab)) {
            updateToolbar();
            if (selectedMediaPath != null) renderMediaFolder();
            else renderMediaFolders();
            return;
        }

        sectionTitle.setText("Recent".equals(activeTab) ? "Recently played" :
                "All videos");

        ArrayList<MediaEntry> shown = new ArrayList<>();
        if ("Recent".equals(activeTab)) {
            JSONArray recent = recentUris();
            for (int i = 0; i < recent.length(); i++) {
                String uri = recent.optString(i);
                MediaEntry match = entryForUri(uri);
                if (match != null) shown.add(match);
            }
        } else {
            shown.addAll(videos);
        }
        shown.removeIf(item -> !item.title.toLowerCase(Locale.ROOT).contains(query));
        if (sortMode == 1) shown.sort(Comparator.comparing(item -> item.title.toLowerCase(Locale.ROOT)));
        else if (sortMode == 2) shown.sort((a, b) -> Long.compare(b.durationMs, a.durationMs));
        else shown.sort((a, b) -> Long.compare(b.dateAdded, a.dateAdded));

        int total = "Recent".equals(activeTab) ? recentUris().length() : videos.size();
        sectionCount.setText(total == 0 ? "" : total + (total == 1 ? " VIDEO" : " VIDEOS"));

        if (shown.isEmpty()) {
            String message = "Folders".equals(activeTab) ? "No folder selected" :
                    !hasMediaPermission() ? "Allow video access to find local videos" : "No videos here yet";
            addEmptyState(message, "Browse a folder or add videos to your device");
            return;
        }
        addVideoItems(shown);
    }

    private void renderMediaFolders() {
        sectionTitle.setText("Folders");
        LinkedHashMap<String, FolderGroup> groups = new LinkedHashMap<>();
        for (MediaEntry entry : videos) {
            String path = entry.relativePath == null ? "" : entry.relativePath;
            FolderGroup group = groups.get(path);
            if (group == null) {
                String name = path.isEmpty() ? "Other videos" : folderName(path);
                group = new FolderGroup(path, name);
                groups.put(path, group);
            }
            group.videoCount++;
            if (entry.sizeBytes >= 0) group.sizeBytes += entry.sizeBytes;
            else group.sizeKnown = false;
            if (entry.title.toLowerCase(Locale.ROOT).contains(query)) group.hasSearchMatch = true;
        }
        ArrayList<FolderGroup> shown = new ArrayList<>(groups.values());
        shown.removeIf(group -> !group.name.toLowerCase(Locale.ROOT).contains(query)
                && !group.hasSearchMatch);
        shown.sort(Comparator.comparing((FolderGroup group) -> group.name.toLowerCase(Locale.ROOT)));
        sectionCount.setText(shown.size() + (shown.size() == 1 ? " FOLDER" : " FOLDERS"));
        if (gridMode) {
            for (int i = 0; i < shown.size(); i += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.setMargins(0, 0, 0, dp(9));
                list.addView(row, rowParams);
                addFolderGridTile(row, shown.get(i));
                if (i + 1 < shown.size()) addFolderGridTile(row, shown.get(i + 1));
                else row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1));
            }
        } else {
            for (FolderGroup group : shown) addFolderSummaryRow(group);
        }

        String saved = getPreferences(MODE_PRIVATE).getString("folder", null);
        if (saved != null) {
            DocumentFile selected = DocumentFile.fromTreeUri(this, Uri.parse(saved));
            if (selected != null && selected.exists()) addSavedFolderRow(selected);
        }
        if (shown.isEmpty() && saved == null) {
            addEmptyState("No video folders found", "Allow video access or choose a folder to browse");
        }
    }

    private void renderMediaFolder() {
        String name = folderName(selectedMediaPath);
        sectionTitle.setText(name);
        TextView back = text("‹   All folders", 13, ACCENT, true);
        back.setPadding(dp(4), dp(5), 0, dp(11));
        list.addView(back);
        back.setOnClickListener(v -> {
            selectedMediaPath = null;
            renderItems();
        });

        ArrayList<MediaEntry> shown = new ArrayList<>();
        for (MediaEntry entry : videos) {
            String path = entry.relativePath == null ? "" : entry.relativePath;
            if (path.equals(selectedMediaPath)
                    && entry.title.toLowerCase(Locale.ROOT).contains(query)) shown.add(entry);
        }
        sortEntries(shown);
        sectionCount.setText(shown.size() + (shown.size() == 1 ? " VIDEO" : " VIDEOS"));
        if (shown.isEmpty()) {
            addEmptyState("No matching videos", "Try another search or return to all folders");
            return;
        }
        addVideoItems(shown);
    }

    private void addVideoItems(ArrayList<MediaEntry> entries) {
        if (!gridMode) {
            for (MediaEntry entry : entries) addMediaRow(entry);
            return;
        }
        for (int i = 0; i < entries.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, 0, 0, dp(10));
            list.addView(row, rowParams);
            addMediaGridTile(row, entries.get(i));
            if (i + 1 < entries.size()) addMediaGridTile(row, entries.get(i + 1));
            else row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1));
        }
    }

    private void sortEntries(ArrayList<MediaEntry> entries) {
        if (sortMode == 1) entries.sort(Comparator.comparing(entry -> entry.title.toLowerCase(Locale.ROOT)));
        else if (sortMode == 2) entries.sort((a, b) -> Long.compare(b.durationMs, a.durationMs));
        else entries.sort((a, b) -> Long.compare(b.dateAdded, a.dateAdded));
    }

    private String folderName(String path) {
        if (path == null || path.isEmpty()) return "Other videos";
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "Size unavailable";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void addEmptyState(String title, String subtitle) {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(dp(22), dp(33), dp(22), dp(29));
        empty.setBackground(background(SURFACE, 16));
        TextView mark = text("N", 35, ACCENT, true);
        mark.setGravity(Gravity.CENTER);
        empty.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView heading = text(title, 16, Color.WHITE, true);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(12), 0, dp(5));
        empty.addView(heading);
        TextView detail = text(subtitle, 13, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        empty.addView(detail);
        list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addMediaRow(MediaEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(9), dp(5), dp(9));
        row.setBackground(background(SURFACE, 15, BORDER));
        row.setForeground(selectableForeground());
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(94));
        rowParams.setMargins(0, 0, 0, dp(9));
        list.addView(row, rowParams);

        FrameLayout poster = new FrameLayout(this);
        poster.setBackground(background(SURFACE_RAISED, 10));
        poster.setClipToOutline(true);
        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setContentDescription("Thumbnail for " + entry.title);
        poster.addView(thumbnail, new FrameLayout.LayoutParams(-1, -1));
        TextView placeholder = text("N", 23, ACCENT, true);
        placeholder.setGravity(Gravity.CENTER);
        poster.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        TextView duration = text(formatDuration(entry.durationMs), 10, Color.WHITE, true);
        duration.setGravity(Gravity.CENTER);
        duration.setPadding(dp(5), dp(2), dp(5), dp(2));
        duration.setBackground(background(0xD9000000, 5));
        FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        durationParams.setMargins(0, 0, dp(5), dp(5));
        if (entry.durationMs > 0) poster.addView(duration, durationParams);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                thumbnail.setImageBitmap(getContentResolver().loadThumbnail(entry.uri, new android.util.Size(dp(108), dp(68)), null));
                placeholder.setVisibility(View.GONE);
            }
        } catch (Exception ignored) { }
        row.addView(poster, new LinearLayout.LayoutParams(dp(116), dp(76)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        details.setPadding(dp(12), 0, dp(4), 0);
        TextView name = text(entry.title, 14, Color.WHITE, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String resolution = entry.width > 0 && entry.height > 0 ? entry.resolutionLabel() : "";
        TextView meta = text(resolution.isEmpty() ? "VIDEO" : resolution, 11,
                resolution.isEmpty() ? MUTED : ACCENT, true);
        meta.setPadding(0, dp(6), 0, 0);
        details.addView(name);
        details.addView(meta);
        row.addView(details, new LinearLayout.LayoutParams(0, -1, 1));
        TextView overflow = text("⋮", 22, MUTED, true);
        overflow.setGravity(Gravity.CENTER);
        overflow.setContentDescription("More options for " + entry.title);
        row.addView(overflow, new LinearLayout.LayoutParams(dp(36), -1));
        overflow.setOnClickListener(v -> showVideoMenu(entry, overflow));
        row.setOnClickListener(v -> play(entry));
    }

    private void showVideoMenu(MediaEntry entry, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Play");
        menu.getMenu().add("Video details");
        menu.setOnMenuItemClickListener(item -> {
            if ("Play".contentEquals(item.getTitle())) {
                play(entry);
            } else {
                String resolution = entry.width > 0 && entry.height > 0
                        ? entry.resolutionLabel() : "Unavailable";
                String duration = entry.durationMs > 0 ? formatDuration(entry.durationMs) : "Unavailable";
                new AlertDialog.Builder(this)
                        .setTitle(entry.title)
                        .setMessage("Duration     " + duration + "\nResolution  " + resolution)
                        .setPositiveButton("Close", null)
                        .show();
            }
            return true;
        });
        menu.show();
    }

    private void addFolderSummaryRow(FolderGroup group) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(background(SURFACE, 16, BORDER));
        row.setForeground(selectableForeground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(86));
        params.setMargins(0, 0, 0, dp(8));
        list.addView(row, params);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(background(0x1EAAB5C2, 13));
        ImageView icon = new ImageView(this);
        icon.setImageResource(com.nplayer.app.R.drawable.ic_folder);
        icon.setContentDescription("Folder");
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        iconBox.addView(icon, iconParams);
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(60), dp(60)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(13), 0, dp(5), 0);
        TextView name = text(group.name, 15, Color.WHITE, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name);
        String size = group.sizeKnown ? formatBytes(group.sizeBytes) : "Size unavailable";
        TextView info = text(group.videoCount + (group.videoCount == 1 ? " video  ·  " : " videos  ·  ") + size,
                11, MUTED, false);
        info.setPadding(0, dp(5), 0, 0);
        labels.addView(info);
        row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));
        TextView arrow = text("›", 26, MUTED, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), -1));
        row.setOnClickListener(v -> {
            selectedMediaPath = group.path;
            renderItems();
        });
    }

    private void addFolderGridTile(LinearLayout parent, FolderGroup group) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(10), dp(13), dp(10), dp(12));
        tile.setBackground(background(SURFACE, 15, BORDER));
        tile.setForeground(selectableForeground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(158), 1);
        params.setMargins(0, 0, dp(6), 0);
        parent.addView(tile, params);
        ImageView icon = new ImageView(this);
        icon.setImageResource(com.nplayer.app.R.drawable.ic_folder);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView name = text(group.name, 13, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, dp(7), 0, 0);
        tile.addView(name, new LinearLayout.LayoutParams(-1, -2));
        String size = group.sizeKnown ? formatBytes(group.sizeBytes) : "Size unavailable";
        TextView details = text(group.videoCount + " videos  ·  " + size, 10, MUTED, false);
        details.setGravity(Gravity.CENTER);
        details.setPadding(0, dp(4), 0, 0);
        tile.addView(details, new LinearLayout.LayoutParams(-1, -2));
        tile.setContentDescription(group.name + ", " + group.videoCount + " videos, " + size);
        tile.setOnClickListener(v -> {
            selectedMediaPath = group.path;
            renderItems();
        });
    }

    private void addSavedFolderRow(DocumentFile folder) {
        String name = folder.getName() == null ? "Selected folder" : folder.getName();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(background(SURFACE, 16, BORDER));
        row.setForeground(selectableForeground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(78));
        params.setMargins(0, 0, 0, dp(9));
        list.addView(row, params);
        ImageView icon = new ImageView(this);
        icon.setImageResource(com.nplayer.app.R.drawable.ic_folder);
        row.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(13), 0, 0, 0);
        labels.addView(text(name, 14, Color.WHITE, true));
        TextView subtitle = text("BROWSE SELECTED FOLDER", 10, MUTED, true);
        subtitle.setPadding(0, dp(4), 0, 0);
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(text("›", 26, MUTED, false));
        row.setOnClickListener(v -> {
            folderStack.clear();
            folderStack.add(folder);
            renderItems();
        });
    }

    private void addMediaGridTile(LinearLayout parent, MediaEntry entry) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(10));
        tile.setBackground(background(SURFACE, 15, BORDER));
        tile.setForeground(selectableForeground());
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, -2, 1);
        tileParams.setMargins(0, 0, dp(6), 0);
        parent.addView(tile, tileParams);

        FrameLayout poster = new FrameLayout(this);
        poster.setBackground(background(SURFACE_RAISED, 10));
        poster.setClipToOutline(true);
        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setContentDescription("Thumbnail for " + entry.title);
        poster.addView(thumbnail, new FrameLayout.LayoutParams(-1, -1));
        TextView placeholder = text("N", 25, ACCENT, true);
        placeholder.setGravity(Gravity.CENTER);
        poster.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        if (entry.durationMs > 0) {
            TextView duration = text(formatDuration(entry.durationMs), 10, Color.WHITE, true);
            duration.setPadding(dp(5), dp(2), dp(5), dp(2));
            duration.setBackground(background(0xD9000000, 5));
            FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.BOTTOM | Gravity.END);
            durationParams.setMargins(0, 0, dp(5), dp(5));
            poster.addView(duration, durationParams);
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                thumbnail.setImageBitmap(getContentResolver().loadThumbnail(entry.uri,
                        new android.util.Size(dp(240), dp(128)), null));
                placeholder.setVisibility(View.GONE);
            }
        } catch (Exception ignored) { }
        tile.addView(poster, new LinearLayout.LayoutParams(-1, dp(118)));

        TextView name = text(entry.title, 13, Color.WHITE, true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(dp(2), dp(8), dp(2), 0);
        tile.addView(name);
        String resolution = entry.width > 0 && entry.height > 0 ? entry.resolutionLabel() : "VIDEO";
        TextView info = text(resolution, 10, MUTED, false);
        info.setPadding(dp(2), dp(4), dp(2), 0);
        tile.addView(info);
        tile.setContentDescription(entry.title + ", " + resolution);
        tile.setOnClickListener(v -> play(entry));
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(tree, flags); } catch (SecurityException ignored) { }
            getPreferences(MODE_PRIVATE).edit().putString("folder", tree.toString()).apply();
            folderStack.clear();
            folderStack.add(DocumentFile.fromTreeUri(this, tree));
            activeTab = "Folders";
            renderItems();
        }
    }

    private void showSavedFolder() {
        String saved = getPreferences(MODE_PRIVATE).getString("folder", null);
        if (saved == null) {
            openFolderPicker();
        } else if (folderStack.isEmpty()) {
            folderStack.add(DocumentFile.fromTreeUri(this, Uri.parse(saved)));
        }
    }

    private void renderFolderContents() {
        DocumentFile directory = folderStack.get(folderStack.size() - 1);
        if (directory == null || !directory.exists()) {
            addEmptyState("Folder unavailable", "Choose a folder again to continue browsing");
            return;
        }
        TextView back = text(folderStack.size() > 1 ? "‹   Parent folder" : "‹   All folders",
                13, ACCENT, true);
        back.setPadding(dp(5), dp(7), 0, dp(13));
        list.addView(back);
        back.setOnClickListener(v -> {
            if (folderStack.size() > 1) folderStack.remove(folderStack.size() - 1);
            else folderStack.clear();
            renderItems();
        });
            DocumentFile[] children = directory.listFiles();
            ArrayList<MediaEntry> folderVideos = new ArrayList<>();
        if (children == null || children.length == 0) {
            addEmptyState("This folder is empty", "Select another folder to browse");
            return;
        }
        for (DocumentFile child : children) {
            String name = child.getName() == null ? "Untitled" : child.getName();
            if (child.isDirectory()) {
                MediaEntry folder = new MediaEntry(child.getUri(), name, 0, 0, 0, 0,
                    null, -1, true);
                addFolderRow(folder, () -> { folderStack.add(child); renderItems(); });
            } else if (child.getType() != null && child.getType().startsWith("video/")) {
                MediaEntry entry = new MediaEntry(child.getUri(), name, 0, 0, 0, 0,
                    null, -1, false);
                knownEntries.put(entry.uri.toString(), entry);
                    if (name.toLowerCase(Locale.ROOT).contains(query)) folderVideos.add(entry);
            }
        }
            addVideoItems(folderVideos);
    }

    private void addFolderRow(MediaEntry folder, Runnable open) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.setBackground(background(SURFACE, 14, BORDER));
        row.setForeground(selectableForeground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(70));
        params.setMargins(0, 0, 0, dp(8));
        list.addView(row, params);
        TextView icon = text("▰", 19, ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(background(SURFACE_RAISED, 10));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        TextView name = text(folder.title, 14, Color.WHITE, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView subtitle = text("FOLDER", 10, MUTED, true);
        subtitle.setPadding(0, dp(3), 0, 0);
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));
        TextView arrow = text("›", 25, MUTED, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(25), -1));
        row.setOnClickListener(v -> open.run());
    }

    private void play(MediaEntry entry) {
        knownEntries.put(entry.uri.toString(), entry);
        JSONArray old = recentUris();
        JSONArray updated = new JSONArray();
        updated.put(entry.uri.toString());
        for (int i = 0; i < old.length() && i < 19; i++) {
            String value = old.optString(i);
            if (!value.equals(entry.uri.toString())) updated.put(value);
        }
        String entryKey = "entry_" + Integer.toHexString(entry.uri.toString().hashCode());
        getPreferences(MODE_PRIVATE).edit()
            .putString("recent", updated.toString())
            .putString(entryKey + "_title", entry.title)
            .putLong(entryKey + "_duration", entry.durationMs)
            .putInt(entryKey + "_width", entry.width)
            .putInt(entryKey + "_height", entry.height)
            .putString(entryKey + "_path", entry.relativePath)
            .putLong(entryKey + "_size", entry.sizeBytes)
            .putLong(entryKey + "_date", entry.dateAdded)
            .apply();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.setData(entry.uri);
        intent.putExtra("title", entry.title);
        intent.putExtra("resume", getPreferences(MODE_PRIVATE).getLong(resumeKey(entry.uri), 0));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private JSONArray recentUris() {
        String value = getPreferences(MODE_PRIVATE).getString("recent", "[]");
        try { return new JSONArray(value); } catch (Exception ignored) { return new JSONArray(); }
    }

    private MediaEntry entryForUri(String value) {
        MediaEntry known = knownEntries.get(value);
        if (known != null) return known;
        String key = "entry_" + Integer.toHexString(value.hashCode());
        String title = getPreferences(MODE_PRIVATE).getString(key + "_title", null);
        if (title == null) return null;
        return new MediaEntry(Uri.parse(value), title,
                getPreferences(MODE_PRIVATE).getLong(key + "_duration", 0),
                getPreferences(MODE_PRIVATE).getInt(key + "_width", 0),
                getPreferences(MODE_PRIVATE).getInt(key + "_height", 0),
                getPreferences(MODE_PRIVATE).getLong(key + "_date", 0),
                getPreferences(MODE_PRIVATE).getString(key + "_path", null),
                getPreferences(MODE_PRIVATE).getLong(key + "_size", -1), false);
    }

    static String resumeKey(Uri uri) {
        return "resume_" + Integer.toHexString(uri.toString().hashCode());
    }

    static String formatDuration(long milliseconds) {
        long total = Math.max(0, milliseconds / 1000);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable background(int color, int radius) {
        return background(color, radius, 0);
    }

    private GradientDrawable background(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private android.graphics.drawable.Drawable selectableForeground() {
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            return getDrawable(value.resourceId);
        }
        return null;
    }

    private static final class FolderGroup {
        final String path;
        final String name;
        int videoCount;
        long sizeBytes;
        boolean sizeKnown = true;
        boolean hasSearchMatch;

        FolderGroup(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}