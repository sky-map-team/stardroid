package com.google.android.stardroid.activities.util.loadPlaces;

import com.google.android.stardroid.activities.util.loadPlaces.model.PlacesResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface API {

    @GET("maps/api/place/autocomplete/json")
    Call<PlacesResponse> getPlacesList(@Query("key") String key,
                                       @Query("types") String types,
                                       @Query("sensor") boolean sensor,
                                       @Query("input") String input);

}
