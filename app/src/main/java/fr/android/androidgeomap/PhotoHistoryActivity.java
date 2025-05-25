package fr.android.androidgeomap;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PhotoHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PhotoAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_history);

        // Bouton retour vers MainActivity
        Button buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        // Initialisation du RecyclerView
        recyclerView = findViewById(R.id.recyclerViewPhotos);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Chargement des photos enregistrées en base locale SQLite
        adapter = new PhotoAdapter(this, loadPhotosFromDB());
        recyclerView.setAdapter(adapter);
    }

    // Récupère les données enregistrées dans SQLite
    private List<PhotoItem> loadPhotosFromDB() {
        List<PhotoItem> photos = new ArrayList<>();
        LocalDatabaseHelper dbHelper = new LocalDatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                LocalDatabaseHelper.TABLE_PHOTOS,
                null, null, null, null, null,
                LocalDatabaseHelper.COL_DATE + " DESC"
        );

        try {
            while (cursor.moveToNext()) {
                PhotoItem item = new PhotoItem();

                int uriIndex = cursor.getColumnIndexOrThrow(LocalDatabaseHelper.COL_URI);
                int addressIndex = cursor.getColumnIndexOrThrow(LocalDatabaseHelper.COL_ADDRESS);
                int dateIndex = cursor.getColumnIndexOrThrow(LocalDatabaseHelper.COL_DATE);

                item.uri = Uri.parse(cursor.getString(uriIndex));
                item.address = cursor.getString(addressIndex);
                item.date = cursor.getString(dateIndex);

                photos.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cursor.close();
            db.close();
        }

        return photos;
    }

    // Objet représentant chaque ligne (photo) affichée
    public static class PhotoItem {
        Uri uri;
        String address;
        String date;
    }

    // Adaptateur pour afficher les photos dans le RecyclerView
    public static class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

        private final Context context;
        private final List<PhotoItem> items;

        public PhotoAdapter(Context context, List<PhotoItem> items) {
            this.context = context;
            this.items = items;
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            PhotoItem item = items.get(position);
            holder.imageView.setImageURI(item.uri);
            holder.textAddress.setText(item.address);
            holder.textDate.setText(item.date);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // Déclaration des vues contenues dans chaque "item_photo"
        public static class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView textAddress;
            TextView textDate;

            public PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageItem);
                textAddress = itemView.findViewById(R.id.textAddressItem);
                textDate = itemView.findViewById(R.id.textDateItem);
            }
        }
    }
}
