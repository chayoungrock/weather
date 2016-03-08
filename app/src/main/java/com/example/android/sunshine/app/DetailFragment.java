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

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.util.Util;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.data.WeatherContract.WeatherEntry;
import com.example.android.sunshine.app.data.WeatherContract.TemperatureEntry;

import java.util.Vector;

/**
 * A placeholder fragment containing a simple view.
 */
public class DetailFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String LOG_TAG = DetailFragment.class.getSimpleName();
    static final String DETAIL_URI = "URI";
    static final String DETAIL_TRANSITION_ANIMATION = "DTA";

    private static final String FORECAST_SHARE_HASHTAG = " #SunshineApp";

    private String mForecast;
    private Uri mUri;
    private boolean bToday;
    private boolean mTransitionAnimation;

    private static final int DETAIL_LOADER = 0;

    private static final String[] DETAIL_COLUMNS = {
            WeatherEntry.TABLE_NAME + "." + WeatherEntry._ID,
            WeatherEntry.COLUMN_DATE,
            WeatherEntry.COLUMN_SHORT_DESC,
            WeatherEntry.COLUMN_MAX_TEMP,
            WeatherEntry.COLUMN_MIN_TEMP,
            WeatherEntry.COLUMN_CUR_TEMP,
            WeatherEntry.COLUMN_HUMIDITY,
            WeatherEntry.COLUMN_PRESSURE,
            WeatherEntry.COLUMN_WIND_SPEED,
            WeatherEntry.COLUMN_DEGREES,
            WeatherEntry.COLUMN_WEATHER_ID,
            WeatherEntry.COLUMN_CODE,
            // This works because the WeatherProvider returns location data joined with
            // weather data, even though they're stored in two different tables.
            WeatherContract.LocationEntry.COLUMN_FULL_ADDRESS_NAME
    };

    private static final String[] DETAIL_COLUMNS2 = {
            WeatherEntry.TABLE_NAME + "." + WeatherEntry._ID,
            WeatherEntry.COLUMN_LOC_KEY
    };

    // These indices are tied to DETAIL_COLUMNS.  If DETAIL_COLUMNS changes, these
    // must change.
    public static final int COL_WEATHER_ID = 0;
    public static final int COL_WEATHER_DATE = 1;
    public static final int COL_WEATHER_DESC = 2;
    public static final int COL_WEATHER_MAX_TEMP = 3;
    public static final int COL_WEATHER_MIN_TEMP = 4;
    public static final int COL_WEATHER_CUR_TEMP = 5;
    public static final int COL_WEATHER_HUMIDITY = 6;
    public static final int COL_WEATHER_PRESSURE = 7;
    public static final int COL_WEATHER_WIND_SPEED = 8;
    public static final int COL_WEATHER_DEGREES = 9;
    public static final int COL_WEATHER_CONDITION_ID = 10;
    public static final int COL_WEATHER_CODE = 11;
    public static final int COL_FULL_ADDRESS_NAME = 12;


    private static final String[] TEMPERATURE_COLUMNS = {
            TemperatureEntry.TABLE_NAME + "." + TemperatureEntry._ID,
            TemperatureEntry.COLUMN_CURRENT_TEMP_ID,
            TemperatureEntry.COLUMN_HOUR,
            TemperatureEntry.COLUMN_TEMPERATURE,
            TemperatureEntry.COLUMN_WINDSPEED,
            TemperatureEntry.COLUMN_WINDDIRECTION,
            TemperatureEntry.COLUMN_HUMIDITY,
            TemperatureEntry.COLUMN_CODE,
            TemperatureEntry.COLUMN_NAME
    };

    public static final int COL_TEMPERATURE_ID           = 0;
    public static final int COL_TEMPERATURE_TEMP_ID     = 1;
    public static final int COL_TEMPERATURE_HOUR        = 2;
    public static final int COL_TEMPERATURE_VALUE       = 3;
    public static final int COL_WINDSPPED_VALUE         = 4;
    public static final int COL_WINDDIRECTION_VALUE    = 5;
    public static final int COL_HUMIDITY_VALUE          = 6;
    public static final int COL_TEMPERATURE_CODE        = 7;
    public static final int COL_TEMPERATURE_NAME        = 8;

    private ImageView mIconView;
    private TextView mDateView;
    private TextView mAddresView;
    private TextView mDescriptionView;
    private TextView mHighTempView;
    private TextView mLowTempView;
    private TextView mHumidityView;
    private TextView mHumidityLabelView;
    private TextView mWindView;
    private TextView mWindLabelView;
    private TextView mPressureView;
    private TextView mPressureLabelView;
    private SeekBar mSeekBarView;

    private Vector<ContentValues> forecastData;
    private Vector<String> forecastTemperature;

    public DetailFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        forecastData = new Vector<ContentValues>();
        forecastTemperature = new Vector<String>();

        Bundle arguments = getArguments();
        Log.d(LOG_TAG, "onCreateLoader position id:" + arguments.getInt(getString(R.string.pref_position_key)));

        if(arguments.getInt(getString(R.string.pref_position_key))==0) bToday = true;
        else    bToday = false;

        if (arguments != null) {
            mUri = arguments.getParcelable(DetailFragment.DETAIL_URI);
            mTransitionAnimation = arguments.getBoolean(DetailFragment.DETAIL_TRANSITION_ANIMATION, false);
        }

        View rootView = inflater.inflate(R.layout.fragment_detail_start, container, false);
        mIconView = (ImageView) rootView.findViewById(R.id.detail_icon);
        mDateView = (TextView) rootView.findViewById(R.id.detail_date_textview);
        mAddresView = (TextView) rootView.findViewById(R.id.detail_address_textview);
        mDescriptionView = (TextView) rootView.findViewById(R.id.detail_forecast_textview);
        mHighTempView = (TextView) rootView.findViewById(R.id.detail_high_textview);
        mLowTempView = (TextView) rootView.findViewById(R.id.detail_low_textview);
        mHumidityView = (TextView) rootView.findViewById(R.id.detail_humidity_textview);
        mHumidityLabelView = (TextView) rootView.findViewById(R.id.detail_humidity_label_textview);
        mWindView = (TextView) rootView.findViewById(R.id.detail_wind_textview);
        mWindLabelView = (TextView) rootView.findViewById(R.id.detail_wind_label_textview);
        mPressureView = (TextView) rootView.findViewById(R.id.detail_pressure_textview);
        mPressureLabelView = (TextView) rootView.findViewById(R.id.detail_pressure_label_textview);
        mSeekBarView = (SeekBar)rootView.findViewById(R.id.seekbar);
        mSeekBarView.setProgress(0);

        return rootView;
    }

    private void finishCreatingMenu(Menu menu) {
        // Retrieve the share menu item
        MenuItem menuItem = menu.findItem(R.id.action_share);
        menuItem.setIntent(createShareForecastIntent());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if ( getActivity() instanceof DetailActivity ){
            // Inflate the menu; this adds items to the action bar if it is present.
            inflater.inflate(R.menu.detailfragment, menu);
            finishCreatingMenu(menu);
        }
    }

    private Intent createShareForecastIntent() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, mForecast + FORECAST_SHARE_HASHTAG);
        return shareIntent;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(DETAIL_LOADER, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    void onLocationChanged( String newLocation ) {
        // replace the uri, since the location has changed
        Uri uri = mUri;
        if (null != uri) {
            long date = WeatherContract.WeatherEntry.getDateFromUri(uri);
            Uri updatedUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(newLocation, date);
            mUri = updatedUri;
            getLoaderManager().restartLoader(DETAIL_LOADER, null, this);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        if ( null != mUri ) {
            // Now create and return a CursorLoader that will take care of
            // creating a Cursor for the data being displayed.
            return new CursorLoader(
                    getActivity(),
                    mUri,
                    DETAIL_COLUMNS,
                    null,
                    null,
                    null
            );
        }
        ViewParent vp = getView().getParent();
        if ( vp instanceof CardView ) {
            ((View)vp).setVisibility(View.INVISIBLE);
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data != null && data.moveToFirst()) {
            ViewParent vp = getView().getParent();
            if ( vp instanceof CardView ) {
                ((View)vp).setVisibility(View.VISIBLE);
            }

            String dateText = null;

            if(bToday) {
                double cur =  data.getDouble(COL_WEATHER_CUR_TEMP);
                String curString = Utility.formatTemperature(getActivity(), cur);
                dateText = Utility.getDayKor(0);
                mHighTempView.setText(curString);
                long id = data.getLong(0);
                String fulladdress = data.getString(COL_FULL_ADDRESS_NAME);

                Log.d(LOG_TAG,"id:" + id + " fulladdress:" + fulladdress);

                Cursor value = getActivity().getContentResolver().query(WeatherContract.TemperatureEntry.
                                CONTENT_URI, TEMPERATURE_COLUMNS,
                        WeatherContract.TemperatureEntry.COLUMN_CURRENT_TEMP_ID + " = ?",
                        new String[]{Long.toString(id)}, null);

                while(value.moveToNext()) {
                    ContentValues content = new ContentValues();
                    String curTempString = Utility.formatTemperature(getActivity(), value.getDouble(COL_TEMPERATURE_VALUE));
                    String curWindString = Utility.getFormattedWind(getActivity(), value.getFloat(COL_WINDSPPED_VALUE), value.getFloat(COL_WINDDIRECTION_VALUE));
                    String curHumdityString = getActivity().getString(R.string.format_humidity,  value.getDouble(COL_HUMIDITY_VALUE));
                    String shortDesc = value.getString(COL_TEMPERATURE_NAME);
                    String code = value.getString(COL_TEMPERATURE_CODE);
                    int hour = value.getInt(COL_TEMPERATURE_HOUR);
                    content.put(TemperatureEntry.COLUMN_TEMPERATURE,curTempString);
                    content.put(TemperatureEntry.COLUMN_WINDSPEED,curWindString);
                    content.put(TemperatureEntry.COLUMN_HUMIDITY,curHumdityString);
                    content.put(TemperatureEntry.COLUMN_NAME,shortDesc);
                    content.put(TemperatureEntry.COLUMN_CODE,code);
                    content.put(TemperatureEntry.COLUMN_HOUR,hour);
                    forecastData.add(content);
                }
                mSeekBarView.setMax(forecastData.size());
                mSeekBarView.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                        Log.d(LOG_TAG,"onSeekBarchangedListner:" + progress);

                        if(progress>=0 && progress <forecastData.size()) {
                            ContentValues value = forecastData.get(progress);
                            String dateText = Utility.getDayKor(progress);
                            mDateView.setText(dateText);
                            mHighTempView.setText(value.getAsString(TemperatureEntry.COLUMN_TEMPERATURE));
                            mDescriptionView.setText(value.getAsString(TemperatureEntry.COLUMN_NAME));
                            mWindView.setText(value.getAsString(TemperatureEntry.COLUMN_WINDSPEED));
                            mHumidityView.setText(value.getAsString(TemperatureEntry.COLUMN_HUMIDITY));
                            mIconView.setImageResource(Utility.getArtResourceForWeatherCondition(value.getAsString(TemperatureEntry.COLUMN_CODE)));
                        }
                    }

                    public void onStartTrackingTouch(SeekBar seekBar) {
                        seekBar.setActivated(true);
                    }

                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
            }else {
                mSeekBarView.setVisibility(View.INVISIBLE);
                long date = data.getLong(COL_WEATHER_DATE);
                dateText = Utility.getFullFriendlyDayString(getActivity(), date);
            }

            // Read weather condition ID from cursor
            int weatherId = data.getInt(COL_WEATHER_CONDITION_ID);

            if ( Utility.usingLocalGraphics(getActivity()) ) {
                mIconView.setImageResource(Utility.getArtResourceForWeatherCondition(data.getString(COL_WEATHER_CODE)));
//                mIconView.setImageResource(Utility.getArtResourceForWeatherCondition(weatherId));
            } else {
                // Use weather art image
                Glide.with(this)
                        .load(Utility.getArtUrlForWeatherCondition(getActivity(), weatherId))
                        .error(Utility.getArtResourceForWeatherCondition(weatherId))
                        .crossFade()
                        .into(mIconView);
            }

            // Read date from cursor and update views for day of week and date
//            long date = data.getLong(COL_WEATHER_DATE);
//            String dateText = Utility.getFullFriendlyDayString(getActivity(),date);
            mDateView.setText(dateText);

            String address = data.getString(COL_FULL_ADDRESS_NAME);
            Log.d(LOG_TAG,"상세화면에서 address name:" + address);
            mAddresView.setText(address);


            // Get description from weather condition ID
//            String description = Utility.getStringForWeatherCondition(getActivity(), weatherId);
            String description = data.getString(ForecastFragment.COL_WEATHER_DESC);
            mDescriptionView.setText(description);
            mDescriptionView.setContentDescription(getString(R.string.a11y_forecast, description));

            // For accessibility, add a content description to the icon field. Because the ImageView
            // is independently focusable, it's better to have a description of the image. Using
            // null is appropriate when the image is purely decorative or when the image already
            // has text describing it in the same UI component.
            mIconView.setContentDescription(getString(R.string.a11y_forecast_icon, description));

            // Read high temperature from cursor and update view
            boolean isMetric = Utility.isMetric(getActivity());
            double high = data.getDouble(COL_WEATHER_MAX_TEMP);
            String highString = Utility.formatTemperature(getActivity(), high);
//            mHighTempView.setText(highString);
            mHighTempView.setContentDescription(getString(R.string.a11y_high_temp, highString));

            // Read low temperature from cursor and update view
            double low = data.getDouble(COL_WEATHER_MIN_TEMP);
            String lowString = Utility.formatTemperature(getActivity(), low);
            mLowTempView.setText(highString +" " + lowString);
            mLowTempView.setContentDescription(getString(R.string.a11y_low_temp, lowString));

            // Read humidity from cursor and update view
            float humidity = data.getFloat(COL_WEATHER_HUMIDITY);
            mHumidityView.setText(getActivity().getString(R.string.format_humidity, humidity));
            mHumidityView.setContentDescription(getString(R.string.a11y_humidity, mHumidityView.getText()));
            mHumidityLabelView.setContentDescription(mHumidityView.getContentDescription());

            // Read wind speed and direction from cursor and update view
            float windSpeedStr = data.getFloat(COL_WEATHER_WIND_SPEED);
            float windDirStr = data.getFloat(COL_WEATHER_DEGREES);
            mWindView.setText(Utility.getFormattedWind(getActivity(), windSpeedStr, windDirStr));
            mWindView.setContentDescription(getString(R.string.a11y_wind, mWindView.getText()));
            mWindLabelView.setContentDescription(mWindView.getContentDescription());

            // Read pressure from cursor and update view
            float pressure = data.getFloat(COL_WEATHER_PRESSURE);
            mPressureView.setText(getString(R.string.format_pressure, pressure));
            mPressureView.setContentDescription(getString(R.string.a11y_pressure, mPressureView.getText()));
            mPressureLabelView.setContentDescription(mPressureView.getContentDescription());

            // We still need this for the share intent
            mForecast = String.format("%s - %s - %s/%s", dateText, description, high, low);

        }
        AppCompatActivity activity = (AppCompatActivity)getActivity();
        Toolbar toolbarView = (Toolbar) getView().findViewById(R.id.toolbar);

        // We need to start the enter transition after the data has loaded
        if ( mTransitionAnimation ) {
            activity.supportStartPostponedEnterTransition();

            if ( null != toolbarView ) {
                activity.setSupportActionBar(toolbarView);

                activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        } else {
            if ( null != toolbarView ) {
                Menu menu = toolbarView.getMenu();
                if ( null != menu ) menu.clear();
                toolbarView.inflateMenu(R.menu.detailfragment);
                finishCreatingMenu(toolbarView.getMenu());
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) { }
}