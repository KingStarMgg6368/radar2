package com.example.radar2;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
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

        // Add a delayed log to see position after map is fully loaded
        mapView.postDelayed(new Runnable() {
            @Override
            public void run() {
                logCurrentPosition();
            }
        }, 2000);
    }

    private void forceInternalStorageOnly() {
        // Create base directory for osmdroid
        File internalBasePath = new File(getFilesDir(), "osmdroid");

        // Create base directory if it doesn't exist
        if (!internalBasePath.exists()) {
            internalBasePath.mkdirs();
        }

        // IMPORTANT: Create a dedicated tile cache directory (NOT tiles.zip)
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

            // The tiles.zip file path (as a FILE, not directory)
            File sqliteFile = new File(osmdroidBasePath, "tiles.zip");

            // CRITICAL: Check if the path exists but is a directory (clean up if needed)
            if (sqliteFile.exists() && sqliteFile.isDirectory()) {
                Log.w(TAG, "tiles.zip is a directory, deleting it...");
                deleteRecursive(sqliteFile);
            }

            // Copy file from assets if it doesn't exist
            if (!sqliteFile.exists()) {
                sqliteFile = copyFileFromAssets("tiles.zip", sqliteFile);
            }

            if (sqliteFile != null && sqliteFile.exists() && !sqliteFile.isDirectory()) {
                Log.d(TAG, "✅ tiles.zip file found: " + sqliteFile.getAbsolutePath());
                Log.d(TAG, "File size: " + sqliteFile.length() + " bytes");

                // Create offline tile provider with the zip file
                OfflineTileProvider tileProvider = new OfflineTileProvider(
                        new SimpleRegisterReceiver(this),
                        new File[]{sqliteFile}
                );

                mapView.setTileProvider(tileProvider);

                // Auto-detect the exact tile source name
                //String sourceName = "Mapnik"; // Fallback default
                //IArchiveFile[] archives = tileProvider.getArchives();
                //if (archives != null && archives.length > 0) {
                //    Set<String> tileSources = archives[0].getTileSources();
                //    if (tileSources != null && !tileSources.isEmpty()) {
                //        sourceName = tileSources.iterator().next();
                //        Log.d(TAG, "✅ Found Tile Source Name: " + sourceName);
                //    }
                //}

                // Apply the correct tile source
                mapView.setTileSource(new XYTileSource("tiles",15,17,256,".png",new String[]{}));
                mapView.setUseDataConnection(false);
                mapView.setBuiltInZoomControls(true);
                mapView.setMultiTouchControls(true);

                // Set zoom levels
                mapView.setMinZoomLevel(14.0);
                mapView.setMaxZoomLevel(17.0);

                // Set map controller and center
                mapController = (MapController) mapView.getController();
                // CORRECT: GeoPoint(latitude, longitude)
                GeoPoint startPoint = new GeoPoint(34.0967443, 49.7057792);

                mapController.setZoom(16.0);
                mapController.setCenter(startPoint);

                // Log map setup details
                Log.d(TAG, "==========================================");
                Log.d(TAG, "📍 MAP SETUP COMPLETE");
                Log.d(TAG, "==========================================");
                logCurrentPosition();

            } else {
                Log.e(TAG, "❌ tiles.zip file not found or is a directory");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting up offline map", e);
        }
    }

    /**
     * Log current map position and view details
     */
    private void logCurrentPosition() {
        if (mapView == null) {
            Log.e(TAG, "MapView is null, cannot log position");
            return;
        }

        try {
            // Get current center position
            GeoPoint center = (GeoPoint) mapView.getMapCenter();
            double centerLat = center.getLatitude();
            double centerLon = center.getLongitude();

            // Get current zoom level
            double zoomLevel = mapView.getZoomLevelDouble();

            // Get visible bounds
            org.osmdroid.util.BoundingBox boundingBox = mapView.getBoundingBox();

            // Calculate visible tile range
            int minZoom = (int) zoomLevel;
            int[] topLeftTile = geoPointToTile(boundingBox.getLatNorth(), boundingBox.getLonWest(), minZoom);
            int[] bottomRightTile = geoPointToTile(boundingBox.getLatSouth(), boundingBox.getLonEast(), minZoom);

            Log.d(TAG, "==========================================");
            Log.d(TAG, "📍 CURRENT MAP POSITION");
            Log.d(TAG, "==========================================");
            Log.d(TAG, "🎯 Center Latitude:  " + centerLat);
            Log.d(TAG, "🎯 Center Longitude: " + centerLon);
            Log.d(TAG, "🔍 Zoom Level:       " + zoomLevel);
            Log.d(TAG, "");
            Log.d(TAG, "📐 VIEWPORT BOUNDS:");
            Log.d(TAG, "   North: " + boundingBox.getLatNorth());
            Log.d(TAG, "   South: " + boundingBox.getLatSouth());
            Log.d(TAG, "   East:  " + boundingBox.getLonEast());
            Log.d(TAG, "   West:  " + boundingBox.getLonWest());
            Log.d(TAG, "");
            Log.d(TAG, "🗺️ VISIBLE TILE RANGE (Zoom " + minZoom + "):");
            Log.d(TAG, "   Top-Left Tile:     " + topLeftTile[0] + "/" + topLeftTile[1]);
            Log.d(TAG, "   Bottom-Right Tile: " + bottomRightTile[0] + "/" + bottomRightTile[1]);
            Log.d(TAG, "");

            // Log current center tile
            int[] centerTile = geoPointToTile(centerLat, centerLon, minZoom);
            Log.d(TAG, "🎯 CENTER TILE: " + minZoom + "/" + centerTile[0] + "/" + centerTile[1]);
            Log.d(TAG, "");

            // Log tile coordinates for all zoom levels
            Log.d(TAG, "📊 TILE COORDINATES FOR CURRENT POSITION:");
            for (int z = 14; z <= 16; z++) {
                int[] tile = geoPointToTile(centerLat, centerLon, z);
                Log.d(TAG, "   Zoom " + z + ": " + z + "/" + tile[0] + "/" + tile[1]);
            }
            Log.d(TAG, "==========================================");

        } catch (Exception e) {
            Log.e(TAG, "Error logging current position", e);
        }
    }

    /**
     * Convert GeoPoint to tile coordinates
     * @param latitude Latitude
     * @param longitude Longitude
     * @param zoom Zoom level
     * @return int array [tileX, tileY]
     */
    private int[] geoPointToTile(double latitude, double longitude, int zoom) {
        int tileX = (int) Math.floor((longitude + 180) / 360 * (1 << zoom));
        int tileY = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(latitude)) + 1 / Math.cos(Math.toRadians(latitude))) / Math.PI) / 2 * (1 << zoom));
        return new int[]{tileX, tileY};
    }

    /**
     * Convert tile coordinates to GeoPoint (top-left corner)
     */
    private GeoPoint tileToGeoPoint(int x, int y, int zoom) {
        double n = Math.pow(2.0, zoom);
        double lon = (x / n) * 360.0 - 180.0;
        double lat = Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / n))) * 180.0 / Math.PI;
        return new GeoPoint(lat, lon);
    }

    /**
     * Get tile center coordinates
     */
    private GeoPoint getTileCenter(int x, int y, int zoom) {
        GeoPoint topLeft = tileToGeoPoint(x, y, zoom);
        GeoPoint bottomRight = tileToGeoPoint(x + 1, y + 1, zoom);
        double centerLat = (topLeft.getLatitude() + bottomRight.getLatitude()) / 2;
        double centerLon = (topLeft.getLongitude() + bottomRight.getLongitude()) / 2;
        return new GeoPoint(centerLat, centerLon);
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

    // Add touch listener to log position when user moves the map
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mapView != null) {
            // Log position whenever user interacts with map
            mapView.addOnFirstLayoutListener((v, left, top, right, bottom) -> {
                logCurrentPosition();
            });
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