package com.google.android.stardroid.activities.util.loadPlaces.http;

import android.support.annotation.NonNull;

import com.google.android.stardroid.activities.util.loadPlaces.API;
import com.google.android.stardroid.activities.util.loadPlaces.PlacesBaseURL;
import com.google.android.stardroid.activities.util.loadPlaces.model.PlacesResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GetPlaces {

    private GetPlacesInterface getPlacesInterface;
    // TODO(makhanov madiyar) remove with team's own api key, this is my temporary key, and locate it in constants file
    private final static String GOOGLE_PLACES_API_KEY = "AIzaSyDy2Q5UOwAcmDL-4HhDFznChe48GQ9Ahic";

    public GetPlaces(GetPlacesInterface getPlacesInterface) {
        this.getPlacesInterface = getPlacesInterface;
    }

    public interface GetPlacesInterface {
        void getPlacesResponse(PlacesResponse response, String message);
    }

    public void load(String input) {
        API service = PlacesBaseURL.getRetrofit();
        Call<PlacesResponse> call = service.getPlacesList(GOOGLE_PLACES_API_KEY, "geocode",
                true, input);
        call.enqueue(new Callback<PlacesResponse>() {
            @Override
            public void onResponse(@NonNull Call<PlacesResponse> call, @NonNull Response<PlacesResponse> response) {
                getPlacesInterface.getPlacesResponse(response.body(), response.message());
            }

            @Override
            public void onFailure(@NonNull Call<PlacesResponse> call, @NonNull Throwable t) {
                getPlacesInterface.getPlacesResponse(null, t.getMessage());
            }
        });

    }

}
