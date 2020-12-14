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

import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.provider.ephemeris.Planet;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Implementation of the time travel dialog.
 *
 * @author Dominic Widdows
 * @author John Taylor
 */
public class TimeTravelDialog extends Dialog {
  private static final String TAG = MiscUtil.getTag(TimeTravelDialog.class);
  private static final int MIN_CLICK_TIME = 1000;
  private Spinner popularDatesMenu;
  private TextView dateTimeReadout;
  private DynamicStarMapActivity parentActivity;
  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
  // This is the date we will apply to the controller when the user hits go.
  private Calendar calendar = Calendar.getInstance();
  private AstronomerModel model;
  private long lastClickTime = 0;

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
          createDatePicker().show();
        }
      });

    Button changeTimeButton = (Button) findViewById(R.id.pickTime);
    changeTimeButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          if (SystemClock.elapsedRealtime() - lastClickTime < MIN_CLICK_TIME) return;
          lastClickTime = SystemClock.elapsedRealtime();
          createTimePicker().show();
        }
      });

    Button goButton = (Button) findViewById(R.id.timeTravelGo);
    goButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          parentActivity.setTimeTravelMode(calendar.getTime());
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
    ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
        this.getContext(), R.array.popular_date_examples, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
    popularDatesMenu.setAdapter(adapter);
    popularDatesMenu.setSelection(1);
    popularDatesMenu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      // The callback received when the user selects a menu item.
      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        setPopularDate(popularDatesMenu.getSelectedItemPosition());
      }
      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Do nothing in this case.
      }
    });
    // Start by initializing ourselves to 'now'.  Note that this is the value
    // the first time the dialog is shown.  Thereafter it will remember the
    // last value set.
    calendar.setTime(new Date());
    updateDisplay();
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
    updateDisplay();
  }

  /**
   * Sets the internal calendar of this dialog.  Current date is preserved.
   */
  private void setTime(int hour, int minute) {
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
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

  private void setToNextSunRiseOrSet(Planet.RiseSetIndicator indicator) {
    Calendar riseset = Planet.Sun.calcNextRiseSetTime(calendar, model.getLocation(), indicator);
    if (riseset == null) {
      Toast.makeText(this.getContext(), R.string.sun_wont_set_message, Toast.LENGTH_SHORT).show();
    } else {
      Log.d(TAG, "Sun rise or set is at: " + TimeUtil.normalizeHours(
            riseset.get(Calendar.HOUR_OF_DAY)) + ":" + riseset.get(Calendar.MINUTE));
      setDate(riseset.getTime());
    }
  }
  
  /**
   * Associates time settings with the options in the popular dates menu.
   * It HAS to be kept in sync with res/values/arrays.xml.
   *
   * @param popularDateIndex The index into the popular dates array.
   */
  private void setPopularDate(int popularDateIndex) {
    String s = (String) popularDatesMenu.getSelectedItem();
    Log.d(TAG, "Popular date " + popularDatesMenu.getSelectedItemPosition() + "  " + s);
    Calendar c = Calendar.getInstance();
    c.setTime(model.getTime());
    switch (popularDateIndex) {
      case 0:  // Now
        calendar.setTime(new Date());
        break;
      case 1:  // Next sunset
        setToNextSunRiseOrSet(Planet.RiseSetIndicator.SET);
        break;
      case 2:  // Next sunrise
        setToNextSunRiseOrSet(Planet.RiseSetIndicator.RISE);
        break;
      case 3:  // Next full moon
        Date nextFullMoon = Planet.getNextFullMoon(calendar.getTime());
        setDate(nextFullMoon);
        break;
      case 4: // Mercury transit 2016.
        // Source: http://eclipsewise.com/oh/tm2016.html
        // http://mainfacts.com/timestamp-date-converter-calculator
        setDate(new Date(1462805846000L));
        break;
      case 5: // Solar Eclipse 2017 North America.
        // Source: http://mainfacts.com/timestamp-date-converter-calculator
        setDate(new Date(1503340380000L));
        break;
      case 6: // Solar Eclipse 2016.
        // Source: http://mainfacts.com/timestamp-date-converter-calculator
        setDate(new Date(1457489160000L));
        break;
      case 7: // Solar Eclipse 1919.
        setDate(new Date(-1596619190000L));
        break;
      case 8:  // Moon Landing 1969.
        setDate(new Date(-14182953622L));
        break;
      case 9:  // 2020 Saturn/Jupiter conjunction
        setDate(new Date(1608574800000L));
        break;
      default:
        Log.d(TAG, "Incorrect popular date index!");
    }
    updateDisplay();
  }
}
