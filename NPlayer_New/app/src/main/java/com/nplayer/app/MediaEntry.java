package com.nplayer.app;

import android.net.Uri;

final class MediaEntry {
    final Uri uri;
    final String title;
    final long durationMs;
    final int width;
    final int height;
    final long dateAdded;
    final String relativePath;
    final long sizeBytes;
    final boolean directory;

    MediaEntry(Uri uri, String title, long durationMs, int width, int height, long dateAdded,
               String relativePath, long sizeBytes, boolean directory) {
        this.uri = uri;
        this.title = title;
        this.durationMs = durationMs;
        this.width = width;
        this.height = height;
        this.dateAdded = dateAdded;
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
        this.directory = directory;
    }

    String resolutionLabel() {
        if (width <= 0 || height <= 0) return "Resolution unavailable";
        return width + " x " + height;
    }
}