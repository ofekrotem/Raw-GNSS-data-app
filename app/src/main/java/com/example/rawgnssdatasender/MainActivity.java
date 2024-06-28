package com.example.rawgnssdatasender;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private LocationManager locationManager;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gson = new Gson();

        Log.d(TAG, "onCreate: Started");
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissions granted");
                startGnssLogging();
            } else {
                Log.d(TAG, "Permissions denied");
            }
        }
    }

    private void startGnssLogging() {
        Log.d(TAG, "startGnssLogging: Started");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission not granted");
            return;
        }

        locationManager.registerGnssMeasurementsCallback(new GnssMeasurementsEvent.Callback() {
            @Override
            public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
                Log.d(TAG, "GnssMeasurements received");
                for (GnssMeasurement measurement : event.getMeasurements()) {
                    sendRawDataToServer(measurement, event.getClock().getTimeNanos(), event.getClock().getFullBiasNanos(), event.getClock().getBiasNanos());
                }
            }

            @Override
            public void onStatusChanged(int status) {
                Log.d(TAG, "GnssMeasurements status changed: " + status);
            }
        });

        locationManager.registerGnssNavigationMessageCallback(new GnssNavigationMessage.Callback() {
            @Override
            public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
                Log.d(TAG, "GnssNavigationMessage received");
                sendNavigationMessageToServer(event);
            }

            @Override
            public void onStatusChanged(int status) {
                Log.d(TAG, "GnssNavigationMessage status changed: " + status);
            }
        });
    }

    private void sendRawDataToServer(GnssMeasurement measurement, long timeNanos, long fullBiasNanos, double biasNanos) {
        Log.d(TAG, "sendRawDataToServer: Started with measurement: " + measurement);
        String serverUrl = "http://10.0.0.7:2121/gnssdata";
        RequestQueue requestQueue = Volley.newRequestQueue(this);

        StringRequest stringRequest = new StringRequest(Request.Method.POST, serverUrl,
                response -> Log.d(TAG, "Response: " + response),
                error -> Log.d(TAG, "Error: " + error.toString())) {
            @Override
            public byte[] getBody() {
                Map<String, Object> measurementMap = new HashMap<>();
                measurementMap.put("svid", measurement.getSvid());
                measurementMap.put("constellationType", measurement.getConstellationType());
                measurementMap.put("timeOffsetNanos", measurement.getTimeOffsetNanos());
                measurementMap.put("state", measurement.getState());
                measurementMap.put("receivedSvTimeNanos", measurement.getReceivedSvTimeNanos());
                measurementMap.put("receivedSvTimeUncertaintyNanos", measurement.getReceivedSvTimeUncertaintyNanos());
                measurementMap.put("cn0DbHz", measurement.getCn0DbHz());
                measurementMap.put("pseudorangeRateMetersPerSecond", measurement.getPseudorangeRateMetersPerSecond());
                measurementMap.put("pseudorangeRateUncertaintyMetersPerSecond", measurement.getPseudorangeRateUncertaintyMetersPerSecond());
                measurementMap.put("accumulatedDeltaRangeState", measurement.getAccumulatedDeltaRangeState());
                measurementMap.put("accumulatedDeltaRangeMeters", measurement.getAccumulatedDeltaRangeMeters());
                measurementMap.put("accumulatedDeltaRangeUncertaintyMeters", measurement.getAccumulatedDeltaRangeUncertaintyMeters());
                measurementMap.put("carrierFrequencyHz", measurement.getCarrierFrequencyHz());
                measurementMap.put("multipathIndicator", measurement.getMultipathIndicator());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    measurementMap.put("codeType", measurement.getCodeType());
                }
                measurementMap.put("timeNanos", timeNanos);
                measurementMap.put("fullBiasNanos", fullBiasNanos);
                measurementMap.put("biasNanos", biasNanos);

                String measurementJson = gson.toJson(measurementMap);
                Log.d(TAG, "sendRawDataToServer: JSON measurement: " + measurementJson);
                return measurementJson.getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        requestQueue.add(stringRequest);
    }

    private void sendNavigationMessageToServer(GnssNavigationMessage message) {
        Log.d(TAG, "sendNavigationMessageToServer: Started with message: " + message);
        String serverUrl = "http://10.0.0.7:2121/gnssnavdata";
        RequestQueue requestQueue = Volley.newRequestQueue(this);

        StringRequest stringRequest = new StringRequest(Request.Method.POST, serverUrl,
                response -> Log.d(TAG, "Response: " + response),
                error -> Log.d(TAG, "Error: " + error.toString())) {
            @Override
            public byte[] getBody() {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("messageType", message.getType());
                messageMap.put("messageId", message.getMessageId());
                messageMap.put("subMessageId", message.getSubmessageId());
                messageMap.put("data", message.getData());

                String messageJson = gson.toJson(messageMap);
                Log.d(TAG, "sendNavigationMessageToServer: JSON message: " + messageJson);
                return messageJson.getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        requestQueue.add(stringRequest);
    }
}
