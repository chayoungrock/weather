package com.example.android.sunshine.app.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.Time;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.util.Util;
import com.example.android.sunshine.app.MainActivity;
import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.muzei.WeatherMuzeiSource;
import com.skp.openplatform.android.sdk.api.APIRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import com.skp.openplatform.android.sdk.common.PlanetXSDKConstants.CONTENT_TYPE;
import com.skp.openplatform.android.sdk.common.PlanetXSDKConstants.HttpMethod;
import com.skp.openplatform.android.sdk.common.PlanetXSDKException;
import com.skp.openplatform.android.sdk.common.RequestBundle;
import com.skp.openplatform.android.sdk.common.ResponseMessage;

public class SunshineSyncAdapter extends AbstractThreadedSyncAdapter {
    public final String LOG_TAG = SunshineSyncAdapter.class.getSimpleName();
    public static final String ACTION_DATA_UPDATED =
            "com.example.android.sunshine.app.ACTION_DATA_UPDATED";
//    static Location mCurLoc;
    // Interval at which to sync with the weather, in seconds.
    // 60 seconds (1 minute) * 180 = 3 hours
    public static final int SYNC_INTERVAL = 60 * 180;
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;
    private static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;
    private static final int WEATHER_NOTIFICATION_ID = 3004;
    public static final String API_SUCCESS	=	"200";
    
	final String SKP_HUMIDITY 		= "humidity";
	final String SKP_WIND 			= "wind";
	final String SKP_WDIR 			= "wdir";
	final String SKP_WSPD 			= "wspd";

    private static Handler mHandler;//위치 정보가 변경 되었을 때 address 이름을 업데이트 해주기 위함

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_CUR_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_CODE
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_CUR_TEMP = 3;
    private static final int INDEX_SHORT_DESC = 4;
    private static final int INDEX_CODE = 	5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({LOCATION_STATUS_OK, LOCATION_STATUS_SERVER_DOWN, LOCATION_STATUS_SERVER_INVALID,  LOCATION_STATUS_UNKNOWN, LOCATION_STATUS_INVALID})
    public @interface LocationStatus {}

    public static final int LOCATION_STATUS_OK = 0;
    public static final int LOCATION_STATUS_SERVER_DOWN = 1;
    public static final int LOCATION_STATUS_SERVER_INVALID = 2;
    public static final int LOCATION_STATUS_UNKNOWN = 3;
    public static final int LOCATION_STATUS_INVALID = 4;

    public SunshineSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);

    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.d(LOG_TAG, "Starting sync");
        Vector<ContentValues> skplanetWeatherData = new Vector<ContentValues>(11);
        Vector<ContentValues> skplanetShortForcastData = new Vector<ContentValues>(21);
        String address=null;
        String fulladdress = null;
        
        // SK Planet API	//
        final String SKP_WEATHER_8DAY_URL = "http://apis.skplanetx.com/weather/forecast/6days";
        final String SKP_WEATHER_SUMMARY_URL = "http://apis.skplanetx.com/weather/summary";
        final String SKP_WEATHER_CURRENT_URL = "http://apis.skplanetx.com/weather/current/hourly";
        final String SKP_WEATHER_3DAY_URL = "http://apis.skplanetx.com/weather/forecast/3days";
        final String SKP_WEATHER_3HOUR_URL = "http://apis.skplanetx.com/weather/forecast/3hours";
        final String SKP_TMAP_ADDRESS_URL = "https://apis.skplanetx.com/tmap/geo/reversegeocoding";

        final String VERSION = "version";
        final String LATITUDE = "lat";
        final String LONGITUDE = "lon";
        
        APIRequest planetapi = new APIRequest();
//        APIRequest.setAppKey("ed18a1b1-6ed0-3efd-a7ca-e55d6880391d");
        APIRequest.setAppKey("fe006873-5644-37af-a2b0-b4a3609c922e");
        
        Map<String, Object> param = new HashMap<String, Object>();
        param.put(VERSION, "1");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        float latitude = prefs.getFloat((getContext().getString(R.string.pref_location_latitude_key)), 0);
        float longitude = prefs.getFloat((getContext().getString(R.string.pref_location_longitude_key)),0);
        param.put(LATITUDE, latitude);
        param.put(LONGITUDE, longitude);
        param.put("coordType", "BESSELGEO");

        // ȣ��� ���� �� ����
        RequestBundle requestBundle = new RequestBundle();
        requestBundle.setParameters(param);
        requestBundle.setHttpMethod(HttpMethod.GET);
        requestBundle.setResponseType(CONTENT_TYPE.JSON);
        
        try {
            requestBundle.setUrl(SKP_TMAP_ADDRESS_URL);
            ResponseMessage addressMsg = planetapi.request(requestBundle);
            Log.d(LOG_TAG,addressMsg.getStatusCode() + "  :  " + addressMsg.getResultMessage());

            if(addressMsg.getStatusCode().equals(API_SUCCESS)) {
                ContentValues addressValue = getAddressTmapDatafromJson(addressMsg.getResultMessage());
                address = addressValue.getAsString("legalDong");
                Message msg = new Message();
                Bundle bundle = new Bundle();
                bundle.putString(getContext().getString(R.string.pref_village_key), address);
                msg.setData(bundle);
                if(mHandler!=null) mHandler.sendMessage(msg);

                fulladdress = addressValue.getAsString("fullAddress");
                Utility.setCurrentAddress(getContext(),addressValue.getAsString("legalDong"));
                Utility.resetLocationStatus(getContext());

                requestBundle.setUrl(SKP_WEATHER_CURRENT_URL);
                ResponseMessage responseCurrentMsg = planetapi.request(requestBundle);
                Log.d(LOG_TAG,responseCurrentMsg.getStatusCode() + "  :  " + responseCurrentMsg.getResultMessage());

                requestBundle.setUrl(SKP_WEATHER_SUMMARY_URL);
                ResponseMessage responseSummaryMsg = planetapi.request(requestBundle);
                Log.d(LOG_TAG,responseSummaryMsg.getStatusCode() + "  :  " + responseSummaryMsg.getResultMessage());

                requestBundle.setUrl(SKP_WEATHER_8DAY_URL);
                ResponseMessage response8dayMsg = planetapi.request(requestBundle);
                Log.d(LOG_TAG,response8dayMsg.getStatusCode() + "  :  " + response8dayMsg.getResultMessage());


                requestBundle.setUrl(SKP_WEATHER_3DAY_URL);
                ResponseMessage response3dayMsg = planetapi.request(requestBundle);
                Log.d(LOG_TAG,response3dayMsg.getStatusCode() + "  :  " + response3dayMsg.getResultMessage());

                requestBundle.setUrl(SKP_WEATHER_3HOUR_URL);
                ResponseMessage response3hourMsg = planetapi.request(requestBundle);
                Log.d(LOG_TAG, response3hourMsg.getStatusCode() + "  :  " + response3hourMsg.getResultMessage());

                if(responseCurrentMsg.getStatusCode().equals(API_SUCCESS) && responseSummaryMsg.getStatusCode().equals(API_SUCCESS)
                        && response8dayMsg.getStatusCode().equals(API_SUCCESS) && response3dayMsg.getStatusCode().equals(API_SUCCESS)
                        && response3hourMsg.getStatusCode().equals(API_SUCCESS)) {

                    ContentValues currentWeather = getCurrentWeatherDataFromJson(responseCurrentMsg.getResultMessage(), skplanetWeatherData);

                    getSummaryWeatherDataFromJson(responseSummaryMsg.getResultMessage(), skplanetWeatherData);
                    getWeather8DayDataFromJson(response8dayMsg.getResultMessage(), skplanetWeatherData);
                    currentWeather.put(WeatherContract.TemperatureEntry.COLUMN_HOUR,0);
                    skplanetShortForcastData.add(currentWeather);
                    getWeather3HourDataFromJson(response3hourMsg.getResultMessage(), skplanetShortForcastData);
                    getWeather3DayDataFromJson(response3dayMsg.getResultMessage(), skplanetShortForcastData);
                }else {
                    Log.e(LOG_TAG, "sk planet api call error:" + responseSummaryMsg.getStatusCode() + ":"  + response8dayMsg.getStatusCode());
                }
            }
        } catch (PlanetXSDKException e) {
            Log.e(LOG_TAG, "sk planet api call error:" + e.getMessage());
        }
        
     // SK Planet API	//        
        String locationQuery = Utility.getCurrentAddress(getContext());

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        int numDays = 10;

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast
            final String FORECAST_BASE_URL =
                    "http://api.openweathermap.org/data/2.5/forecast/daily?";
            final String QUERY_PARAM = "q";
            final String FORMAT_PARAM = "mode";
            final String UNITS_PARAM = "units";
            final String DAYS_PARAM = "cnt";
            final String APPID_PARAM = "appid";

            Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(FORMAT_PARAM, format)
                    .appendQueryParameter(UNITS_PARAM, units)
                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                    .appendQueryParameter(APPID_PARAM, "905984a57c8836581a235048471b18be")
                    .appendQueryParameter(LATITUDE, "37.5714000000")
                    .appendQueryParameter(LONGITUDE, "126.9658000000")
                    .build();

            URL url = new URL(builtUri.toString());

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                return;
            }
            forecastJsonStr = buffer.toString();
            Log.d(LOG_TAG,forecastJsonStr);            
            long id = getWeatherDataFromJson(forecastJsonStr, locationQuery,fulladdress, address, skplanetWeatherData);
            if(id!=-1) {
                Log.d(LOG_TAG,"insert 하기 전 current id:" + id);
                int count = insertShortForcastData(skplanetShortForcastData,id);
                Log.d(LOG_TAG,"bulk insert tody current count:" + count);
            }

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attempting
            // to parse it.
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }
        return;
    }

    private int insertShortForcastData(Vector<ContentValues> weatherData,long id) {
        int inserted = 0;
        // add to database
        if ( weatherData.size() > 0 ) {
                ContentValues[] cvArray = new ContentValues[weatherData.size()];
                weatherData.toArray(cvArray);
                for(int i=0; i<cvArray.length; ++i) {
                    ContentValues value = cvArray[i];
                    value.put(WeatherContract.TemperatureEntry.COLUMN_CURRENT_TEMP_ID,id);
                    cvArray[i] = value;
//                    int hour = value.getAsInteger(WeatherContract.TemperatureEntry.COLUMN_HOUR);
//                    double temp = value.getAsDouble(WeatherContract.TemperatureEntry.COLUMN_TEMPERATURE);
//                    double wspd = value.getAsDouble(WeatherContract.TemperatureEntry.COLUMN_WINDSPEED);
//                    double wdir = value.getAsDouble(WeatherContract.TemperatureEntry.COLUMN_WINDDIRECTION);
//                    double humidity = value.getAsDouble(WeatherContract.TemperatureEntry.COLUMN_HUMIDITY);
//                    String code  = value.getAsString(WeatherContract.TemperatureEntry.COLUMN_CODE);
//                    String shordesc  = value.getAsString(WeatherContract.TemperatureEntry.COLUMN_NAME);
//                    Log.d(LOG_TAG,"today current data:"+hour +" temp:" + temp + " code:" + code + " shortdesc:" + shordesc + " wspd:" + wspd + " humidity:" + humidity + " wdir:" + wdir);
                }
                // delete old data so we don't build up an endless history
//                getContext().getContentResolver().delete(WeatherContract.TemperatureEntry.CONTENT_URI,
//                        WeatherContract.TemperatureEntry.COLUMN_CURRENT_TEMP_ID + " = ?", new String[]{Long.toString(id)});

            try {
                inserted = getContext().getContentResolver().bulkInsert(WeatherContract.TemperatureEntry.CONTENT_URI, cvArray);
            }catch (Exception e) {
                Log.e(LOG_TAG,"bulk insert error:" + e.getMessage());
            }
        }

        return inserted;
    }

    private ContentValues getAddressTmapDatafromJson(String tmapJsonStr) {
        Log.d(LOG_TAG,tmapJsonStr);
        ContentValues contentValues = new ContentValues();
        try {
            JSONObject addressJson = new JSONObject(tmapJsonStr);
            JSONObject addressInfo = addressJson.getJSONObject("addressInfo");
            String legalDong = addressInfo.getString("legalDong");
            String fulladdress = addressInfo.getString("city_do") + " "
                    + addressInfo.getString("gu_gun") + " " +legalDong;
            contentValues.put("fullAddress",fulladdress);
            contentValues.put("legalDong",legalDong);

            Log.d(LOG_TAG,"tmap address:" + legalDong);
            return contentValues;
        }catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }

        return null;
    }

    private void getWeather3HourDataFromJson(String forecastJsonStr,Vector<ContentValues> skplanetWeatherData) {
        Log.d(LOG_TAG,forecastJsonStr);

        final String SKP_WEATHER = "weather";
        final String SKP_FORECAST3HOURS = "forecast3hours";

        final String SKP_FORECAST3HOUR = "fcst3hour";

        final String SKP_SKY = "sky";
        final String SKP_TEMPERATURE = "temperature";

        final String SKP_TEMP = "temp";
        final String SKP_NAME = "name";
        final String SKP_CODE = "code";
        final String SKP_SHORT_DESC = "short_desc";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONObject weather = forecastJson.getJSONObject(SKP_WEATHER);
            JSONArray forecast3hours = weather.getJSONArray(SKP_FORECAST3HOURS);

            if(forecast3hours!=null && forecast3hours.length()>0) {
                JSONObject dayForecast = forecast3hours.getJSONObject(0);
                JSONObject sky = dayForecast.getJSONObject(SKP_SKY);

                String copyDesc = null;
                String copycode = null;
                int cnt = 0;

                for(int i=1;i<=3;i++) {
                    ContentValues value = new ContentValues();
                    String shortDescName = SKP_NAME + i + "hour";
                    String codeName = SKP_CODE + i + "hour";
                    String shortDesc = sky.getString(shortDescName);
                    String code = sky.getString(codeName);

                    if(!code.isEmpty()) {
                        value.put(WeatherContract.TemperatureEntry.COLUMN_NAME, shortDesc);
                        value.put(WeatherContract.TemperatureEntry.COLUMN_CODE, code);
                        value.put(WeatherContract.TemperatureEntry.COLUMN_HOUR, i);
                        copyDesc = shortDesc;
                        copycode = code;
                        skplanetWeatherData.add(value);
                    }else break;

                    cnt++;
                }

                for(int i=cnt;i<3;++i) {
                    ContentValues value = new ContentValues();
                    value.put(WeatherContract.TemperatureEntry.COLUMN_NAME, copyDesc);
                    value.put(WeatherContract.TemperatureEntry.COLUMN_CODE, copycode);
                    value.put(WeatherContract.TemperatureEntry.COLUMN_HOUR,i);
                    skplanetWeatherData.add(value);
                }
            }
        }catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private double[] getForecast2(double now, double next) {
        double[] result = new double[2];
        double diff = (next-now)/3;
        diff = Math.round(diff/0.1)*0.1;
        result[0] = now + diff;
        result[1] = now + diff*2;
        return result;
    }

    private double[] getForecast3(double now, double next) {
        double[] result = new double[3];
        double diff = (next-now)/4;
        diff = Math.round(diff/0.1)*0.1;
        result[0] = now + diff;
        result[1] = now + diff*2;
        result[2] = now + diff*3;
        return result;
    }

    private void getWeather3DayDataFromJson(String forecastJsonStr,Vector<ContentValues> skplanetWeatherData) {
        Log.d(LOG_TAG,forecastJsonStr);

        final String SKP_WEATHER = "weather";
        final String SKP_FORECAST3DAY = "forecast3days";

        final String SKP_FORECAST3HOUR = "fcst3hour";

        final String SKP_SKY = "sky";
        final String SKP_TEMPERATURE = "temperature";
        final String SKP_WIND = "wind";
        final String SKP_TEMP = "temp";
        final String SKP_WSPD = "wspd";
        final String SKP_WDIR = "wdir";
        final String HUMIDITY = "rh";
        final String SKP_HUMIDITY = "humidity";
        final String SKP_NAME = "name";
        final String SKP_CODE = "code";
        final String SKP_SHORT_DESC = "short_desc";

        double curTemp = skplanetWeatherData.get(0).getAsDouble(WeatherContract.TemperatureEntry.COLUMN_TEMPERATURE);
        double curWspd = skplanetWeatherData.get(0).getAsDouble(WeatherContract.TemperatureEntry.COLUMN_WINDSPEED);
        double curWdir = skplanetWeatherData.get(0).getAsDouble(WeatherContract.TemperatureEntry.COLUMN_WINDDIRECTION);
        double curHumidity = skplanetWeatherData.get(0).getAsDouble(WeatherContract.TemperatureEntry.COLUMN_HUMIDITY);

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONObject weather = forecastJson.getJSONObject(SKP_WEATHER);
            JSONArray forecast3days = weather.getJSONArray(SKP_FORECAST3DAY);

            if(forecast3days!=null && forecast3days.length()>0) {
                JSONObject dayForecast = forecast3days.getJSONObject(0);
                JSONObject fcst3hour = dayForecast.getJSONObject(SKP_FORECAST3HOUR);
                JSONObject temperature = fcst3hour.getJSONObject(SKP_TEMPERATURE);
                JSONObject wind = fcst3hour.getJSONObject(SKP_WIND);
                JSONObject humidity = fcst3hour.getJSONObject(SKP_HUMIDITY);
                JSONObject sky = fcst3hour.getJSONObject(SKP_SKY);

                double forcastTemp = temperature.getDouble(SKP_TEMP + 4 + "hour");
                double forcastWspd = wind.getDouble(SKP_WSPD + 4 + "hour");
                double forcastWdir = wind.getDouble(SKP_WDIR + 4 + "hour");
                double forcastHumidity = humidity.getDouble(HUMIDITY + 4 + "hour");
                double[] temp1_3 = getForecast3(curTemp, forcastTemp);
                double[] wspd1_3 = getForecast3(curWspd, forcastWspd);
                double[] wdir1_3 = getForecast3(curWdir, forcastWdir);
                double[] humidity1_3 = getForecast3(curHumidity, forcastHumidity);

                for(int i=1;i<=temp1_3.length;++i) {
                    ContentValues content = skplanetWeatherData.get(i);
                    content.put(WeatherContract.TemperatureEntry.COLUMN_TEMPERATURE, temp1_3[i-1]);
                    content.put(WeatherContract.TemperatureEntry.COLUMN_WINDSPEED, wspd1_3[i-1]);
                    content.put(WeatherContract.TemperatureEntry.COLUMN_WINDDIRECTION, wdir1_3[i-1]);
                    content.put(WeatherContract.TemperatureEntry.COLUMN_HUMIDITY, humidity1_3[i-1]);
                    content.put(WeatherContract.TemperatureEntry.COLUMN_HOUR,i);
                    skplanetWeatherData.setElementAt(content,i);
                }

                for(int i=4;i<=67;i+=3) {
                    ContentValues value = new ContentValues();
                    String shortDescName = SKP_NAME + i + "hour";
                    String codeName = SKP_CODE + i + "hour";
                    String shortDesc = sky.getString(shortDescName);
                    String code = sky.getString(codeName);

                    if(code.isEmpty()) break;//현재 값이 존재 하지 않는다면

                    curTemp = temperature.getDouble(SKP_TEMP + i + "hour");
                    curWspd = wind.getDouble(SKP_WSPD + i + "hour");
                    curWdir = wind.getDouble(SKP_WDIR + i + "hour");
                    curHumidity = humidity.getDouble(HUMIDITY + i + "hour");
                    value.put(WeatherContract.TemperatureEntry.COLUMN_NAME, shortDesc);
                    value.put(WeatherContract.TemperatureEntry.COLUMN_CODE, code);
                    value.put(WeatherContract.TemperatureEntry.COLUMN_TEMPERATURE, curTemp);
                    value.put(WeatherContract.TemperatureEntry.COLUMN_WINDSPEED, curWspd);
                    value.put(WeatherContract.TemperatureEntry.COLUMN_WINDDIRECTION, curWdir);
                    value.put(WeatherContract.TemperatureEntry.COLUMN_HUMIDITY, curHumidity);
                    value.put(WeatherContract.TemperatureEntry.COLUMN_HOUR, i);
                    skplanetWeatherData.add(value);

                    if(i<67 && !sky.getString(SKP_CODE + (i + 3) + "hour").isEmpty()) {//3시간 뒤 값이 존재 한다면 현재값과 3시간 뒤 값을 가지고 중간값들을 구함
                        forcastTemp = temperature.getDouble(SKP_TEMP + (i + 3) + "hour");
                        forcastWspd = wind.getDouble(SKP_WSPD + (i + 3) + "hour");
                        forcastWdir = wind.getDouble(SKP_WDIR + (i + 3) + "hour");
                        forcastHumidity = humidity.getDouble(HUMIDITY + (i + 3) + "hour");
                        double[] tempForcast2hour = getForecast2(curTemp, forcastTemp);
                        double[] wspdForcast2hour = getForecast2(curWspd, forcastWspd);
                        double[] wdirForcast2hour = getForecast2(curWspd, forcastWdir);
                        double[] humidityForcast2hour = getForecast2(curHumidity, forcastHumidity);

                        for (int j = 1; j <= tempForcast2hour.length; ++j) {
                            ContentValues value2 = new ContentValues();
                            value2.put(WeatherContract.TemperatureEntry.COLUMN_TEMPERATURE, tempForcast2hour[j-1]);
                            value2.put(WeatherContract.TemperatureEntry.COLUMN_WINDSPEED, wspdForcast2hour[j-1]);
                            value2.put(WeatherContract.TemperatureEntry.COLUMN_WINDDIRECTION, wdirForcast2hour[j-1]);
                            value2.put(WeatherContract.TemperatureEntry.COLUMN_HUMIDITY, humidityForcast2hour[j-1]);
                            value2.put(WeatherContract.TemperatureEntry.COLUMN_NAME, shortDesc);
                            value2.put(WeatherContract.TemperatureEntry.COLUMN_CODE, code);
                            value2.put(WeatherContract.TemperatureEntry.COLUMN_HOUR,i+j);
                            skplanetWeatherData.add(value2);
                        }

                    }
                }
            }
        }catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private void getWeather8DayDataFromJson(String forecastJsonStr,Vector<ContentValues> skplanetWeatherData) {
    	Log.d(LOG_TAG,forecastJsonStr);
    	
    	final String SKP_WEATHER = "weather";
    	final String SKP_FORECAST6DAY = "forecast6days";
    	final String SKP_SKY = "sky";
    	final String SKP_TEMPERATURE = "temperature";
    	
    	final String SKP_TMAX = "tmax";
    	final String SKP_TMIN = "tmin";
    	final String SKP_NAME = "amName";
    	final String SKP_CODE = "amCode";
    	
    	final String SKP_MAX = "max";
    	final String SKP_MIN = "min";
    	final String SKP_SHORT_DESC = "short_desc";
        
        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONObject weather = forecastJson.getJSONObject(SKP_WEATHER);
            JSONArray forecast6days = weather.getJSONArray(SKP_FORECAST6DAY);
            
            if(forecast6days!=null && forecast6days.length()>0) {
            	JSONObject dayForecast = forecast6days.getJSONObject(0);  	            	
            	JSONObject temperature = dayForecast.getJSONObject(SKP_TEMPERATURE);
            	JSONObject sky = dayForecast.getJSONObject(SKP_SKY);            	            	                       
            	for(int i=3;i<=10;++i) {
            		ContentValues value = new ContentValues();
            		String maxTempName = SKP_TMAX+i+"day";
            		String minTempName = SKP_TMIN+i+"day";
            		String shortDescName = SKP_NAME + i + "day";
            		String codeName = SKP_CODE + i + "day";
            		long maxtemp = temperature.getLong(maxTempName);
            		long mintemp = temperature.getLong(minTempName);
            		String shortDesc = sky.getString(shortDescName);
            		String code = sky.getString(codeName);
            		            		
            		value.put(SKP_MAX, maxtemp);
            		value.put(SKP_MIN, mintemp);
            		value.put(SKP_SHORT_DESC, shortDesc);
            		value.put(WeatherContract.WeatherEntry.COLUMN_CODE, code);
            		skplanetWeatherData.add(value);
            	}            	
            }             
        }catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();            
        }    	
    }

    private ContentValues getCurrentWeatherDataFromJson(String forecastJsonStr,Vector<ContentValues> skplanetWeatherData){
    	
    	final String SKP_WEATHER 		= "weather";
    	final String SKP_GRID 			= "grid";
    	final String SKP_HOURLY 		= "hourly";
    	final String SKP_SKY 			= "sky";
    	final String SKP_TEMPERATURE 	= "temperature";
    	final String SKP_VILLAGE 		= "village";
    	final String SKP_CITY 			= "city";
    	final String SKP_COUNTY 		= "county";
    	
    	final String SKP_TMAX 	= "tmax";
    	final String SKP_TMIN 	= "tmin";
    	final String SKP_TCUR 	= "tc";
    	final String SKP_NAME 	= "name";    	
    	String address = null;    	
    	try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONObject weather = forecastJson.getJSONObject(SKP_WEATHER);
            JSONArray hourly = weather.getJSONArray(SKP_HOURLY);
            
            if(hourly!=null && hourly.length()>0) {
            	JSONObject dayForecast = hourly.getJSONObject(0);
            	JSONObject grid = dayForecast.getJSONObject(SKP_GRID);
            	String city = grid.getString(SKP_CITY);
            	String county = grid.getString(SKP_COUNTY);
            	String village = grid.getString(SKP_VILLAGE);
            	address = city + " " +county +" " + village;

            	JSONObject sky = dayForecast.getJSONObject(SKP_SKY);
            	JSONObject temperature = dayForecast.getJSONObject(SKP_TEMPERATURE);            	
            	double tMax = temperature.getDouble(SKP_TMAX);
            	double tMin = temperature.getDouble(SKP_TMIN);
            	JSONObject wind = dayForecast.getJSONObject(SKP_WIND);
            	double wdir = wind.getDouble(SKP_WDIR);
            	double wspd = wind.getDouble(SKP_WSPD);
            	double humidity = dayForecast.getDouble(SKP_HUMIDITY);
            	double tc = temperature.getDouble(SKP_TCUR);
            	String shorDesc = sky.getString(SKP_NAME);
            	String code = sky.getString(WeatherContract.WeatherEntry.COLUMN_CODE);
            	
        		ContentValues value = new ContentValues();
            	
            	value.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, tMax);
            	value.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, tMin);
            	value.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, shorDesc);
            	value.put(WeatherContract.WeatherEntry.COLUMN_CODE, code);
            	value.put(SKP_HUMIDITY, humidity);
            	value.put(WeatherContract.WeatherEntry.COLUMN_CUR_TEMP, tc);
            	value.put(SKP_WDIR, wdir);
            	value.put(SKP_WSPD, wspd);
            	skplanetWeatherData.add(value);

                //현재 온도
                ContentValues result = new ContentValues();
                result.put(WeatherContract.TemperatureEntry.COLUMN_NAME, shorDesc);
                result.put(WeatherContract.TemperatureEntry.COLUMN_CODE, code);
                result.put(WeatherContract.TemperatureEntry.COLUMN_TEMPERATURE, tc);
                result.put(WeatherContract.TemperatureEntry.COLUMN_HUMIDITY, humidity);
                result.put(WeatherContract.TemperatureEntry.COLUMN_WINDSPEED, wspd);
                result.put(WeatherContract.TemperatureEntry.COLUMN_WINDDIRECTION, wdir);

                return result;
            }
    	}catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();            
        }
    	
    	return null;
    }
    
    private String getSummaryWeatherDataFromJson(String forecastJsonStr,Vector<ContentValues> skplanetWeatherData) {
    	final String SKP_WEATHER = "weather";
    	final String SKP_GRID = "grid";
    	final String SKP_SUMMARY = "summary";
    	final String SKP_VILLAGE = "village";
    	final String SKP_TOMMOROW = "tomorrow";
    	final String SKP_DAYAFTERTOMORROW = "dayAfterTomorrow";
    	final String SKP_SKY = "sky";
    	final String SKP_TEMPERATURE = "temperature";
    	
    	final String SKP_TMAX = "tmax";
    	final String SKP_TMIN = "tmin";
    	final String SKP_NAME = "name";
    	
    	String address = null;
    	
    	try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONObject weather = forecastJson.getJSONObject(SKP_WEATHER);
            JSONArray summary = weather.getJSONArray(SKP_SUMMARY);
            
            if(summary!=null && summary.length()>0) {
            	JSONObject dayForecast = summary.getJSONObject(0);
            	JSONObject grid = dayForecast.getJSONObject(SKP_GRID);
//            	String city = grid.getString("city");
//            	String county = grid.getString("county");
            	String village = grid.getString(SKP_VILLAGE);
            	address = village;
            	
            	String dayName = SKP_TOMMOROW;
            	
            	for(int i=0;i<2; ++i) {
            		if(i==0) dayName = SKP_TOMMOROW;
            		else if(i==1) dayName = SKP_DAYAFTERTOMORROW;
            		
            		ContentValues value = new ContentValues();            	
                	JSONObject day = dayForecast.getJSONObject(dayName);
                	JSONObject sky = day.getJSONObject(SKP_SKY);
                	JSONObject temperature = day.getJSONObject(SKP_TEMPERATURE);
                	
                	double tMax = temperature.getDouble(SKP_TMAX);
                	double tMin = temperature.getDouble(SKP_TMIN);
                	String shorDesc = sky.getString(SKP_NAME);
                	String code = sky.getString(WeatherContract.WeatherEntry.COLUMN_CODE);
                	
                	value.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, tMax);
                	value.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, tMin);
                	value.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, shorDesc);
                	value.put(WeatherContract.WeatherEntry.COLUMN_CODE, code);
                	skplanetWeatherData.add(value);
            	}            	                        	            	
            }
    	}catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();            
        }
    	
    	return address;
    }

    
    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     *
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private long getWeatherDataFromJson(String forecastJsonStr,String locationSetting,
                                        String fullAddres, String address,Vector<ContentValues> skplanetWeatherData)
            throws JSONException {

        long id = -1;//today ID(weatherEntry Contact)
        // Now we have a String representing the complete forecast in JSON Format.
        // Fortunately parsing is easy:  constructor takes the JSON string and converts it
        // into an Object hierarchy for us.

        // These are the names of the JSON objects that need to be extracted.

        // Location information
        final String OWM_CITY = "city";
        final String OWM_CITY_NAME = "name";
        final String OWM_COORD = "coord";

        // Location coordinate
        final String OWM_LATITUDE = "lat";
        final String OWM_LONGITUDE = "lon";

        // Weather information.  Each day's forecast info is an element of the "list" array.
        final String OWM_LIST = "list";

        final String OWM_PRESSURE = "pressure";
        final String OWM_HUMIDITY = "humidity";
        final String OWM_WINDSPEED = "speed";
        final String OWM_WIND_DIRECTION = "deg";

        // All temperatures are children of the "temp" object.
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";

        final String OWM_WEATHER = "weather";
        final String OWM_DESCRIPTION = "main";
        final String OWM_WEATHER_ID = "id";

        final String OWM_MESSAGE_CODE = "cod";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            // do we have an error?
            if ( forecastJson.has(OWM_MESSAGE_CODE) ) {
                int errorCode = forecastJson.getInt(OWM_MESSAGE_CODE);

                switch (errorCode) {
                    case HttpURLConnection.HTTP_OK:
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        setLocationStatus(getContext(), LOCATION_STATUS_INVALID);
                        return -1;
                    default:
                        setLocationStatus(getContext(), LOCATION_STATUS_SERVER_DOWN);
                        return -1;
                }
            }

            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            JSONObject cityJson = forecastJson.getJSONObject(OWM_CITY);
//            String cityName = cityJson.getString(OWM_CITY_NAME);

            JSONObject cityCoord = cityJson.getJSONObject(OWM_COORD);
            double cityLatitude = cityCoord.getDouble(OWM_LATITUDE);
            double cityLongitude = cityCoord.getDouble(OWM_LONGITUDE);

            long locationId = addLocation(locationSetting, fullAddres, address, cityLatitude, cityLongitude);

            // Insert the new weather information into the database
            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            for(int i = 0; i < weatherArray.length(); i++) {
                // These are the values that will be collected.
                long dateTime;
                double pressure;
                int humidity;
                double windSpeed;
                double windDirection;

                double high;
                double low;

                String description;
                String code = null;
                int weatherId;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);

                pressure = dayForecast.getDouble(OWM_PRESSURE);
                humidity = dayForecast.getInt(OWM_HUMIDITY);
                windSpeed = dayForecast.getDouble(OWM_WINDSPEED);
                windDirection = dayForecast.getDouble(OWM_WIND_DIRECTION);

                // Description is in a child array called "weather", which is 1 element long.
                // That element also contains a weather code.
                JSONObject weatherObject =
                        dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);
                weatherId = weatherObject.getInt(OWM_WEATHER_ID);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                high = temperatureObject.getDouble(OWM_MAX);
                low = temperatureObject.getDouble(OWM_MIN);

                ContentValues weatherValues = new ContentValues();
                               
                if(i>=0 && i<=10) {
//                if(skplanetWeatherData!=null && skplanetWeatherData.size()>=10 && i>=0 && i<=10) {
                	// SK planet���� ������ ��� ���� ���� �ִ´�.
                	ContentValues value = skplanetWeatherData.get(i);
                	high = value.getAsDouble(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP);
                	low = value.getAsDouble(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP);
                	description = value.getAsString(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC);
                	code = value.getAsString(WeatherContract.WeatherEntry.COLUMN_CODE);
                }
                
                if(i==0) {//today�̸� current ���� ������ �´�.
                	ContentValues value = skplanetWeatherData.get(i);
                	windSpeed = value.getAsDouble(SKP_WSPD);
                	windDirection = value.getAsDouble(SKP_WDIR);
                	double humidity_temp= value.getAsDouble(SKP_HUMIDITY);
                	humidity = (int)humidity_temp;
                	double currentTemp = value.getAsDouble(WeatherContract.WeatherEntry.COLUMN_CUR_TEMP);
                	weatherValues.put(WeatherContract.WeatherEntry.COLUMN_CUR_TEMP, currentTemp);
                }

                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, dateTime);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, high);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, low);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_CODE, code);
                cVVector.add(weatherValues);
            }

            int inserted = 0;
            // add to database
            if ( cVVector.size() > 0 ) {
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);

                // 기존에 있는 temerature table에 있는 값들을 쌓이지 않도록 지워줌
                Cursor data = getContext().getContentResolver().query(WeatherContract.WeatherEntry.CONTENT_URI,
                        new String[] {WeatherContract.WeatherEntry._ID},
                        WeatherContract.WeatherEntry.COLUMN_DATE + " = ? AND " + WeatherContract.WeatherEntry.COLUMN_LOC_KEY + " = ?" ,
                        new String[] {Long.toString(dayTime.setJulianDay(julianStartDay)), Long.toString(locationId)},
                        null);

                if(data!=null && data.moveToFirst()) {
                    id = data.getLong(0);
                    Log.d(LOG_TAG,"delete previous current temp id:" + id);
                    // delete old data so we don't build up an endless history
                    getContext().getContentResolver().delete(WeatherContract.TemperatureEntry.CONTENT_URI,
                            WeatherContract.TemperatureEntry.COLUMN_CURRENT_TEMP_ID + " = ?", new String[]{Long.toString(id)});
                }
                data.close();

                getContext().getContentResolver().bulkInsert(WeatherContract.WeatherEntry.CONTENT_URI, cvArray);
                // delete old data so we don't build up an endless history
                getContext().getContentResolver().delete(WeatherContract.WeatherEntry.CONTENT_URI,
                        WeatherContract.WeatherEntry.COLUMN_DATE + " <= ?",
                        new String[]{Long.toString(dayTime.setJulianDay(julianStartDay - 1))});

                data = getContext().getContentResolver().query(WeatherContract.WeatherEntry.CONTENT_URI,
                        new String[] {WeatherContract.WeatherEntry._ID},
                        WeatherContract.WeatherEntry.COLUMN_DATE + " = ? AND " + WeatherContract.WeatherEntry.COLUMN_LOC_KEY + " = ?" ,
                        new String[] {Long.toString(dayTime.setJulianDay(julianStartDay)), Long.toString(locationId)},
                        null);
                if(data!=null && data.moveToFirst()) {
                    id = data.getLong(0);
                    Log.d(LOG_TAG,"weathcontract today id:" + id);
                }

                updateWidgets();
                updateMuzei();
                notifyWeather();
            }
            Log.d(LOG_TAG, "Sync Complete. " + cVVector.size() + " Inserted");
            setLocationStatus(getContext(), LOCATION_STATUS_OK);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            setLocationStatus(getContext(), LOCATION_STATUS_SERVER_INVALID);
        }

        return id;
    }

    private void updateWidgets() {
        Context context = getContext();
        // Setting the package ensures that only components in our app will receive the broadcast
        Intent dataUpdatedIntent = new Intent(ACTION_DATA_UPDATED)
                .setPackage(context.getPackageName());
        context.sendBroadcast(dataUpdatedIntent);
    }

    private void updateMuzei() {
        // Muzei is only compatible with Jelly Bean MR1+ devices, so there's no need to update the
        // Muzei background on lower API level devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Context context = getContext();
            context.startService(new Intent(ACTION_DATA_UPDATED)
                    .setClass(context, WeatherMuzeiSource.class));
        }
    }

    private void notifyWeather() {
        Context context = getContext();
        //checking the last update and notify if it' the first of the day
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String displayNotificationsKey = context.getString(R.string.pref_enable_notifications_key);
        boolean displayNotifications = prefs.getBoolean(displayNotificationsKey,
                Boolean.parseBoolean(context.getString(R.string.pref_enable_notifications_default)));

        if ( displayNotifications ) {

            String lastNotificationKey = context.getString(R.string.pref_last_notification);
            long lastSync = prefs.getLong(lastNotificationKey, 0);

            if (System.currentTimeMillis() - lastSync >= DAY_IN_MILLIS) {
                // Last sync was more than 1 day ago, let's send a notification with the weather.
                String locationQuery = Utility.getCurrentAddress(context);

                Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

                // we'll query our contentProvider, as always
                Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

                if (cursor.moveToFirst()) {
                    int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                    double high = cursor.getDouble(INDEX_MAX_TEMP);
                    double low = cursor.getDouble(INDEX_MIN_TEMP);
                    double cur = cursor.getDouble(INDEX_CUR_TEMP);
                    String desc = cursor.getString(INDEX_SHORT_DESC);
                    String code = cursor.getString(INDEX_CODE);

                    int iconId = Utility.getIconResourceForCodeCondition(code);
                    Resources resources = context.getResources();
                    int artResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
                    String artUrl = Utility.getArtUrlForWeatherCondition(context, weatherId);

                    // On Honeycomb and higher devices, we can retrieve the size of the large icon
                    // Prior to that, we use a fixed size
                    @SuppressLint("InlinedApi")
                    int largeIconWidth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
                            ? resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
                            : resources.getDimensionPixelSize(R.dimen.notification_large_icon_default);
                    @SuppressLint("InlinedApi")
                    int largeIconHeight = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
                            ? resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
                            : resources.getDimensionPixelSize(R.dimen.notification_large_icon_default);

                    // Retrieve the large icon
                    Bitmap largeIcon;
                    try {
                        largeIcon = Glide.with(context)
                                .load(artUrl)
                                .asBitmap()
                                .error(artResourceId)
                                .fitCenter()
                                .into(largeIconWidth, largeIconHeight).get();
                    } catch (InterruptedException | ExecutionException e) {
                        Log.e(LOG_TAG, "Error retrieving large icon from " + artUrl, e);
                        largeIcon = BitmapFactory.decodeResource(resources, artResourceId);
                    }
                    String title = context.getString(R.string.app_name);

                    // Define the text of the forecast.
                    String contentText = String.format(context.getString(R.string.format_notification),
                            desc,
                            Utility.formatTemperature(context, high),
                            Utility.formatTemperature(context, low));

                    // NotificationCompatBuilder is a very convenient way to build backward-compatible
                    // notifications.  Just throw in some data.
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(getContext())
                                    .setColor(resources.getColor(R.color.primary_light))
                                    .setSmallIcon(iconId)
                                    .setLargeIcon(largeIcon)
                                    .setContentTitle(title)
                                    .setContentText(contentText);

                    // Make something interesting happen when the user clicks on the notification.
                    // In this case, opening the app is sufficient.
                    Intent resultIntent = new Intent(context, MainActivity.class);

                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                    stackBuilder.addNextIntent(resultIntent);
                    PendingIntent resultPendingIntent =
                            stackBuilder.getPendingIntent(
                                    0,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            );
                    mBuilder.setContentIntent(resultPendingIntent);

                    NotificationManager mNotificationManager =
                            (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    // WEATHER_NOTIFICATION_ID allows you to update the notification later on.
                    mNotificationManager.notify(WEATHER_NOTIFICATION_ID, mBuilder.build());

                    //refreshing last sync
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(lastNotificationKey, System.currentTimeMillis());
                    editor.commit();
                }
                cursor.close();
            }
        }
    }

    /**
     * Helper method to handle insertion of a new location in the weather database.
     *
     * @param locationSetting The location string used to request updates from the server.
     * @param cityName A human-readable city name, e.g "Mountain View"
     * @param lat the latitude of the city
     * @param lon the longitude of the city
     * @return the row ID of the added location.
     */
    long addLocation(String locationSetting, String fullAddress, String cityName, double lat, double lon) {
        long locationId;

        // First, check if the location with this city name exists in the db
        Cursor locationCursor = getContext().getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID},
                WeatherContract.LocationEntry.COLUMN_CITY_NAME + " = ?",
                new String[]{cityName},
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);
        } else {
            // Now that the content provider is set up, inserting rows of data is pretty simple.
            // First create a ContentValues object to hold the data you want to insert.
            ContentValues locationValues = new ContentValues();

            // Then add the data, along with the corresponding name of the data type,
            // so the content provider knows what kind of value is being inserted.
            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_FULL_ADDRESS_NAME, fullAddress);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LAT, lat);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LONG, lon);

            // Finally, insert location data into the database.
            Uri insertedUri = getContext().getContentResolver().insert(
                    WeatherContract.LocationEntry.CONTENT_URI,
                    locationValues
            );

            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            locationId = ContentUris.parseId(insertedUri);
        }

        locationCursor.close();
        // Wait, that worked?  Yes!
        return locationId;
    }

    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account,
                    authority, new Bundle(), syncInterval);
        }
    }

    /**
     * Helper method to have the sync adapter sync immediately
     * @param context The context used to access the account service
     */
    public static void syncImmediately(Context context) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(getSyncAccount(context),
                context.getString(R.string.content_authority), bundle);
    }

    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
     */
    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if ( null == accountManager.getPassword(newAccount) ) {

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */

            onAccountCreated(newAccount, context);
        }
        return newAccount;
    }

    private static void onAccountCreated(Account newAccount, Context context) {
        /*
         * Since we've created an account
         */
        SunshineSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);

        /*
         * Without calling setSyncAutomatically, our periodic sync will not be enabled.
         */
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);

        /*
         * Finally, let's do a sync to get things started
         */
        syncImmediately(context);
    }

    public static void initializeSyncAdapter(Context context,Handler handler) {
        mHandler = handler;
        getSyncAccount(context);
    }

    /**
     * Sets the location status into shared preference.  This function should not be called from
     * the UI thread because it uses commit to write to the shared preferences.
     * @param c Context to get the PreferenceManager from.
     * @param locationStatus The IntDef value to set
     */
    static private void setLocationStatus(Context c, @LocationStatus int locationStatus){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt(c.getString(R.string.pref_location_status_key), locationStatus);
        spe.commit();
    }
}