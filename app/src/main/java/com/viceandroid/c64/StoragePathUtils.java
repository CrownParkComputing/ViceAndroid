package com.viceandroid.c64;

import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;

final class StoragePathUtils {
    private StoragePathUtils() {
    }

    static File fileForTreeUri(Uri treeUri) {
        if (treeUri == null) {
            return null;
        }
        String documentId;
        try {
            documentId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception ignored) {
            return null;
        }
        int colon = documentId.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String volume = documentId.substring(0, colon);
        String relativePath = documentId.substring(colon + 1);
        File base = "primary".equalsIgnoreCase(volume)
                ? new File("/storage/emulated/0")
                : new File("/storage/" + volume);
        return relativePath.isEmpty() ? base : new File(base, relativePath);
    }

    static boolean isWritableDirectory(File directory) {
        return directory != null && directory.isDirectory() && directory.canWrite();
    }
}
