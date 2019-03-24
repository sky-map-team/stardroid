package com.google.android.stardroid.activities.util.loadPlaces;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PlacesBaseURL {

    private static Retrofit retrofit = null;
    private final static String PLACES_BASE_URL = "https://maps.googleapis.com/";

    public static API getRetrofit() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(PLACES_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(API.class);
    }

}