package com.google.android.stardroid.activities;

import static com.google.android.stardroid.math.CoordinateManipulationsKt.getDecOfUnitGeocentricVector;
import static com.google.android.stardroid.math.CoordinateManipulationsKt.getRaOfUnitGeocentricVector;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.activities.util.SensorAccuracyDecoder;
import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.control.LocationController;
import com.google.android.stardroid.databinding.ActivityDiagnosticBinding;
import com.google.android.stardroid.math.LatLong;
import com.google.android.stardroid.math.Vector3;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class DiagnosticActivity extends AppCompatInjectableActivity implements SensorEventListener {
  private static final String TAG = MiscUtil.getTag(DiagnosticActivity.class);
  private static final int UPDATE_PERIOD_MILLIS = 500;

  @Inject Analytics analytics;
  @Inject StardroidApplication app;
  @Inject @Nullable SensorManager sensorManager;
  @Inject @Nullable ConnectivityManager connectivityManager;
  @Inject @Nullable LocationManager locationManager;
  @Inject LocationController locationController;
  @Inject AstronomerModel model;
  @Inject Handler handler;
  @Inject SensorAccuracyDecoder sensorAccuracyDecoder;

  private Sensor accelSensor;
  private Sensor magSensor;
  private Sensor gyroSensor;
  private Sensor rotationVectorSensor;
  private Sensor lightSensor;
  private ActivityDiagnosticBinding binding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DaggerDiagnosticActivityComponent.builder().applicationComponent(
      getApplicationComponent()).diagnosticActivityModule(new DiagnosticActivityModule(this))
          .build().inject(this);
    binding = ActivityDiagnosticBinding.inflate(this.getLayoutInflater());
    setContentView(binding.getRoot());
  }

  @Override
  public void onStart() {
    super.onStart();

    binding.diagnosePhoneTxt.setText(Build.MODEL + " (" + Build.HARDWARE + ") " +
        Locale.getDefault().getLanguage());
    String androidVersion = String.format(Build.VERSION.RELEASE + " (%d)", Build.VERSION.SDK_INT);
    binding.diagnoseAndroidVersionTxt.setText(androidVersion);

    String skyMapVersion = String.format(
        app.getVersionName() + " (%d)", app.getVersion());
    binding.diagnoseSkymapVersionTxt.setText(skyMapVersion);
  }

  private boolean continueUpdates;

  @Override
  public void onResume() {
    super.onResume();
    onResumeSensors();
    continueUpdates = true;
    handler.post(new Runnable() {
      public void run() {
        updateLocation();
        updateModel();
        updateNetwork();
        if (continueUpdates) {
          handler.postDelayed(this, UPDATE_PERIOD_MILLIS);
        }
      }
    });
  }

  private void onResumeSensors() {
    if (sensorManager == null) {
      return;
    }
    accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    int absentSensorColor = getResources().getColor(R.color.absent_sensor);
    if (accelSensor == null) {
      binding.diagnoseAccelerometerValuesTxt.setTextColor(absentSensorColor);
    } else {
      sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
    magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    if (magSensor == null) {
      binding.diagnoseCompassValuesTxt.setTextColor(absentSensorColor);
    } else {
      sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
    gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    if (gyroSensor == null) {
      binding.diagnoseGyroValuesTxt.setTextColor(absentSensorColor);
    } else {
      sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
    rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    if (rotationVectorSensor == null) {
      binding.diagnoseRotationValuesTxt.setTextColor(absentSensorColor);
    } else {
      sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
    lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
    if (lightSensor == null) {
      binding.diagnoseLightValuesTxt.setTextColor(absentSensorColor);
    } else {
      sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
    }
  }

  private void updateLocation() {
    // TODO(johntaylor): add other things like number of satellites and status
    String gpsStatusMessage;
    try {
      LocationProvider gps = locationManager.getProvider(LocationManager.GPS_PROVIDER);
      boolean gpsStatus = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
      if (gps == null) {
        gpsStatusMessage = getString(R.string.no_gps);
      } else {
        gpsStatusMessage = gpsStatus ? getString(R.string.enabled) : getString(R.string.disabled);
      }
    } catch (SecurityException ex) {
      gpsStatusMessage = getString(R.string.permission_disabled);
    }
    binding.diagnoseGpsStatusTxt.setText(gpsStatusMessage);
    LatLong currentLocation = locationController.getCurrentLocation();
    String locationMessage = currentLocation.getLatitude() + ", " + currentLocation.getLongitude();
    // Current provider not working    + " (" + locationController.getCurrentProvider() + ")";
    binding.diagnoseLocationTxt.setText(locationMessage);
  }

  private void updateModel() {
    float magCorrection = model.getMagneticCorrection();
    String text = Math.abs(magCorrection) + " " + (magCorrection > 0
        ? getString(R.string.east) : getString(R.string.west)) + " "
        + getString(R.string.degrees);
    binding.diagnoseMagneticCorrectionTxt.setText(text);
    AstronomerModel.Pointing pointing = model.getPointing();
    Vector3 lineOfSight = pointing.getLineOfSight();
    binding.diagnosePointingTxt.setText(getDegreeInHour(getRaOfUnitGeocentricVector(lineOfSight)) + ", " + getDecOfUnitGeocentricVector(lineOfSight));
    Date nowTime = model.getTime();
    SimpleDateFormat dateFormatUtc = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
    dateFormatUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat dateFormatLocal = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");

    binding.diagnoseUtcDatetimeTxt.setText(dateFormatUtc.format(nowTime));
    binding.diagnoseLocalDatetimeTxt.setText(dateFormatLocal.format(nowTime));
  }

  @Override
  public void onPause() {
    super.onPause();
    continueUpdates = false;
    sensorManager.unregisterListener(this);
  }

  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    knownSensorAccuracies.add(sensor);
    Log.d(TAG, "set size" + knownSensorAccuracies.size());
    TextView sensorView;
    if (sensor == accelSensor) {
      sensorView = binding.diagnoseAccelerometerValuesTxt;
    } else if (sensor == magSensor) {
      sensorView = binding.diagnoseCompassValuesTxt;
    } else if (sensor == gyroSensor) {
      sensorView = binding.diagnoseGyroValuesTxt;
    } else if (sensor == rotationVectorSensor) {
      sensorView = binding.diagnoseRotationValuesTxt;
    } else if (sensor == lightSensor) {
      sensorView = binding.diagnoseLightValuesTxt;
    } else {
      Log.e(TAG, "Receiving accuracy change for unknown sensor " + sensor);
      return;
    }
    sensorView.setTextColor(sensorAccuracyDecoder.getColorForAccuracy(accuracy));
  }

  private Set<Sensor> knownSensorAccuracies = new HashSet<>();
  
  public void onSensorChanged(SensorEvent event) {
    Sensor sensor = event.sensor;
    if (!knownSensorAccuracies.contains(sensor)) {
      onAccuracyChanged(sensor, event.accuracy);
    }
    TextView valuesView;
    if (sensor == accelSensor) {
      valuesView = binding.diagnoseAccelerometerValuesTxt;
    } else if (sensor == magSensor) {
      valuesView = binding.diagnoseCompassValuesTxt;
    } else if (sensor == gyroSensor) {
      valuesView = binding.diagnoseGyroValuesTxt;
    } else if (sensor == rotationVectorSensor) {
      valuesView = binding.diagnoseRotationValuesTxt;
    } else if (sensor == lightSensor) {
      valuesView = binding.diagnoseLightValuesTxt;
    } else {
      Log.e(TAG, "Receiving values for unknown sensor " + sensor);
      return;
    }
    float[] values = event.values;
    setArrayValuesInUi(valuesView, values);

    // Something special for rotation sensor - convert to a matrix.
    if (sensor == rotationVectorSensor) {
      float[] matrix = new float[9];
      SensorManager.getRotationMatrixFromVector(matrix, event.values);
      for (int row = 0; row < 3; ++row) {
        switch(row) {
          case 0:
            valuesView = binding.diagnoseRotationMatrixRow1Txt;
            break;
          case 1:
            valuesView = binding.diagnoseRotationMatrixRow2Txt;
            break;
          case 2:
          default:
            valuesView = binding.diagnoseRotationMatrixRow3Txt;
        }
        float[] rowValues = new float[3];
        System.arraycopy(matrix, row * 3, rowValues, 0, 3);
        setArrayValuesInUi(valuesView, rowValues);
      }
    }
  }

  private void setArrayValuesInUi(TextView valuesView, float[] values) {
    StringBuilder valuesText = new StringBuilder();
    for (float value : values) {
      valuesText.append(String.format("%.2f", value));
      valuesText.append(',');
    }
    valuesText.setLength(valuesText.length() - 1);
    valuesView.setText(valuesText.toString());
  }

  private void updateNetwork() {
    NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
    boolean isConnected = activeNetwork != null &&
        activeNetwork.isConnectedOrConnecting();
    String message = isConnected ? getString(R.string.connected) : getString(R.string.disconnected);
    if (isConnected) {
      if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
        message += getString(R.string.wifi);
      }
      if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
        message += getString(R.string.cell_network);
      }
    }
    binding.diagnoseNetworkStatusTxt.setText(message);
  }


  private String getDegreeInHour(float deg) {
    int h = (int) deg / 15;
    int m = (int) ((deg / 15 - h) * 60);
    int s = (int) ((((deg / 15 - h) * 60) - m) * 60);
    return h + "h " + m + "m " + s + "s ";
  }
}
