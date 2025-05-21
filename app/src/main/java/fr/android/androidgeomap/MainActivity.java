package fr.android.androidgeomap;

import java.util.List;
import java.util.Locale;
import java.io.IOException;

import android.widget.Button;
import android.content.Intent;

import android.graphics.Bitmap;
import android.provider.MediaStore;


import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Address;
import android.location.Geocoder;

import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;

import java.io.File;

import androidx.core.content.FileProvider;


import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

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
    private Uri photoUri;
    private ImageView imagePhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);




        // 1. Récupérer les TextViews
        textLatitude = findViewById(R.id.textLatitude);
        textLongitude = findViewById(R.id.textLongitude);
        textAddress = findViewById(R.id.textAddress);

        Button buttonMap = findViewById(R.id.buttonMap);
        buttonMap.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapsActivity.class);
            startActivity(intent);
        });



        // 2. Initialiser le LocationManager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, true);

        // 3. Vérifier et demander les permissions si nécessaire
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);
            return;
        }

        // 4. Récupérer la dernière position connue
        Location location = locationManager.getLastKnownLocation(provider);
        if (location != null) {
            onLocationChanged(location);
        } else {
            textLatitude.setText("Latitude: indisponible");
            textLongitude.setText("Longitude: indisponible");
        }


        imagePhoto = findViewById(R.id.imagePhoto);
        Button buttonPhoto = findViewById(R.id.buttonPhoto);

        buttonPhoto.setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                if (photoFile != null) {
                    photoUri = FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(provider, 1000, 1, this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
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

    private File createImageFile() throws IOException {
        // 1. Nom de fichier unique
        String imageFileName = "photo_" + System.currentTimeMillis();

        // 2. Dossier de destination : /Android/data/<package>/files/Pictures
        File storageDir = new File(getExternalFilesDir(null), "Pictures");

        // 3. Créer le dossier s’il n’existe pas
        if (!storageDir.exists()) {
            boolean success = storageDir.mkdirs();
            if (!success) {
                throw new IOException("Impossible de créer le dossier Pictures");
            }
        }

        // 4. Créer le fichier temporaire .jpg
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoUri);
                imagePhoto.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



}