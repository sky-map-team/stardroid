package com.google.android.stardroid.activities.util;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.google.android.stardroid.activities.util.loadPlaces.http.GetPlaces;
import com.google.android.stardroid.activities.util.loadPlaces.model.PlacesResponse;
import com.google.android.stardroid.activities.util.loadPlaces.model.Prediction;
import com.google.android.stardroid.util.MiscUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AutoCompletePreference extends EditTextPreference implements GetPlaces.GetPlacesInterface {

    public AutoCompletePreference(Context context) {
        super(context);
    }

    public AutoCompletePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoCompletePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private GetPlaces getPlaces;
    private AutoCompleteTextView autoCompleteTextView = null;
    private ArrayList<String> placesList = new ArrayList<>();
    private ArrayAdapter<String> placesListAdatper;
    private static final String TAG = MiscUtil.getTag(AutoCompletePreference.class);

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        final EditText editText = view.findViewById(android.R.id.edit);
        ViewGroup.LayoutParams params = editText.getLayoutParams();
        ViewGroup vg = (ViewGroup) editText.getParent();
        String curVal = editText.getText().toString();
        // remove view from the existing layout hierarchy
        vg.removeView(editText);

        // construct a new editable autocompleteTextView
        autoCompleteTextView = new AutoCompleteTextView(getContext());
        autoCompleteTextView.setLayoutParams(params);
        autoCompleteTextView.setId(android.R.id.edit);
        autoCompleteTextView.setText(curVal);
        autoCompleteTextView.setSingleLine();
        // init getPlaces
        getPlaces = new GetPlaces(this);

        //add listener
        autoCompleteTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                placesList = new ArrayList<>();
                getPlaces.load(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });


        placesListAdatper = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, placesList);
        autoCompleteTextView.setAdapter(placesListAdatper);

        // add the new view to the layout
        vg.addView(autoCompleteTextView);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult && autoCompleteTextView != null) {
            String value = autoCompleteTextView.getText().toString();
            if (callChangeListener(value)) {
                setText(value);
            }
        }
    }

    @Override
    public EditText getEditText() {
        return autoCompleteTextView;
    }

    @Override
    public void getPlacesResponse(PlacesResponse response, String message) {
        placesList.clear();

        if (response != null) {
            try {
                List<Prediction> predictions = response.getPredictions();
                for (int i = 0; i < predictions.size(); i++) {
                    placesList.add(i, predictions.get(i).getDescription());
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            Log.w(TAG, message);
        }

        placesListAdatper = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, placesList);

        autoCompleteTextView.setAdapter(placesListAdatper);
        placesListAdatper.notifyDataSetChanged();
    }


}
