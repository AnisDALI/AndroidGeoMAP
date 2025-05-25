package fr.android.androidgeomap;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    // Constantes
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final String PREFS_NAME = "PhotoPrefs";
    private static final String KEY_PHOTO_URI = "photoUri";

    // UI
    private TextView textLatitude, textLongitude, textAddress;
    private ImageView imagePhoto;

    // Localisation
    private LocationManager locationManager;
    private String provider;

    // Photo
    private Uri photoUri;
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation UI
        textLatitude = findViewById(R.id.textLatitude);
        textLongitude = findViewById(R.id.textLongitude);
        textAddress = findViewById(R.id.textAddress);
        imagePhoto = findViewById(R.id.imagePhoto);

        restorePhotoUri();

        // Boutons de navigation
        findViewById(R.id.buttonMap).setOnClickListener(v -> startActivity(new Intent(this, MapsActivity.class)));
        findViewById(R.id.buttonHistory).setOnClickListener(v -> startActivity(new Intent(this, PhotoHistoryActivity.class)));
        findViewById(R.id.buttonViewRemote).setOnClickListener(v -> startActivity(new Intent(this, PhotoRemoteActivity.class)));
        findViewById(R.id.buttonPhoto).setOnClickListener(v -> dispatchTakePictureIntent());

        // Initialisation localisation
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, true);

        checkAndRequestPermissions();
    }

    // 1. Gestion des permissions
    private void checkAndRequestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
            }, 1);
        } else {
            initializeLocation();
        }
    }

    private void initializeLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                onLocationChanged(location);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Ajout de l’appel à la méthode parent
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeLocation();
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Permission de caméra requise", Toast.LENGTH_SHORT).show();
            }
        }
    }


    // 2. Cycle de vie
    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(provider, 1000, 1, this);
        }
        if (photoUri != null) {
            loadImageFromUri(photoUri);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
        savePhotoUri();
    }

    // 3. Gestion de la géolocalisation
    @Override
    public void onLocationChanged(@NonNull Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        textLatitude.setText("Latitude: " + lat);
        textLongitude.setText("Longitude: " + lon);

        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                textAddress.setText("Adresse: " + addresses.get(0).getAddressLine(0));
            }
        } catch (IOException e) {
            textAddress.setText("Erreur lors du géocodage");
        }
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        Toast.makeText(this, "GPS activé", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Toast.makeText(this, "GPS désactivé", Toast.LENGTH_SHORT).show();
    }

    // 4. Capture photo
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File photoFile;
        try {
            photoFile = createImageFile();
            currentPhotoPath = photoFile.getAbsolutePath();
        } catch (IOException e) {
            Toast.makeText(this, "Erreur création fichier photo", Toast.LENGTH_SHORT).show();
            return;
        }

        photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
    }

    // 5. Résultat capture photo
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK && photoUri != null) {
            File photoFile = new File(currentPhotoPath);
            if (photoFile.exists()) {
                loadImageFromUri(photoUri);
                galleryAddPic();
                saveToSQLite();
                sendDataToMySQL();
            }
        }
    }

    private void saveToSQLite() {
        LocalDatabaseHelper dbHelper = new LocalDatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(LocalDatabaseHelper.COL_LAT, getDoubleFromText(textLatitude));
        values.put(LocalDatabaseHelper.COL_LON, getDoubleFromText(textLongitude));
        values.put(LocalDatabaseHelper.COL_ADDRESS, textAddress.getText().toString());
        values.put(LocalDatabaseHelper.COL_URI, photoUri.toString());
        values.put(LocalDatabaseHelper.COL_DATE, getCurrentDate());

        db.insert(LocalDatabaseHelper.TABLE_PHOTOS, null, values);
        db.close();
    }

    private void sendDataToMySQL() {
        double lat = getDoubleFromText(textLatitude);
        double lon = getDoubleFromText(textLongitude);
        String address = textAddress.getText().toString();
        String uri = photoUri.toString();
        String date = getCurrentDate();

        new Thread(() -> {
            try {
                Class.forName("com.mysql.jdbc.Driver");
                Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://10.0.2.2:3306/geoapp", "aaa", "aaa");

                String sql = "INSERT INTO photos (latitude, longitude, address, uri, date) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement stmt = connection.prepareStatement(sql);
                stmt.setDouble(1, lat);
                stmt.setDouble(2, lon);
                stmt.setString(3, address);
                stmt.setString(4, uri);
                stmt.setString(5, date);
                stmt.executeUpdate();

                stmt.close();
                connection.close();
            } catch (Exception e) {
                Log.e("MySQL", "Erreur MySQL : " + e.getMessage());
            }
        }).start();
    }

    // 6. Utilitaires
    private void galleryAddPic() {
        if (currentPhotoPath != null) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            File f = new File(currentPhotoPath);
            mediaScanIntent.setData(Uri.fromFile(f));
            sendBroadcast(mediaScanIntent);
        }
    }

    private void loadImageFromUri(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);

            int scaleFactor = Math.max(1, Math.min(
                    options.outWidth / Math.max(1, imagePhoto.getWidth()),
                    options.outHeight / Math.max(1, imagePhoto.getHeight())));

            options.inJustDecodeBounds = false;
            options.inSampleSize = scaleFactor;

            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);
            imagePhoto.setImageBitmap(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Erreur chargement image", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePhotoUri() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_PHOTO_URI, photoUri != null ? photoUri.toString() : null).apply();
    }

    private void restorePhotoUri() {
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PHOTO_URI, null);
        if (uriStr != null) {
            photoUri = Uri.parse(uriStr);
        }
    }

    private double getDoubleFromText(TextView textView) {
        return Double.parseDouble(textView.getText().toString().split(": ")[1]);
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
    }
}
