/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sunshine.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.skp.openplatform.android.sdk.api.APIRequest;
import com.skp.openplatform.android.sdk.common.PlanetXSDKConstants;
import com.skp.openplatform.android.sdk.common.PlanetXSDKException;
import com.skp.openplatform.android.sdk.common.RequestBundle;
import com.skp.openplatform.android.sdk.common.PlanetXSDKConstants.CONTENT_TYPE;
import com.skp.openplatform.android.sdk.common.ResponseMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements ForecastFragment.Callback {

    private final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String DETAILFRAGMENT_TAG = "DFTAG";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";


    /**
     * Substitute you own project number here. This project number comes
     * from the Google Developers Console.
     */
    static final String PROJECT_NUMBER = "587454268503";

    private boolean mTwoPane;
    private String mLocation;
    private GoogleCloudMessaging mGcm;
    LocationManager locationManager;
    String mediumProvider;
    TextView mAddressView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocation = Utility.getCurrentAddress(this);
        Uri contentUri = getIntent() != null ? getIntent().getData() : null;

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        mAddressView = (TextView)findViewById(R.id.list_item_address_textview);
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setPowerRequirement(Criteria.NO_REQUIREMENT);
        criteria.setAltitudeRequired(false);
        criteria.setCostAllowed(true);
        mediumProvider = locationManager.getBestProvider(criteria, true);
//        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60*60*1000, 3000, mListener);
        Location location = locationManager.getLastKnownLocation(mediumProvider);

        if(location == null) {
            Log.d(LOG_TAG,"location is null");
        }else {
            Log.d(LOG_TAG,"longitude:" + location.getLongitude() + " latitude" + location.getLatitude());
            Utility.setCurrentLocation(this,location);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String address = prefs.getString(getString(R.string.pref_location_village_key), "");
        Log.d(LOG_TAG,"address is " + address);
        if(mAddressView!=null) mAddressView.setText(address);

        if (findViewById(R.id.weather_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/layout-sw600dp). If this view is present, then the activity should be
            // in two-pane mode.
            mTwoPane = true;
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (savedInstanceState == null) {
                DetailFragment fragment = new DetailFragment();
                if (contentUri != null) {
                    Bundle args = new Bundle();
                    args.putParcelable(DetailFragment.DETAIL_URI, contentUri);
                    fragment.setArguments(args);
                }
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.weather_detail_container, fragment, DETAILFRAGMENT_TAG)
                        .commit();
            }
        } else {
            mTwoPane = false;
            getSupportActionBar().setElevation(0f);
        }

        ForecastFragment forecastFragment =  ((ForecastFragment)getSupportFragmentManager()
                .findFragmentById(R.id.fragment_forecast));
        forecastFragment.setUseTodayLayout(!mTwoPane);
        if (contentUri != null) {
            forecastFragment.setInitialSelectedDate(
                    WeatherContract.WeatherEntry.getDateFromUri(contentUri));
        }

        SunshineSyncAdapter.initializeSyncAdapter(this,handler);

        // If Google Play Services is not available, some features, such as GCM-powered weather
        // alerts, will not be available.
        if (checkPlayServices()) {
            mGcm = GoogleCloudMessaging.getInstance(this);
            String regId = getRegistrationId(this);

            if (PROJECT_NUMBER.equals("Your Project Number")) {
                new AlertDialog.Builder(this)
                .setTitle("Needs Project Number")
                .setMessage("GCM will not function in Sunshine until you set the Project Number to the one from the Google Developers Console.")
                .setPositiveButton(android.R.string.ok, null)
                .create().show();
            } else if (regId.isEmpty()) {
                registerInBackground(this);
            }
        } else {
            Log.i(LOG_TAG, "No valid Google Play Services APK. Weather alerts will be disabled.");
            // Store regID as null
            storeRegistrationId(this, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(LOG_TAG,"handleMessage가 수신됨");
            Bundle bundle = msg.getData();
            String village = bundle.getString(getString(R.string.pref_village_key));
            if(mAddressView!=null){
                Log.d(LOG_TAG,"address village가 업데이트 됨:" + village);
                mAddressView.setText(village);
            }
        }
    };

    LocationListener mListener = new LocationListener() {

        class LoopThread extends Thread {  // Runnable 인터페이스 구현
            String lat;
            String lon;
            final String SKP_TMAP_ADDRESS_URL = "https://apis.skplanetx.com/tmap/geo/reversegeocoding";
            final String VERSION = "version";
            final String LATITUDE = "lat";
            final String LONGITUDE = "lon";
            final String API_SUCCESS	=	"200";

            public LoopThread(String lat, String lon) {
                this.lat = lat;
                this.lon = lon;
            }
            public void run() {
                Map<String, Object> param = new HashMap<String, Object>();
                param.put(VERSION, "1");
                param.put(LATITUDE, lat);
                param.put(LONGITUDE, lon);
                param.put("coordType", "BESSELGEO");

                RequestBundle requestBundle = new RequestBundle();
                requestBundle.setUrl(SKP_TMAP_ADDRESS_URL);
                requestBundle.setParameters(param);
                requestBundle.setHttpMethod(PlanetXSDKConstants.HttpMethod.GET);
                requestBundle.setResponseType(CONTENT_TYPE.JSON);

                //        APIRequest.setAppKey("ed18a1b1-6ed0-3efd-a7ca-e55d6880391d");
                APIRequest.setAppKey("fe006873-5644-37af-a2b0-b4a3609c922e");
                APIRequest planetapi = new APIRequest();

                String village = Utility.getCurrentAddress(getApplicationContext());

                try {
                    ResponseMessage addressMsg = planetapi.request(requestBundle);
                    Log.d(LOG_TAG, "location status is update:" + addressMsg.getStatusCode() + "  :  " + addressMsg.getResultMessage());

                    if(addressMsg.getStatusCode().equals(API_SUCCESS)) {
                        JSONObject addressJson = new JSONObject(addressMsg.getResultMessage());
                        JSONObject addressInfo = addressJson.getJSONObject("addressInfo");
                        String legalDong = addressInfo.getString("legalDong");

                        if(!legalDong.equals(village)) {
                            Log.i(LOG_TAG, "위치정보가 " + village +"에서 " + legalDong + "으로 변경되었습니다");
                            Message msg = new Message();
                            Bundle bundle = new Bundle();
                            bundle.putString(getString(R.string.pref_village_key), legalDong);
                            msg.setData(bundle);
                            handler.sendMessage(msg);
                            updateWeather();
                        }else {
                            Log.i(LOG_TAG, "위치정보가 " + legalDong + "으로 동일함");
                        }
                    }
                } catch (PlanetXSDKException e) {
                    Log.e(LOG_TAG, "sk planet api call error:" + e.getMessage());
                } catch (JSONException e) {
                    Log.e(LOG_TAG," JSON parsing error:" + e.getMessage());
                }
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            if(location == null) {
                Log.d(LOG_TAG,"location is null");
            }else {
                Log.d(LOG_TAG, "onLocationChanged longitude:" + location.getLongitude() + " latitude:" + location.getLatitude());

                String lon = location.getLongitude() +"";
                String lat = location.getLatitude() +"";
                new LoopThread(lat,lon).start();
                //너무 자주 업데이트 됨
                Utility.setCurrentLocation(getApplicationContext(), location);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(LOG_TAG, "위치서비스 이용가능");
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(LOG_TAG, "위치서비스 이용불가");
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        locationManager.requestLocationUpdates(mediumProvider,60000, 1000, mListener);
        // If Google Play Services is not available, some features, such as GCM-powered weather
        // alerts, will not be available.
        if (!checkPlayServices()) {
            // Store regID as null
        }

        String location = Utility.getCurrentAddress(this);
        // update the location in our second pane using the fragment manager
        if (location != null && !location.equals(mLocation)) {
            ForecastFragment ff = (ForecastFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
            if ( null != ff ) {
                ff.onLocationChanged();
            }
            DetailFragment df = (DetailFragment)getSupportFragmentManager().findFragmentByTag(DETAILFRAGMENT_TAG);
            if ( null != df ) {
                df.onLocationChanged(location);
            }
            mLocation = location;
        }
    }

    @Override
    public void onItemSelected(Uri contentUri, ForecastAdapter.ForecastAdapterViewHolder vh) {
        Log.d(LOG_TAG, "onItemSelected:" + vh.getAdapterPosition());


        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle args = new Bundle();
            args.putParcelable(DetailFragment.DETAIL_URI, contentUri);

            DetailFragment fragment = new DetailFragment();
            fragment.setArguments(args);



            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.weather_detail_container, fragment, DETAILFRAGMENT_TAG)
                    .commit();
        } else {

            Intent intent = new Intent(this, DetailActivity.class);
            intent.setData(contentUri);
            intent.putExtra(getString(R.string.pref_position_key), vh.getAdapterPosition());

            ActivityOptionsCompat activityOptions =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(this,
                            new Pair<View, String>(vh.mIconView, getString(R.string.detail_icon_transition_name)));
            ActivityCompat.startActivity(this, intent, activityOptions.toBundle());
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean     checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(LOG_TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Gets the current registration ID for application on GCM service.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(LOG_TAG, "GCM Registration not found.");
            return "";
        }

        // Check if app was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(LOG_TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        // Sunshine persists the registration ID in shared preferences, but
        // how you store the registration ID in your app is up to you. Just make sure
        // that it is private!
        return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // Should never happen. WHAT DID YOU DO?!?!
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground(final Context context) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                String msg = "";
                try {
                    if (mGcm == null) {
                        mGcm = GoogleCloudMessaging.getInstance(context);
                    }
                    String regId = mGcm.register(PROJECT_NUMBER);
                    msg = "Device registered, registration ID=" + regId;

                    // You should send the registration ID to your server over HTTP,
                    // so it can use GCM/HTTP or CCS to send messages to your app.
                    // The request to your server should be authenticated if your app
                    // is using accounts.
                    //sendRegistrationIdToBackend();
                    // For this demo: we don't need to send it because the device
                    // will send upstream messages to a server that echo back the
                    // message using the 'from' address in the message.

                    // Persist the registration ID - no need to register again.
                    storeRegistrationId(context, regId);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // TODO: If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return null;
            }
        }.execute(null, null, null);
    }

    /**
     * Stores the registration ID and app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(LOG_TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    private void updateWeather() {
        SunshineSyncAdapter.syncImmediately(this);
    }
}
