// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.activities;

import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger.NightModeable;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.control.AstronomerModel.Pointing;
import com.google.android.stardroid.control.ControllerGroup;
import com.google.android.stardroid.control.MagneticDeclinationCalculatorSwitcher;
import com.google.android.stardroid.kml.KmlManager;
import com.google.android.stardroid.layers.LayerManager;
import com.google.android.stardroid.renderer.RendererController;
import com.google.android.stardroid.renderer.SkyRenderer;
import com.google.android.stardroid.renderer.util.AbstractUpdateClosure;
import com.google.android.stardroid.search.SearchResult;
import com.google.android.stardroid.touch.DragRotateZoomGestureDetector;
import com.google.android.stardroid.touch.GestureInterpreter;
import com.google.android.stardroid.touch.MapMover;
import com.google.android.stardroid.units.GeocentricCoordinates;
import com.google.android.stardroid.units.Vector3;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MathUtil;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.OsVersions;
import com.google.android.stardroid.views.ButtonLayerView;
import com.google.android.stardroid.views.WidgetFader;
import com.google.android.stardroid.views.WidgetFader.Fadeable;

import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The main map-rendering Activity.
 */
public class DynamicStarMapActivity extends Activity implements OnSharedPreferenceChangeListener {
  private static final int TIME_DISPLAY_DELAY_MILLIS = 1000;

  /**
   * Passed to the renderer to get per-frame updates from the model.
   *
   * @author John Taylor
   */
  private static final class RendererModelUpdateClosure extends AbstractUpdateClosure {
    private RendererController rendererController;
    private AstronomerModel model;

    public RendererModelUpdateClosure(AstronomerModel model,
        RendererController rendererController) {
      this.model = model;
      this.rendererController = rendererController;
    }

    @Override
    public void run() {
      Pointing pointing = model.getPointing();
      float directionX = pointing.getLineOfSightX();
      float directionY = pointing.getLineOfSightY();
      float directionZ = pointing.getLineOfSightZ();

      float upX = pointing.getPerpendicularX();
      float upY = pointing.getPerpendicularY();
      float upZ = pointing.getPerpendicularZ();

      rendererController.queueSetViewOrientation(directionX, directionY, directionZ, upX, upY, upZ);

      Vector3 acceleration = model.getPhoneAcceleration();
      rendererController.queueTextAngle(MathUtil.atan2(-acceleration.x, -acceleration.y));
      rendererController.queueViewerUpDirection(model.getZenith().copy());

      float fieldOfView = model.getFieldOfView();
      rendererController.queueFieldOfView(fieldOfView);
    }
  }

  private static final String AUTO_MODE_PREF_KEY = "auto_mode";
  private static final String BUNDLE_TARGET_NAME = "target_name";
  private static final String BUNDLE_NIGHT_MODE = "night_mode";
  private static final String BUNDLE_X_TARGET = "bundle_x_target";
  private static final String BUNDLE_Y_TARGET = "bundle_y_target";
  private static final String BUNDLE_Z_TARGET = "bundle_z_target";
  private static final String BUNDLE_SEARCH_MODE = "bundle_search";
  private static final String SOUND_EFFECTS = "sound_effects";
  private static final int DELAY_BETWEEN_ZOOM_REPEATS_MILLIS = 100;
  private static final float ROTATION_SPEED = 10;
  private static final String TAG = MiscUtil.getTag(DynamicStarMapActivity.class);
  // Preference that keeps track of whether or not the user accepted the ToS
  public static final String READ_TOS_PREF = "read_tos";
  private ImageButton cancelSearchButton;
  private ControllerGroup controller;
  private GestureDetector gestureDetector;
  private AstronomerModel model;
  private RendererController rendererController;
  private boolean nightMode = false;
  private boolean searchMode = false;
  private GeocentricCoordinates searchTarget = GeocentricCoordinates.getInstance(0, 0);
  private SharedPreferences sharedPreferences;
  private GLSurfaceView skyView;
  private PowerManager.WakeLock wakeLock;
  private String searchTargetName;
  private LayerManager layerManager;
  // TODO(widdows): Figure out if we should break out the
  // time dialog and time player into separate activities.
  private View timePlayerUI;
  private DialogFactory dialogFactory;
  private MediaPlayer timeTravelNoise;
  private MediaPlayer timeTravelBackNoise;
  KmlManager kmlManager;
  private Handler handler = new Handler();
  // A list of runnables to post on the handler when we resume.
  private List<Runnable> runnables = new ArrayList<Runnable>();

  // We need to maintain references to these objects to keep them from
  // getting gc'd.
  @SuppressWarnings("unused")
  private MagneticDeclinationCalculatorSwitcher magneticSwitcher;

  private DragRotateZoomGestureDetector dragZoomRotateDetector;
  private Animation flashAnimation;
  private ActivityLightLevelManager activityLightLevelManager;
  private long sessionStartTime;

  @Override
  public void onCreate(Bundle icicle) {
    Log.d(TAG, "onCreate at " + System.currentTimeMillis());
    super.onCreate(icicle);
    timeTravelNoise = MediaPlayer.create(this, R.raw.timetravel);
    timeTravelBackNoise = MediaPlayer.create(this, R.raw.timetravelback);
    flashAnimation = AnimationUtils.loadAnimation(this, R.anim.timetravelflash);
    dialogFactory = new DialogFactory(this);
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);

    boolean eulaConfirmed = sharedPreferences.getBoolean(READ_TOS_PREF, false);
    if (!eulaConfirmed) {
      showDialog(DialogFactory.DIALOG_EULA_WITH_BUTTONS);
    }

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    model = StardroidApplication.getModel();
    layerManager = StardroidApplication.getLayerManager(getAssets(),
                                                        sharedPreferences,
                                                        getResources(),
                                                        this);
    initializeModelViewController();
    // We want to reset to auto mode on every restart, as users seem to get
    // stuck in manual mode and can't find their way out.
    // TODO(johntaylor): this is a bit of an abuse of the prefs system, but
    // the button we use is wired into the preferences system.  Should probably
    // change this to a use a different mechanism.
    sharedPreferences.edit().putBoolean(AUTO_MODE_PREF_KEY, true).commit();
    setAutoMode(true);

    // Search related
    setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

    kmlManager = new KmlManager(layerManager);
    ActivityLightLevelChanger activityLightLevelChanger = new ActivityLightLevelChanger(this,
        new NightModeable() {
          @Override
          public void setNightMode(boolean nightMode) {
            DynamicStarMapActivity.this.rendererController.queueNightVisionMode(nightMode);
          }});
    activityLightLevelManager = new ActivityLightLevelManager(activityLightLevelChanger,
                                                              sharedPreferences);

    final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);

    // Were we started as the result of a search?
    Intent intent = getIntent();
    Log.d(TAG, "Intent received: " + intent);
    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
      Log.d(TAG, "Started as a result of a search");
      doSearchWithIntent(intent);
    }
    Log.d(TAG, "-onCreate at " + System.currentTimeMillis());
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflator = getMenuInflater();
    inflator.inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "DynamicStarMap onDestroy");
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case (KeyEvent.KEYCODE_DPAD_LEFT):
        Log.d(TAG, "Key left");
        controller.rotate(-10.0f);
        break;
      case (KeyEvent.KEYCODE_DPAD_RIGHT):
        Log.d(TAG, "Key right");
        controller.rotate(10.0f);
        break;
      case (KeyEvent.KEYCODE_BACK):
        // If we're in search mode when the user presses 'back' the natural
        // thing is to back out of search.
        Log.d(TAG, "In search mode " + searchMode);
        if (searchMode) {
          cancelSearch();
          break;
        }
      default:
        Log.d(TAG, "Key: " + event);
        return super.onKeyDown(keyCode, event);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case R.id.menu_item_search:
        Log.d(TAG, "Search");
        Analytics.getInstance(this).trackEvent(Analytics.USER_ACTION_CATEGORY,
            Analytics.MENU_ITEM, Analytics.SEARCH_REQUESTED_LABEL, 1);
        onSearchRequested();
        break;
      case R.id.menu_item_settings:
        Log.d(TAG, "Settings");
        Analytics.getInstance(this).trackEvent(Analytics.USER_ACTION_CATEGORY,
            Analytics.MENU_ITEM, Analytics.SETTINGS_OPENED_LABEL, 1);
        startActivity(new Intent(this, EditSettingsActivity.class));
        break;
      case R.id.menu_item_help:
        Log.d(TAG, "Help");
        Analytics.getInstance(this).trackEvent(Analytics.USER_ACTION_CATEGORY,
            Analytics.MENU_ITEM, Analytics.HELP_OPENED_LABEL, 1);
        showDialog(DialogFactory.DIALOG_ID_HELP);
        break;
      case R.id.menu_item_dim:
        Log.d(TAG, "Toggling nightmode");
        nightMode = !nightMode;
        sharedPreferences.edit().putString(ActivityLightLevelManager.LIGHT_MODE_KEY,
            nightMode ? "NIGHT" : "DAY").commit();
        Analytics.getInstance(this).trackEvent(Analytics.USER_ACTION_CATEGORY,
            Analytics.MENU_ITEM, Analytics.TOGGLED_NIGHT_MODE_LABEL, nightMode ? 1 : 0);
        break;
      case R.id.menu_item_time:
        Log.d(TAG, "Starting Time Dialog from menu");
        Analytics.getInstance(this).trackEvent(Analytics.USER_ACTION_CATEGORY,
            Analytics.MENU_ITEM, Analytics.TIME_TRAVEL_OPENED_LABEL, 1);
        if (!timePlayerUI.isShown()) {
          Log.d(TAG, "Resetting time in time travel dialog.");
          controller.goTimeTravel(new Date());
        } else {
          Log.d(TAG, "Resuming current time travel dialog.");
        }
        showDialog(DialogFactory.DIALOG_ID_TIME_TRAVEL);
        break;
      case R.id.menu_item_gallery:
        Log.d(TAG, "Loading gallery");
        Analytics.getInstance(this).trackEvent(Analytics.USER_ACTION_CATEGORY,
            Analytics.MENU_ITEM, Analytics.GALLERY_OPENED_LABEL, 1);
        startActivity(new Intent(this, ImageGalleryActivity.class));
        break;
      case R.id.menu_item_tos:
        Log.d(TAG, "Loading ToS");
        Analytics.getInstance(this).trackEvent(Analytics.USER_ACTION_CATEGORY,
            Analytics.MENU_ITEM, Analytics.TOS_OPENED_LABEL, 1);
        showDialog(DialogFactory.DIALOG_EULA_NO_BUTTONS);
        break;
      default:
        Log.e(TAG, "Unwired-up menu item");
        return false;
    }
    return true;
  }

  @Override
  public void onStart() {
    super.onStart();
    Analytics.getInstance(this).trackPageView(Analytics.DYNAMIC_STARMAP_ACTIVITY);
    sessionStartTime = System.currentTimeMillis();
  }

  private enum SessionBucketLength {
    LESS_THAN_TEN_SECS(10), TEN_SECS_TO_THIRTY_SECS(30),
    THIRTY_SECS_TO_ONE_MIN(60), ONE_MIN_TO_FIVE_MINS(300),
    MORE_THAN_FIVE_MINS(Integer.MAX_VALUE);
    private int seconds;
    private SessionBucketLength(int seconds) {
      this.seconds = seconds;
    }
  }

  private SessionBucketLength getSessionLengthBucket(int sessionLengthSeconds) {
    for (SessionBucketLength bucket : SessionBucketLength.values()) {
      if (sessionLengthSeconds < bucket.seconds) {
        return bucket;
      }
    }
    Log.e(TAG, "Programming error - should not get here");
    return SessionBucketLength.MORE_THAN_FIVE_MINS;
  }

  @Override
  public void onStop() {
    super.onStart();
    // Define a session as being the time between the main activity being in
    // the foreground and pushed back.  Note that this will mean that sessions
    // do get interrupted by (e.g.) loading preference or help screens.
    int sessionLengthSeconds = (int) ((
        System.currentTimeMillis() - sessionStartTime) / 1000);
    SessionBucketLength bucket = getSessionLengthBucket(sessionLengthSeconds);
    Analytics.getInstance(this).trackEvent(
        Analytics.GENERAL_CATEGORY, Analytics.SESSION_LENGTH_BUCKET,
        bucket.toString(), sessionLengthSeconds);
  }

  @Override
  public void onResume() {
    Log.d(TAG, "onResume at " + System.currentTimeMillis());
    super.onResume();
    Log.i(TAG, "Resuming");
    wakeLock.acquire();
    Log.i(TAG, "Starting view");
    skyView.onResume();
    Log.i(TAG, "Starting controller");
    controller.start();
    activityLightLevelManager.onResume();
    for (Runnable runnable : runnables) {
      handler.post(runnable);
    }
    Log.d(TAG, "-onResume at " + System.currentTimeMillis());
  }

  public void setTimeTravelMode(Date newTime) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy.MM.dd G  HH:mm:ss z");
    Toast.makeText(this,
                   String.format(getString(R.string.time_travel_start_message_alt),
                                 dateFormatter.format(newTime)),
                   Toast.LENGTH_LONG).show();
    if (sharedPreferences.getBoolean(SOUND_EFFECTS, true)) {
      try {
        timeTravelNoise.start();
      } catch (IllegalStateException e) {
        Log.e(TAG, "Exception trying to play time travel sound", e);
        // It's not the end of the world - carry on.
      }
    }

    Log.d(TAG, "Showing TimePlayer UI.");
    timePlayerUI.setVisibility(View.VISIBLE);
    timePlayerUI.requestFocus();
    flashTheScreen();
    controller.goTimeTravel(newTime);
  }

  public void setNormalTimeModel() {
    if (sharedPreferences.getBoolean(SOUND_EFFECTS, true)) {
      try {
        timeTravelBackNoise.start();
      } catch (IllegalStateException e) {
        Log.e(TAG, "Exception trying to play return time travel sound", e);
        // It's not the end of the world - carry on.
      }
    }
    flashTheScreen();
    controller.useRealTime();
    Toast.makeText(this,
                   R.string.time_travel_close_message,
                   Toast.LENGTH_SHORT).show();
    Log.d(TAG, "Leaving Time Travel mode.");
    timePlayerUI.setVisibility(View.GONE);
  }

  private void flashTheScreen() {
    final View view = findViewById(R.id.view_mask);
    // We don't need to set it invisible again - the end of the
    // animation will see to that.
    // TODO(johntaylor): check if setting it to GONE will bring
    // performance benefits.
    view.setVisibility(View.VISIBLE);
    view.startAnimation(flashAnimation);
  }

  @Override
  public void onPause() {
    Log.d(TAG, "DynamicStarMap onPause");
    super.onPause();
    for (Runnable runnable : runnables) {
      handler.removeCallbacks(runnable);
    }
    activityLightLevelManager.onPause();
    controller.stop();
    skyView.onPause();
    wakeLock.release();
    // Debug.stopMethodTracing();
    Log.d(TAG, "DynamicStarMap -onPause");
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Log.d(TAG, "Preferences changed: key=" + key);

    if (!key.equals(AUTO_MODE_PREF_KEY)) return;
    final boolean mode = sharedPreferences.getBoolean(key, true);
    Log.d(TAG, "Automode is set to " + mode);
    if (!mode) {
      Log.d(TAG, "Switching to manual control");
      Toast.makeText(DynamicStarMapActivity.this, R.string.set_manual, Toast.LENGTH_SHORT).show();
    } else {
      Log.d(TAG, "Switching to sensor control");
      Toast.makeText(DynamicStarMapActivity.this, R.string.set_auto, Toast.LENGTH_SHORT).show();
    }
    setAutoMode(mode);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // Log.d(TAG, "Touch event " + event);
    // Either of the following detectors can absorb the event, but one
    // must not hide it from the other
    boolean eventAbsorbed = false;
    if (gestureDetector.onTouchEvent(event)) {
      eventAbsorbed = true;
    }
    if (dragZoomRotateDetector.onTouchEvent(event)) {
      eventAbsorbed = true;
    }
    return eventAbsorbed;
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    Log.d(TAG, "Trackball motion " + event);
    controller.rotate(event.getX() * ROTATION_SPEED);
    return true;
  }

  private void doSearchWithIntent(Intent searchIntent) {
    // If we're already in search mode, cancel it.
    if (searchMode) {
      cancelSearch();
    }
    Log.d(TAG, "Performing Search");
    final String queryString = searchIntent.getStringExtra(SearchManager.QUERY);
    searchMode = true;
    Log.d(TAG, "Query string " + queryString);
    List<SearchResult> results = layerManager.searchByObjectName(queryString);
    // Log the search, with value "1" for successful searches
    Analytics.getInstance(this).trackEvent(
        Analytics.USER_ACTION_CATEGORY, Analytics.SEARCH, "search:" + queryString,
        results.size() > 0 ? 1 : 0);
    if (results.size() == 0) {
      Log.d(TAG, "No results returned");
      showDialog(DialogFactory.DIALOG_ID_NO_SEARCH_RESULTS);
    } else if (results.size() > 1) {
      Log.d(TAG, "Multiple results returned");
      dialogFactory.showUserChooseResultDialog(results);
    } else {
      Log.d(TAG, "One result returned.");
      final SearchResult result = results.get(0);
      activateSearchTarget(result.coords, result.capitalizedName);
    }
  }

  private void initializeModelViewController() {
    Log.i(TAG, "Initializing Model, View and Controller @ " + System.currentTimeMillis());
    setContentView(R.layout.skyrenderer);
    OsVersions.setSystemStatusBarVisible(findViewById(R.id.main_sky_view_root), false);
    skyView = (GLSurfaceView) findViewById(R.id.skyrenderer_view);
    // We don't want a depth buffer.
    skyView.setEGLConfigChooser(false);
    SkyRenderer renderer = new SkyRenderer(getResources());
    skyView.setRenderer(renderer);

    rendererController = new RendererController(renderer, skyView);
    // The renderer will now call back every frame to get model updates.
    rendererController.addUpdateClosure(
        new RendererModelUpdateClosure(model, rendererController));

    Log.i(TAG, "Setting layers @ " + System.currentTimeMillis());
    layerManager.registerWithRenderer(rendererController);
    Log.i(TAG, "Set up controllers @ " + System.currentTimeMillis());
    controller = ControllerGroup.createControllerGroup(this);
    controller.setModel(model);
    wireUpScreenControls(); // TODO(johntaylor) move these?
    magneticSwitcher = new MagneticDeclinationCalculatorSwitcher(model, sharedPreferences);
    wireUpTimePlayer();  // TODO(widdows) move these?
  }

  private void setAutoMode(boolean auto) {
    controller.setAutoMode(auto);
  }

  private void wireUpScreenControls() {
    cancelSearchButton = (ImageButton) findViewById(R.id.cancel_search_button);
    // TODO(johntaylor): move to set this in the XML once we don't support 1.5
    cancelSearchButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        cancelSearch();
      }
    });

    final ZoomControls zooms = (ZoomControls) findViewById(R.id.zoom_control);
    final WidgetFader zoomControlFader = new WidgetFader(new Fadeable() {
      @Override
      public void hide() {
        zooms.hide();
      }

      @Override
      public void show() {
        zooms.show();
      }
    });
    zooms.setOnZoomInClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        controller.zoomIn();
        zoomControlFader.keepActive();
      }
    });
    zooms.setOnZoomOutClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        controller.zoomOut();
        zoomControlFader.keepActive();
      }
    });
    zooms.setZoomSpeed(DELAY_BETWEEN_ZOOM_REPEATS_MILLIS);
    zooms.hide();
    final ButtonLayerView providerButtons = (ButtonLayerView) findViewById(R.id.layer_buttons_control);
    final WidgetFader layerControlFader = new WidgetFader(providerButtons, 2500);
    providerButtons.hide();
    final int numChildren = providerButtons.getChildCount();
    for (int i = 0; i < numChildren; ++i) {
      final ImageButton button = (ImageButton) providerButtons.getChildAt(i);
      button.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          layerControlFader.keepActive();
        }
      });
    }
    final ButtonLayerView manualButtonLayer = (ButtonLayerView) findViewById(R.id.layer_manual_auto_toggle);
    final WidgetFader manualControlFader = new WidgetFader(manualButtonLayer);
    manualButtonLayer.hide();
    final ImageButton manualAuto = (ImageButton) findViewById(R.id.manual_auto_toggle);
    manualAuto.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        manualControlFader.keepActive();
      }
    });

    MapMover mapMover = new MapMover(model, controller, this, sharedPreferences);
    gestureDetector = new GestureDetector(new GestureInterpreter(
        new WidgetFader[] {manualControlFader, layerControlFader, zoomControlFader},
        mapMover));
    dragZoomRotateDetector = new DragRotateZoomGestureDetector(mapMover);
  }

  private void cancelSearch() {
    View searchControlBar = findViewById(R.id.search_control_bar);
    searchControlBar.setVisibility(View.INVISIBLE);
    rendererController.queueDisableSearchOverlay();
    searchMode = false;
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    return dialogFactory.onCreateDialog(id);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.d(TAG, "New Intent received " + intent);
    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
      doSearchWithIntent(intent);
    }
  }

  @Override
  protected void onRestoreInstanceState(Bundle icicle) {
    Log.d(TAG, "DynamicStarMap onRestoreInstanceState");
    super.onRestoreInstanceState(icicle);
    if (icicle == null) return;
    searchMode = icicle.getBoolean(BUNDLE_SEARCH_MODE);
    float x = icicle.getFloat(BUNDLE_X_TARGET);
    float y = icicle.getFloat(BUNDLE_Y_TARGET);
    float z = icicle.getFloat(BUNDLE_Z_TARGET);
    searchTarget = new GeocentricCoordinates(x, y, z);
    searchTargetName = icicle.getString(BUNDLE_TARGET_NAME);
    if (searchMode) {
      Log.d(TAG, "Searching for target " + searchTargetName + " at target=" + searchTarget);
      rendererController.queueEnableSearchOverlay(searchTarget, searchTargetName);
      cancelSearchButton.setVisibility(View.VISIBLE);
    }
    nightMode = icicle.getBoolean(BUNDLE_NIGHT_MODE, false);
  }

  @Override
  protected void onSaveInstanceState(Bundle icicle) {
    Log.d(TAG, "DynamicStarMap onSaveInstanceState");
    icicle.putBoolean(BUNDLE_SEARCH_MODE, searchMode);
    icicle.putFloat(BUNDLE_X_TARGET, searchTarget.x);
    icicle.putFloat(BUNDLE_Y_TARGET, searchTarget.y);
    icicle.putFloat(BUNDLE_Z_TARGET, searchTarget.z);
    icicle.putString(BUNDLE_TARGET_NAME, searchTargetName);
    icicle.putBoolean(BUNDLE_NIGHT_MODE, nightMode);
    super.onSaveInstanceState(icicle);
  }

  void activateSearchTarget(GeocentricCoordinates target, final String searchTerm) {
    Log.d(TAG, "Item " + searchTerm + " selected");
    // Store these for later.
    searchTarget = target;
    searchTargetName = searchTerm;
    Log.d(TAG, "Searching for target=" + target);
    rendererController.queueViewerUpDirection(model.getZenith().copy());
    rendererController.queueEnableSearchOverlay(target.copy(), searchTerm);
    boolean autoMode = sharedPreferences.getBoolean(AUTO_MODE_PREF_KEY, true);
    if (!autoMode) {
      controller.teleport(target);
    }

    TextView searchPromptText = (TextView) findViewById(R.id.search_status_label);
    searchPromptText.setText(
        String.format("%s %s", getString(R.string.search_target_looking_message), searchTerm));
    View searchControlBar = findViewById(R.id.search_control_bar);
    searchControlBar.setVisibility(View.VISIBLE);
  }

  /**
   * Creates and wire up all time player controls.
   */
  private void wireUpTimePlayer() {
    Log.d(TAG, "Initializing TimePlayer UI.");
    timePlayerUI = findViewById(R.id.time_player_view);
    ImageButton timePlayerCancelButton = (ImageButton) findViewById(R.id.time_player_close);
    ImageButton timePlayerBackwardsButton = (ImageButton) findViewById(
        R.id.time_player_play_backwards);
    ImageButton timePlayerStopButton = (ImageButton) findViewById(R.id.time_player_play_stop);
    ImageButton timePlayerForwardsButton = (ImageButton) findViewById(
        R.id.time_player_play_forwards);
    final TextView timeTravelSpeedLabel = (TextView) findViewById(R.id.time_travel_speed_label);

    timePlayerCancelButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Heard time player close click.");
        setNormalTimeModel();
      }
    });
    timePlayerBackwardsButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Heard time player play backwards click.");
        controller.decelerateTimeTravel();
        timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
      }
    });
    timePlayerStopButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Heard time player play stop click.");
        controller.pauseTime();
        timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
      }
    });
    timePlayerForwardsButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Heard time player play forwards click.");
        controller.accelerateTimeTravel();
        timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
      }
    });

    Runnable displayUpdater = new Runnable() {
      private TextView timeTravelTimeReadout = (TextView) findViewById(
          R.id.time_travel_time_readout);
      private TextView timeTravelStatusLabel = (TextView) findViewById(
          R.id.time_travel_status_label);
      private TextView timeTravelSpeedLabel = (TextView) findViewById(
          R.id.time_travel_speed_label);
      private final SimpleDateFormat dateFormatter = new SimpleDateFormat(
          "yyyy.MM.dd G  HH:mm:ss z");
      private Date date = new Date();
      @Override
      public void run() {
        long time = model.getTimeMillis();
        date.setTime(time);
        timeTravelTimeReadout.setText(dateFormatter.format(date));
        if (time > System.currentTimeMillis()) {
          timeTravelStatusLabel.setText(R.string.time_travel_label_future);
        } else {
          timeTravelStatusLabel.setText(R.string.time_travel_label_past);
        }
        timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
        handler.postDelayed(this, TIME_DISPLAY_DELAY_MILLIS);
      }
    };
    runnables.add(displayUpdater);
  }

  public AstronomerModel getModel() {
    return model;
  }
}
