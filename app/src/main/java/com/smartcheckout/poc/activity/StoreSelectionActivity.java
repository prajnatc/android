package com.smartcheckout.poc.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.samples.vision.barcodereader.BarcodeCaptureActivity;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.smartcheckout.poc.R;
import com.smartcheckout.poc.models.Store;
import com.smartcheckout.poc.models.Transaction;
import com.smartcheckout.poc.util.CommonUtils;
import com.smartcheckout.poc.util.SharedPreferrencesUtil;
import com.smartcheckout.poc.util.StateData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import cz.msebera.android.httpclient.Header;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static com.smartcheckout.poc.constants.constants.BARCODE_SEARCH_EP;
import static com.smartcheckout.poc.constants.constants.LOCATION_SEARCH_EP;
import static com.smartcheckout.poc.constants.constants.RC_CHECK_SETTING;
import static com.smartcheckout.poc.constants.constants.RC_LOCATION_PERMISSION;
import static com.smartcheckout.poc.constants.constants.RC_SCAN_BARCODE_STORE;
import static com.smartcheckout.poc.constants.constants.SP_TRANSACTION_ID;
import static com.smartcheckout.poc.constants.constants.TIMEOUT_TRANSACTION_MINS;


/**
 * Created by yeshwanth on 4/5/2017.
 */

public class StoreSelectionActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ActivityCompat.OnRequestPermissionsResultCallback, LocationListener  {

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private ProgressBar progressBar;

   // private static final int RC_SCAN_BARCODE_PROD = 100;

    private AsyncHttpClient ahttpClient = new AsyncHttpClient();
    private Store selectedStore;
    private boolean locationEnabled = false;
    private int locationRetryCount = 0;
    private int locationRetryLimit = 5;
    private static final String TAG = "StoreSelectionActivity";

    AnimationDrawable frameAnimation = null;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        System.out.println("In on Connected() --> Google APi client");
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(2000)
                .setFastestInterval(1000);
                //.setNumUpdates(locationRetryLimit);
        startLocationUpdates();
    }

    public void startLocationUpdates(){

        if(locationEnabled){

            if (ActivityCompat.checkSelfPermission(StoreSelectionActivity.this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){

                String[] requiredPermission = {ACCESS_FINE_LOCATION};
                ActivityCompat.requestPermissions(StoreSelectionActivity.this,requiredPermission,RC_LOCATION_PERMISSION);
            }else {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            }

        }else{
            enableLocationSettings();
        }

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        System.out.println("In on ConnectionFailed() --> Google APi client");
        createNoLocView(getResources().getString(R.string.no_connection_message));
    }

    @Override
    public void onConnectionSuspended(int i) {
        System.out.println("In on ConnectionSuspended() --> Google APi client");
        createNoLocView(getResources().getString(R.string.no_connection_message));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        setContentView(R.layout.cart_loading);
//        ImageView img = (ImageView)findViewById(R.id.cart_animation);
//        // Get the source, which has been compiled to an AnimationDrawable object.
//        frameAnimation = (AnimationDrawable) img.getDrawable();
//        // Start the animation (looped playback by default).
//        frameAnimation.start();

        super.onCreate(savedInstanceState);
        //Conenct Google API client. Will receive call back. See appropriate method for success or faliure
        connectGoogleApiClient();
    }

    @Override
    protected void onStart() {
        super.onStart();
        locationRetryCount = 0;
    }

    @Override
    protected void onStop() {
        super.onStop();

        if(frameAnimation != null)
            frameAnimation.stop();

        if(mGoogleApiClient!=null && mGoogleApiClient.isConnected()){
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, StoreSelectionActivity.this);
            mGoogleApiClient.disconnect();
        }
        ahttpClient.cancelAllRequests(true);

    }
    // Need to add code for on Pause and on Resume
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bundle bundle = data.getExtras();
        switch (requestCode) {
            case RC_SCAN_BARCODE_STORE:

                if (resultCode == RESULT_OK ) {
                    String barcode = bundle.getString("Barcode");
                    if(barcode != null) {
                        Log.d(TAG, "=====> Control returned from Scan Barcode Activity. Barcode : " + barcode);
                        findStoreByBarcode(barcode);
                    }
                }
                else if(resultCode == RESULT_CANCELED )
                {
                    String reason = bundle.getString("Reason");
                    if(reason != null && reason.equalsIgnoreCase("Timeout"))
                        Toast.makeText(this,getResources().getString(R.string.toast_scan_timedout),Toast.LENGTH_LONG).show();
                }
                break;
            case RC_CHECK_SETTING: // Response from location enabled
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        locationEnabled = true;
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        createNoLocView(getResources().getString(R.string.no_loc_perm_denied));
                        break;
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case RC_LOCATION_PERMISSION:
                if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    startLocationUpdates();
                }
                else  {
                    createNoLocView(getResources().getString(R.string.no_loc_scan_qr_store));
                }
        }
    }

    /**
     * Utility Method to enable location settings
     *
     * */
    public void enableLocationSettings(){

        LocationRequest request = new LocationRequest().setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(request);

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            //Location Setting Result Handler
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        locationEnabled = true;
                        startLocationUpdates();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            status.startResolutionForResult(StoreSelectionActivity.this,RC_CHECK_SETTING);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                          Log.d(TAG,e.getLocalizedMessage());
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        System.out.println("Resolution not possible");
                        createNoLocView(getResources().getString(R.string.no_loc_message));
                        break;
                }
            }
        });

    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    public void findStoreByLocation(final Location location){
      
        RequestParams params = new RequestParams();
        params.put("lattitude", location.getLatitude());
        params.put("longitude", location.getLongitude());
        params.put("context", "STORE_IN_CURRENT_LOC");
        Log.d(TAG,"Invoking findStoreByLocation with location : "+ location.getLatitude() + " : " + location.getLongitude());
        ahttpClient.setMaxRetriesAndTimeout(2,1000);
        ahttpClient.get(LOCATION_SEARCH_EP, params, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                System.out.println("In onSuccess SelectStore");
                try {
                    // Unique store found
                    if (response.length() == 1) {
                        selectedStore = new Store();
                        JSONObject store = response.getJSONObject(0);

                        selectedStore = new Gson().fromJson(store.toString(), Store.class);

                        StateData.storeId = selectedStore.getId();
                        StateData.storeName = selectedStore.getTitle();
                        launchCartActivity();
                    }
                } catch (JSONException je) {
                    je.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errResponse) {
                Log.d(TAG,"Unable to find store details. " + statusCode+" "+errResponse,throwable);
               }
        });
    }

    public void findStoreByBarcode(String barcode){
        //Get Product Details

        RequestParams params = new RequestParams();
        params.put("barcode", barcode);
        progressBar = (ProgressBar) findViewById(R.id.progressBarStoreScan);
        progressBar.setVisibility(View.VISIBLE);
        ahttpClient.setMaxRetriesAndTimeout(2,1000);
        ahttpClient.get(BARCODE_SEARCH_EP, params, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                selectedStore = new Store();
                try{
                    selectedStore.setDisplayAddress(response.getString("displayAddress"));
                    selectedStore.setId(response.getString("id"));
                    selectedStore.setTitle(response.getString("title"));
                    progressBar.setVisibility(View.GONE);
                    StateData.storeId = selectedStore.getId();
                    StateData.storeName = selectedStore.getTitle();
                    launchCartActivity();
                }catch(JSONException je ){
                    je.printStackTrace();

                }catch(Exception e){
                    e.printStackTrace();
                }
            }

            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errResponse) {
                Log.d(TAG,"Unable to find store details. " + statusCode+" "+errResponse,throwable);
                ((TextView)findViewById(R.id.noLocMessage)).setText(R.string.store_not_found);

            }
        });

    }


    //Methods to launch applications activities. scanType should be a predefined constant for store or product(i.e.RC_SCAN_BARCODE_STORE etc.)
    public void launchScanBarcode(int scanType){
        Intent barcodeScanIntent = new Intent(this,BarcodeCaptureActivity.class);
        barcodeScanIntent.putExtra("requestCode",scanType);
        startActivityForResult(barcodeScanIntent, scanType);
    }

    public void launchCartActivity(){
        StateData.store =selectedStore;
        final Intent cartActivityIntent = new Intent(this,CartActivity.class);
        cartActivityIntent.putExtra("StoreId",selectedStore.getId());
        cartActivityIntent.putExtra("StoreTitle",selectedStore.getTitle());
        cartActivityIntent.putExtra("StoreDisplayAddress", selectedStore.getDisplayAddress());


        // check if there is a pending transaction
         if (SharedPreferrencesUtil.getStringPreference(this,"TransactionId") != null )
         {
             Date lastTransactionDate = SharedPreferrencesUtil.getDatePreference(this,"TransactionUpdatedDate",null);

             // if the last transaction was left pending under "N" minutes
             long minute_diff = CommonUtils.getDifferenceinMinutes(lastTransactionDate,CommonUtils.getCurrentDate());
             Log.d("tag","last pending transaction in "+ minute_diff);
             if( minute_diff < TIMEOUT_TRANSACTION_MINS)
             {
                 StateData.transactionId =  SharedPreferrencesUtil.getStringPreference(this,"TransactionId");

                 AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                         StoreSelectionActivity.this,R.style.DialogTheme);

                 // set dialog message
                 alertDialogBuilder
                         .setMessage(R.string.saved_transaction_dialog)
                         .setCancelable(false)
                         .setPositiveButton(R.string.continue_transaction,new DialogInterface.OnClickListener() {
                             public void onClick(DialogInterface dialog,int id) {
                                 cartActivityIntent.putExtra("TransactionId", StateData.transactionId);
                                 startActivity(cartActivityIntent);

                             }
                         })
                         .setNegativeButton(R.string.start_over,new DialogInterface.OnClickListener() {
                             public void onClick(DialogInterface dialog,int id) {
                                 StateData.transactionId = null;
                                 SharedPreferrencesUtil.setStringPreference(getApplicationContext(),SP_TRANSACTION_ID, null);
                                 startActivity(cartActivityIntent);

                             }
                         });
                 // create alert dialog
                 AlertDialog alertDialog = alertDialogBuilder.create();
                 alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                 alertDialog.show();

             }
             else {
                 startActivity(cartActivityIntent);
             }

         }
         else
         {
             startActivity(cartActivityIntent);
         }

    }


    // Call back handler for receiving location updates
    @Override
    public void onLocationChanged(Location location) {

        Log.d(TAG,"Location Update received. Accuracy : "+ location.getAccuracy());
        System.out.println();
        locationRetryCount++;
        if(locationRetryCount <= locationRetryLimit){
            if(location.getAccuracy() <100){
                Log.d(TAG,"Accuracy lt 100. Invoking store selection by location");
                findStoreByLocation(location);
            }else{
                Log.d(TAG,"Accuracy gt 100. Defering store search by location.");
            }
        }else{
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, StoreSelectionActivity.this);
            createNoLocView(getResources().getString(R.string.no_loc_message));
        }
    }

    //Create the no location view
    public void createNoLocView(String message) {
        setTheme(R.style.AppTheme);
        setContentView(R.layout.no_loc_store_selection);
        ((TextView)findViewById(R.id.noLocMessage)).setText(message);
        //Set listener for the on store listener
        Button scanQRStore = (Button) findViewById(R.id.scanQrStore);
        //Need to add code to find locaiton from the QR code from the service
        scanQRStore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchScanBarcode(RC_SCAN_BARCODE_STORE);

            }
        });

    }

    public void connectGoogleApiClient() {
        System.out.println("In connectGoogleApiClient()");

        if (mGoogleApiClient == null) {
            System.out.println("GoogleApiClient null....Creating client");
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        //Connect the google API client
        System.out.println("GoogleApiClient not null");
        mGoogleApiClient.connect();
        System.out.println("Connection done");

    }

    @Override
    public void onBackPressed() {
    }

}
