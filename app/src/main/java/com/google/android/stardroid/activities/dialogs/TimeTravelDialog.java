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

package com.google.android.stardroid.activities.dialogs;

import static com.google.android.stardroid.math.AstronomyKt.getNextFullMoon;
import static com.google.android.stardroid.math.AstronomyKt.getNextNewMoon;
import static com.google.android.stardroid.math.TimeUtilsKt.normalizeHours;

import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.activities.util.NightModeHelper;
import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.ephemeris.SolarSystemBody;
import com.google.android.stardroid.space.CelestialObject;
import com.google.android.stardroid.space.Universe;
import com.google.android.stardroid.util.MiscUtil;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Implementation of the time travel dialog.
 *
 * @author Dominic Widdows
 * @author John Taylor
 */
public class TimeTravelDialog extends Dialog {
  private static final String TAG = MiscUtil.getTag(TimeTravelDialog.class);
  private static final int MIN_CLICK_TIME = 1000;
  private boolean isNight = false;
  private Spinner popularDatesMenu;
  private ArrayAdapter<String> popularDatesAdapter;
  private TextView dateTimeReadout;
  private DynamicStarMapActivity parentActivity;
  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
  // This is the date we will apply to the controller when the user hits go.
  private Calendar calendar = Calendar.getInstance();
  private AstronomerModel model;
  private long lastClickTime = 0;
  private int currentSearchTargetRes = 0;  // 0 = no search target
  private boolean userHasModifiedTime = false;
  private Button goButton;

  public TimeTravelDialog(final DynamicStarMapActivity parentActivity,
                          final AstronomerModel model) {
    super(parentActivity);
    this.parentActivity = parentActivity;
    this.model = model;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setContentView(R.layout.time_dialog);
    // Assumes that the dialog's title should be the same as the menu option.
    setTitle(R.string.menu_time);
    // Capture our View elements
    dateTimeReadout = (TextView) findViewById(R.id.dateDisplay);
    // Capture and wire up the buttons
    Button changeDateButton = (Button) findViewById(R.id.pickDate);
    changeDateButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          if (SystemClock.elapsedRealtime() - lastClickTime < MIN_CLICK_TIME) return;
          lastClickTime = SystemClock.elapsedRealtime();
          // Snap spinner back to hint so it's clear the custom date overrides any event.
          popularDatesMenu.setSelection(0);
          currentSearchTargetRes = 0;
          createDatePicker().show();
        }
      });

    Button changeTimeButton = (Button) findViewById(R.id.pickTime);
    changeTimeButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          if (SystemClock.elapsedRealtime() - lastClickTime < MIN_CLICK_TIME) return;
          lastClickTime = SystemClock.elapsedRealtime();
          // Snap spinner back to hint so it's clear the custom time overrides any event.
          popularDatesMenu.setSelection(0);
          currentSearchTargetRes = 0;
          createTimePicker().show();
        }
      });

    goButton = (Button) findViewById(R.id.timeTravelGo);
    goButton.setText(R.string.start_from_now);
    goButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          parentActivity.setTimeTravelMode(calendar.getTime(), currentSearchTargetRes);
          dismiss();
        }
      });

    Button cancelButton = (Button) findViewById(R.id.timeTravelCancel);
    cancelButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          dismiss();
        }
      });

    popularDatesMenu = (Spinner) findViewById(R.id.popular_dates_spinner);
    popularDatesAdapter = buildEventAdapter(getContext());
    popularDatesMenu.setAdapter(popularDatesAdapter);
    popularDatesMenu.setSelection(0);
    popularDatesMenu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position > 0) {
          applyPopularEvent(position);
        }
      }
      @Override
      public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
      }
    });
    // Start by initializing ourselves to 'now'.  Note that this is the value
    // the first time the dialog is shown.  Thereafter it will remember the
    // last value set.
    calendar.setTime(new Date());
    updateDisplay();
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Reset state each time the dialog is shown.
    userHasModifiedTime = false;
    currentSearchTargetRes = 0;
    popularDatesMenu.setSelection(0);
    calendar.setTime(new Date());
    updateGoButtonText();
    updateDisplay();
    applyNightMode();
  }

  private void applyNightMode() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    isNight = ActivityLightLevelManager.isNightMode(prefs);
    int textColor = isNight ? getContext().getColor(R.color.night_text_color) : Color.WHITE;
    if (getWindow() != null && getWindow().getDecorView() instanceof ViewGroup) {
      NightModeHelper.tintTextViews((ViewGroup) getWindow().getDecorView(), textColor);
    }
    if (getWindow() != null) {
      int dividerId = getContext().getResources().getIdentifier("titleDivider", "id", "android");
      if (dividerId != 0) {
        View divider = getWindow().getDecorView().findViewById(dividerId);
        if (divider != null) divider.setBackgroundColor(isNight ? textColor : 0xFF444444);
      }
    }
    // Refresh spinner dropdown so it picks up the updated isNight state
    if (popularDatesAdapter != null) {
      popularDatesAdapter.notifyDataSetChanged();
    }
  }

  /**
   * Builds an adapter for the popular-events spinner. Position 0 is a non-selectable hint item
   * displayed in a greyed, italic style.
   */
  private ArrayAdapter<String> buildEventAdapter(Context context) {
    List<TimeTravelEvent> events = TimeTravelEvents.ALL;
    String[] labels = new String[events.size()];
    for (int i = 0; i < events.size(); i++) {
      labels[i] = context.getString(events.get(i).getDisplayNameRes());
    }
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
        context, android.R.layout.simple_spinner_item, labels) {

      @Override
      public boolean isEnabled(int position) {
        return position != 0;
      }

      @Override
      public View getDropDownView(int position, @Nullable View convertView,
          @NonNull ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        // Always set color to handle recycled views correctly.
        // spinner_dropdown_item.xml has a dark background; use GRAY for the hint row.
        if (view instanceof TextView) {
          int activeColor = isNight ? context.getColor(R.color.night_text_color) : Color.WHITE;
          ((TextView) view).setTextColor(position == 0 ? Color.GRAY : activeColor);
        }
        return view;
      }
    };
    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
    return adapter;
  }

  private Dialog createTimePicker() {
    OnTimeSetListener timeSetListener = new TimePickerDialog.OnTimeSetListener() {
      public void onTimeSet(TimePicker view, int hour, int minute) {
        setTime(hour, minute);
        Log.d(TAG, "Setting time to: " + hour + ":" + minute);
      }
    };
    return new TimePickerDialog(getContext(),
                                timeSetListener,
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true) {
    };
  }

  private Dialog createDatePicker() {
    OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
      public void onDateSet(DatePicker view, int year,
                            int monthOfYear, int dayOfMonth) {
        setDate(year, monthOfYear, dayOfMonth);
        Log.d(TAG, "Setting date to: " + year + "-" + monthOfYear + "-" + dayOfMonth);
      }
    };
    return new DatePickerDialog(getContext(),
                                dateSetListener,
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)) {
    };
  }

  /**
   * Sets the internal calendar of this dialog.  Remember that months are zero
   * based.  Current time is preserved.
   */
  private void setDate(int year, int month, int day) {
    calendar.set(year, month, day);
    userHasModifiedTime = true;
    updateGoButtonText();
    updateDisplay();
  }

  /**
   * Sets the internal calendar of this dialog.  Current date is preserved.
   */
  private void setTime(int hour, int minute) {
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
    userHasModifiedTime = true;
    updateGoButtonText();
    updateDisplay();
  }

  /**
   * Sets the internal calendar of this dialog to the given date.
   */
  private void setDate(Date date) {
    calendar.setTime(date);
    updateDisplay();
  }

  private void updateDisplay() {
    Date date = calendar.getTime();
    dateTimeReadout.setText(parentActivity.getString(R.string.now_visiting,
                                                                   dateFormat.format(date)));
  }

  private void updateGoButtonText() {
    if (userHasModifiedTime) {
      goButton.setText(R.string.go);
    } else {
      goButton.setText(R.string.start_from_now);
    }
  }

  private Universe universe = new Universe();

  private void setToNextSunRiseOrSet(CelestialObject.RiseSetIndicator indicator) {
    Calendar riseset = universe.solarSystemObjectFor(SolarSystemBody.Sun).calcNextRiseSetTime(
        calendar, model.getLocation(), indicator);
    if (riseset == null) {
      Toast.makeText(this.getContext(), R.string.sun_wont_set_message, Toast.LENGTH_SHORT).show();
    } else {
      Log.d(TAG, "Sun rise or set is at: " + normalizeHours(
            riseset.get(Calendar.HOUR_OF_DAY)) + ":" + riseset.get(Calendar.MINUTE));
      setDate(riseset.getTime());
    }
  }

  /**
   * Applies the time travel event at the given index in {@link TimeTravelEvents#ALL}.
   */
  private void applyPopularEvent(int index) {
    TimeTravelEvent event = TimeTravelEvents.ALL.get(index);
    Log.d(TAG, "Popular event " + index + ": " + getContext().getString(event.getDisplayNameRes()));
    currentSearchTargetRes = event.getSearchTargetRes();
    userHasModifiedTime = true;
    updateGoButtonText();
    switch (event.getType()) {
      case NOW:
        calendar.setTime(new Date());
        break;
      case NEXT_SUNSET:
        setToNextSunRiseOrSet(CelestialObject.RiseSetIndicator.SET);
        break;
      case NEXT_SUNRISE:
        setToNextSunRiseOrSet(CelestialObject.RiseSetIndicator.RISE);
        break;
      case NEXT_FULL_MOON:
        setDate(getNextFullMoon(calendar.getTime()));
        break;
      case NEXT_NEW_MOON:
        setDate(getNextNewMoon(calendar.getTime()));
        break;
      case FIXED:
        setDate(new Date(event.getTimestampMs()));
        break;
      default:
        Log.e(TAG, "Unknown event type: " + event.getType());
    }
    updateDisplay();
  }
}
