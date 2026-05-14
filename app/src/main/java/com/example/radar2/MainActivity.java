package com.example.radar2;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.modules.DatabaseFileArchive;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapController;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    private MapView mapView;
    private MapController mapController;
    private static final String TAG = "MapDebug";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force OSMDroid to use internal storage
        forceInternalStorageOnly();

        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        setupOfflineMap();
    }

    private void forceInternalStorageOnly() {
        File internalBasePath = new File(getFilesDir(), "osmdroid");
        File internalTilePath = new File(internalBasePath, "tiles");

        if (!internalBasePath.exists()) internalBasePath.mkdirs();
        if (!internalTilePath.exists()) internalTilePath.mkdirs();

        Configuration.getInstance().setOsmdroidBasePath(internalBasePath);
        Configuration.getInstance().setOsmdroidTileCache(internalTilePath);
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));

        Log.d(TAG, "OSMDroid Base Path: " + internalBasePath.getAbsolutePath());
    }
    private void setupOfflineMap() {
        try {
            // Copy your SQLite file to internal storage
            File sqliteFile = new File(getFilesDir(), "op.sqlite");
            if (!sqliteFile.exists()) {
                sqliteFile = copyFileFromAssets("op.sqlite");
            }

            if (sqliteFile != null && sqliteFile.exists()) {
                Log.d(TAG, "✅ SQLite file found: " + sqliteFile.getAbsolutePath());
                Log.d(TAG, "✅ File size: " + sqliteFile.length() + " bytes");

                // 🔥 KEY: Just set the map to offline mode with default tile source
                // OSMDroid will automatically scan the base path for archives
                mapView.setUseDataConnection(false);
                mapView.setBuiltInZoomControls(true);
                mapView.setMultiTouchControls(true);

                // Set reasonable zoom levels
                mapView.setMinZoomLevel(7.0);
                mapView.setMaxZoomLevel(16.0);

                // Center on Poltava, Ukraine
                mapController = (MapController) mapView.getController();
                GeoPoint startPoint = new GeoPoint(48.328857, 34.985003);
                mapController.setZoom(7.5);
                mapController.setCenter(startPoint);

                Log.d(TAG, "✅ Map setup complete - OSMDroid will auto-detect archive");
            } else {
                Log.e(TAG, "❌ SQLite file not found!");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error setting up offline map", e);
        }
    }
    private File copyFileFromAssets(String fileName) {
        File destinationFile = new File(getFilesDir(), fileName);

        if (destinationFile.exists()) {
            Log.d(TAG, "File already exists: " + destinationFile.getAbsolutePath());
            return destinationFile;
        }

        try (InputStream inputStream = getAssets().open(fileName);
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            Log.d(TAG, "Copied file: " + destinationFile.getAbsolutePath());
            return destinationFile;
        } catch (IOException e) {
            Log.e(TAG, "Error copying file", e);
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }
}