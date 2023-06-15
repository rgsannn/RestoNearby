package com.rgsannn.restonearby;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * 10120076
 * Rifqi Galih Nur Ikhsan
 * IF-2
 */

public class MapsFragment extends Fragment implements OnMapReadyCallback {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_CHECK_SETTINGS = 2;

    private GoogleMap googleMap;
    private FusedLocationProviderClient locationClient;
    private RequestQueue queue;
    private Geocoder geocoder;

    public MapsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.maps, container, false);

        MapView mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        locationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        queue = Volley.newRequestQueue(requireContext());
        geocoder = new Geocoder(requireContext(), Locale.getDefault());

        return view;
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Izin diperlukan untuk fitur lokasi", Toast.LENGTH_SHORT).show();
            requestLocationPermission();
            return;
        }
        googleMap.setMyLocationEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        fetchLastLocation();
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
    }

    private void fetchLastLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        double userLatitude = location.getLatitude();
                        double userLongitude = location.getLongitude();
                        LatLng userLatLng = new LatLng(userLatitude, userLongitude);

                        try {
                            List<Address> addresses = geocoder.getFromLocation(userLatitude, userLongitude, 1);
                            if (!addresses.isEmpty()) {
                                Address address = addresses.get(0);
                                String alamat = address.getAddressLine(0);
                                googleMap.addMarker(new MarkerOptions().position(userLatLng).title(alamat));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 12));

                        String apiKey = getString(R.string.google_maps_api_key);
                        String type = "tempat makan favorit terdekat";

                        Uri.Builder builder = new Uri.Builder();
                        builder.scheme("https")
                                .authority("maps.googleapis.com")
                                .appendPath("maps")
                                .appendPath("api")
                                .appendPath("place")
                                .appendPath("textsearch")
                                .appendPath("json")
                                .appendQueryParameter("query", type)
                                .appendQueryParameter("location", userLatitude + "," + userLongitude)
                                .appendQueryParameter("key", apiKey);
                        String url = builder.build().toString();

                        Executors.newSingleThreadExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                sendNearbyPlacesRequest(url);
                            }
                        });
                    }
                }
            });
        } else {
            requestLocationPermission();
        }
    }

    private void sendNearbyPlacesRequest(String url) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        handleNearbyPlacesResponse(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                });

        // Menambahkan request ke antrian
        queue.add(request);
    }

    private void handleNearbyPlacesResponse(JSONObject response) {
        try {
            JSONArray results = response.getJSONArray("results");
            List<JSONObject> sortedResults = new ArrayList<>();

            for (int i = 0; i < results.length(); i++) {
                sortedResults.add(results.getJSONObject(i));
            }

            Collections.sort(sortedResults, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject place1, JSONObject place2) {
                    try {
                        int rating1 = place1.getInt("user_ratings_total");
                        int rating2 = place2.getInt("user_ratings_total");
                        return Integer.compare(rating2, rating1);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            });

            for (int i = 0; i < Math.min(sortedResults.size(), 5); i++) {
                JSONObject place = sortedResults.get(i);
                JSONObject location = place.getJSONObject("geometry").getJSONObject("location");
                double lat = location.getDouble("lat");
                double lng = location.getDouble("lng");
                String name = place.getString("name");

                LatLng restaurantLatLng = new LatLng(lat, lng);
                googleMap.addMarker(new MarkerOptions()
                        .position(restaurantLatLng)
                        .title(name)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_location)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLastLocation();
            } else {
                showLocationPermissionDeniedDialog();
            }
        }
    }

    private void showLocationPermissionDeniedDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Izin Lokasi Diperlukan")
                .setMessage("Untuk menggunakan fitur ini, izinkan aplikasi untuk mengakses lokasi Anda. Pergi ke pengaturan sekarang untuk mengaktifkannya?")
                .setPositiveButton("Pengaturan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openAppSettings();
                    }
                })
                .setNegativeButton("Batal", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
        intent.setData(uri);
        startActivityForResult(intent, REQUEST_CHECK_SETTINGS);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CHECK_SETTINGS) {
            fetchLastLocation();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MapView mapView = requireView().findViewById(R.id.mapView);
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        MapView mapView = requireView().findViewById(R.id.mapView);
        mapView.onPause();
    }
}
