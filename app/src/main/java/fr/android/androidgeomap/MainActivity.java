package fr.android.androidgeomap;

import java.util.List;
import java.util.Locale;
import android.util.Log;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.InputStream;
import java.io.IOException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import android.widget.Button;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Environment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Address;
import android.location.Geocoder;

import androidx.core.content.FileProvider;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private TextView textLatitude;
    private TextView textLongitude;
    private TextView textAddress;

    private LocationManager locationManager;
    private String provider;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final String PREFS_NAME = "PhotoPrefs";
    private static final String KEY_PHOTO_URI = "photoUri";

    private Uri photoUri;
    private ImageView imagePhoto;
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Récupérer les TextViews
        textLatitude = findViewById(R.id.textLatitude);
        textLongitude = findViewById(R.id.textLongitude);
        textAddress = findViewById(R.id.textAddress);
        imagePhoto = findViewById(R.id.imagePhoto);

        // Récupérer l'URI de la photo précédente s'il existe
        restorePhotoUri();

        // Boutton permettant de lancer l'activité de géolocalisation Google Map
        Button buttonMap = findViewById(R.id.buttonMap);
        buttonMap.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapsActivity.class);
            startActivity(intent);
        });
        Button buttonHistory = findViewById(R.id.buttonHistory);
        buttonHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PhotoHistoryActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.buttonViewRemote).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, PhotoRemoteActivity.class));
        });



        // Initialiser le LocationManager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, true);

        // Vérifier et demander les permissions si nécessaire
        checkAndRequestPermissions();

        Button buttonPhoto = findViewById(R.id.buttonPhoto);
        buttonPhoto.setOnClickListener(v -> {
            dispatchTakePictureIntent();
        });
    }

    private void checkAndRequestPermissions() {
        // Vérification des permissions de localisation
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);
            return;
        }

        // Vérification de la permission caméra
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return;
        }

        // Si on a les permissions, initialiser la dernière position connue
        initializeLocation();
    }

    private void initializeLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                onLocationChanged(location);
            } else {
                textLatitude.setText("Latitude: indisponible");
                textLongitude.setText("Longitude: indisponible");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(provider, 1000, 1, this);
        }

        // Si une photo a été prise, la charger
        if (photoUri != null) {
            loadImageFromUri(photoUri);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);

        // Sauvegarder l'URI de la photo
        savePhotoUri();
    }

    private void savePhotoUri() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (photoUri != null) {
            editor.putString(KEY_PHOTO_URI, photoUri.toString());
        }
        editor.apply();
    }

    private void restorePhotoUri() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uriString = settings.getString(KEY_PHOTO_URI, null);
        if (uriString != null) {
            photoUri = Uri.parse(uriString);
            loadImageFromUri(photoUri);
        }
    }

    private void loadImageFromUri(Uri uri) {
        try {
            // Log pour débugger
            Log.d("PhotoDebug", "Tentative de chargement de l'image depuis URI: " + uri.toString());

            // Vérifier que l'URI est accessible
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    Toast.makeText(this, "Flux d'entrée nul lors de l'accès à l'URI", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Utiliser BitmapFactory.Options pour éviter les problèmes de mémoire
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true; // Juste obtenir les dimensions sans charger l'image
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);

            // Calculer le facteur de réduction
            int photoW = options.outWidth;
            int photoH = options.outHeight;
            int targetW = imagePhoto.getWidth();
            int targetH = imagePhoto.getHeight();

            // Si imageView n'a pas encore de dimensions, utiliser une valeur par défaut
            if (targetW == 0) targetW = 300;
            if (targetH == 0) targetH = 300;

            int scaleFactor = Math.max(1, Math.min(photoW/targetW, photoH/targetH));

            // Utiliser le facteur de réduction pour charger l'image
            options.inJustDecodeBounds = false;
            options.inSampleSize = scaleFactor;

            Bitmap bitmap = BitmapFactory.decodeStream(
                    getContentResolver().openInputStream(uri),
                    null,
                    options
            );

            if (bitmap != null) {
                imagePhoto.setImageBitmap(bitmap);
                Log.d("PhotoDebug", "Image chargée avec succès, dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            } else {
                Toast.makeText(this, "Impossible de décoder l'image", Toast.LENGTH_SHORT).show();
                Log.e("PhotoDebug", "Bitmap null après décodage");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("PhotoDebug", "Erreur lors du chargement de l'image: " + e.getMessage());
            Toast.makeText(this, "Erreur lors du chargement de l'image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Créer le fichier où la photo doit être sauvegardée, sans vérifier resolveActivity d'abord
        File photoFile = null;
        try {
            photoFile = createImageFile();
            currentPhotoPath = photoFile.getAbsolutePath();
        } catch (IOException ex) {
            // Erreur lors de la création du fichier
            Toast.makeText(this, "Erreur lors de la création du fichier photo", Toast.LENGTH_SHORT).show();
            ex.printStackTrace();
            return;
        }

        // Si le fichier a été créé avec succès
        if (photoFile != null) {
            try {
                photoUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        photoFile
                );
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                // Accorder les permissions de lecture/écriture pour l'URI
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // Essayer de lancer l'activité de prise de photo
                try {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                } catch (Exception e) {
                    Toast.makeText(this, "Erreur lors du lancement de la caméra: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Erreur avec FileProvider: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    }

    private File createImageFile() throws IOException {
        // Créer un nom de fichier unique basé sur l'horodatage
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        // Dossier de destination
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        // Créer le fichier avec un préfixe, un suffixe (.jpg) et le dossier
        return File.createTempFile(
                imageFileName,  // préfixe
                ".jpg",         // suffixe
                storageDir      // dossier
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                // La photo a été sauvegardée dans photoUri
                if (photoUri != null) {
                    try {
                        // Vérifier que le fichier existe bien
                        File photoFile = new File(currentPhotoPath);
                        if (photoFile.exists() && photoFile.length() > 0) {
                            loadImageFromUri(photoUri);
                            // Ajouter la photo à la galerie
                            galleryAddPic();
                            Toast.makeText(this, "Photo sauvegardée avec succès", Toast.LENGTH_SHORT).show();

                            LocalDatabaseHelper dbHelper = new LocalDatabaseHelper(this);
                            SQLiteDatabase db = dbHelper.getWritableDatabase();

                            ContentValues values = new ContentValues();
                            values.put(LocalDatabaseHelper.COL_LAT, Double.parseDouble(textLatitude.getText().toString().replace("Latitude: ", "")));
                            values.put(LocalDatabaseHelper.COL_LON, Double.parseDouble(textLongitude.getText().toString().replace("Longitude: ", "")));
                            values.put(LocalDatabaseHelper.COL_ADDRESS, textAddress.getText().toString());
                            values.put(LocalDatabaseHelper.COL_URI, photoUri.toString());
                            values.put(LocalDatabaseHelper.COL_DATE, new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));

                            db.insert(LocalDatabaseHelper.TABLE_PHOTOS, null, values);
                            db.close();


                            sendDataToMySQL(
                                    Double.parseDouble(textLatitude.getText().toString().replace("Latitude: ", "")),
                                    Double.parseDouble(textLongitude.getText().toString().replace("Longitude: ", "")),
                                    textAddress.getText().toString(),
                                    photoUri.toString(),
                                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date())
                            );


                        } else {
                            Toast.makeText(this, "Fichier photo vide ou inexistant", Toast.LENGTH_SHORT).show();
                        }

                    } catch (Exception e) {
                        Toast.makeText(this, "Erreur lors du traitement de la photo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else {
                    // Si photoUri est null mais que data contient une miniature
                    Bundle extras = data != null ? data.getExtras() : null;
                    if (extras != null && extras.containsKey("data")) {
                        Bitmap imageBitmap = (Bitmap) extras.get("data");
                        if (imageBitmap != null) {
                            imagePhoto.setImageBitmap(imageBitmap);
                            Toast.makeText(this, "Affichage de la miniature (pas d'image complète)", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Erreur: Aucune donnée d'image disponible", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Prise de photo annulée", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Échec de la prise de photo: code " + resultCode, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendDataToMySQL(double lat, double lon, String address, String uri, String date) {
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

                Log.d("MySQL", "Données insérées avec succès");

                stmt.close();
                connection.close();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("MySQL", "Erreur lors de l'envoi MySQL : " + e.getMessage());
            }
        }).start();
    }

    private void galleryAddPic() {
        if (currentPhotoPath != null) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            File f = new File(currentPhotoPath);
            Uri contentUri = Uri.fromFile(f);
            mediaScanIntent.setData(contentUri);
            this.sendBroadcast(mediaScanIntent);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        textLatitude.setText("Latitude: " + lat);
        textLongitude.setText("Longitude: " + lon);

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                textAddress.setText("Adresse: " + address);
            } else {
                textAddress.setText("Adresse: non trouvée");
            }
        } catch (IOException e) {
            e.printStackTrace();
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

}