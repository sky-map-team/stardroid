package com.google.android.stardroid.activities;

import android.app.Activity;
import android.os.Bundle;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.util.Analytics;

import javax.inject.Inject;

public class DiagnosticActivity extends Activity {

  @Inject
  Analytics analytics;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((StardroidApplication) getApplication()).getApplicationComponent().inject(this);
    setContentView(R.layout.activity_diagnostic);
  }

  @Override
  public void onStart() {
    super.onStart();
    analytics.trackPageView(Analytics.DIAGNOSTICS_ACTIVITY);
  }
}
