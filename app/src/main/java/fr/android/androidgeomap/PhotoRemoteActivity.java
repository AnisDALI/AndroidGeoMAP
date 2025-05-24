package fr.android.androidgeomap;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class PhotoRemoteActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RemotePhotoAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_remote);
        findViewById(R.id.buttonBackRemote).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerViewRemote);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RemotePhotoAdapter();
        recyclerView.setAdapter(adapter);

        loadRemotePhotos();
    }

    private void loadRemotePhotos() {
        new Thread(() -> {
            List<RemotePhotoItem> photos = new ArrayList<>();
            try {
                Class.forName("com.mysql.jdbc.Driver");
                Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://10.0.2.2:3306/geoapp", "aaa", "aaa");

                String sql = "SELECT latitude, longitude, address, date FROM photos ORDER BY date DESC";
                PreparedStatement stmt = connection.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    RemotePhotoItem item = new RemotePhotoItem();
                    item.lat = rs.getDouble("latitude");
                    item.lon = rs.getDouble("longitude");
                    item.address = rs.getString("address");
                    item.date = rs.getString("date");
                    photos.add(item);
                }

                rs.close();
                stmt.close();
                connection.close();

                runOnUiThread(() -> adapter.setItems(photos));

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("MYSQL", "Erreur: " + e.getMessage());
            }
        }).start();
    }

    public static class RemotePhotoItem {
        double lat;
        double lon;
        String address;
        String date;
    }

    public static class RemotePhotoAdapter extends RecyclerView.Adapter<RemotePhotoAdapter.RemoteViewHolder> {

        private List<RemotePhotoItem> items = new ArrayList<>();

        public void setItems(List<RemotePhotoItem> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RemoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_remote_photo, parent, false);
            return new RemoteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RemoteViewHolder holder, int position) {
            RemotePhotoItem item = items.get(position);
            holder.textLocation.setText("Lat: " + item.lat + ", Lon: " + item.lon);
            holder.textAddress.setText(item.address);
            holder.textDate.setText(item.date);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class RemoteViewHolder extends RecyclerView.ViewHolder {
            TextView textLocation, textAddress, textDate;

            public RemoteViewHolder(@NonNull View itemView) {
                super(itemView);
                textLocation = itemView.findViewById(R.id.textLocation);
                textAddress = itemView.findViewById(R.id.textAddress);
                textDate = itemView.findViewById(R.id.textDate);
            }
        }
    }
}
