package com.example.radar2;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private MapView mapView;
    private MapController mapController;
    private static final String TAG = "MapDebug";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        forceInternalStorageOnly();
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        setupOfflineMap();
    }

    private void forceInternalStorageOnly() {
        // Create base directory for osmdroid
        File internalBasePath = new File(getFilesDir(), "osmdroid");

        // Create base directory if it doesn't exist
        if (!internalBasePath.exists()) {
            internalBasePath.mkdirs();
        }

        // IMPORTANT: Create a dedicated tile cache directory (NOT tiles.mbtiles)
        // This should be a DIRECTORY, not a file
        File tileCacheDir = new File(internalBasePath, "tile_cache");
        if (!tileCacheDir.exists()) {
            tileCacheDir.mkdirs();
        }

        // Configure osmdroid paths
        Configuration.getInstance().setOsmdroidBasePath(internalBasePath);
        Configuration.getInstance().setOsmdroidTileCache(tileCacheDir);
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));

        Log.d(TAG, "OSMDroid configured with base path: " + internalBasePath.getAbsolutePath());
        Log.d(TAG, "Tile cache path: " + tileCacheDir.getAbsolutePath());
    }

    private void setupOfflineMap() {
        try {
            // Get the osmdroid base path
            File osmdroidBasePath = new File(getFilesDir(), "osmdroid");

            // The tiles.mbtiles file path (as a FILE, not directory)
            File sqliteFile = new File(osmdroidBasePath, "tiles.mbtiles");

            // CRITICAL: Check if the path exists but is a directory (clean up if needed)
            if (sqliteFile.exists() && sqliteFile.isDirectory()) {
                Log.w(TAG, "tiles.mbtiles is a directory, deleting it...");
                deleteRecursive(sqliteFile);
            }

            // Copy file from assets if it doesn't exist
            if (!sqliteFile.exists()) {
                sqliteFile = copyFileFromAssets("tiles.mbtiles", sqliteFile);
            }

            if (sqliteFile != null && sqliteFile.exists() && !sqliteFile.isDirectory()) {
                Log.d(TAG, "✅ tiles.mbtiles file found: " + sqliteFile.getAbsolutePath());
                Log.d(TAG, "File size: " + sqliteFile.length() + " bytes");

                // Create offline tile provider with the zip file
                OfflineTileProvider tileProvider = new OfflineTileProvider(
                        new SimpleRegisterReceiver(this),
                        new File[]{sqliteFile}
                );

                mapView.setTileProvider(tileProvider);

                // Auto-detect the exact tile source name
                String sourceName = "Mapnik"; // Fallback default
                IArchiveFile[] archives = tileProvider.getArchives();
                if (archives != null && archives.length > 0) {
                    Set<String> tileSources = archives[0].getTileSources();
                    if (tileSources != null && !tileSources.isEmpty()) {
                        sourceName = tileSources.iterator().next();
                        Log.d(TAG, "✅ Found Tile Source Name: " + sourceName);
                    }
                }

                // Apply the correct tile source
                mapView.setTileSource(FileBasedTileSource.getSource(sourceName));
                mapView.setUseDataConnection(false);
                mapView.setBuiltInZoomControls(true);
                mapView.setMultiTouchControls(true);

                // Set zoom levels
                mapView.setMinZoomLevel(14.0);
                mapView.setMaxZoomLevel(16.0);

                // Set map controller and center
                mapController = (MapController) mapView.getController();
                GeoPoint startPoint = new GeoPoint(34.0967443,49.7057792);

                mapController.setZoom(15.0);
                mapController.setCenter(startPoint);

                Log.d(TAG, "✅ Map setup complete");
            } else {
                Log.e(TAG, "❌ tiles.mbtiles file not found or is a directory");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting up offline map", e);
        }
    }
    private GeoPoint tileToGeoPoint(int x, int y, int zoom) {
        double n = Math.pow(2.0, zoom);
        double lon = (x / n) * 360.0 - 180.0;
        double lat = Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / n))) * 180.0 / Math.PI;
        return new GeoPoint(lat, lon);
    }
    /**
     * Delete a file or directory recursively
     */
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        boolean deleted = fileOrDirectory.delete();
        if (deleted) {
            Log.d(TAG, "Deleted: " + fileOrDirectory.getAbsolutePath());
        }
    }

    /**
     * Copy file from assets to destination
     */
    private File copyFileFromAssets(String fileName, File destinationFile) {
        try (InputStream inputStream = getAssets().open(fileName);
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

            byte[] buffer = new byte[8192];
            int length;
            long totalBytes = 0;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
                totalBytes += length;
            }
            Log.d(TAG, "Copied " + totalBytes + " bytes from assets to " + destinationFile.getAbsolutePath());
            return destinationFile;
        } catch (IOException e) {
            Log.e(TAG, "Error copying file from assets", e);
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }
}