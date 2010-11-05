/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.interactions;

import com.android.contacts.R;
import com.android.contacts.model.AccountTypes;
import com.android.contacts.model.BaseAccountType;
import com.google.android.collect.Sets;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Entity;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import java.util.HashSet;

/**
 * An interaction invoked to delete a contact.
 */
public class ContactDeletionInteraction {

    private static final String TAG = "ContactDeletionInteraction";

    public static final String EXTRA_KEY_CONTACT_URI = "contactUri";
    public static final String EXTRA_KEY_MESSAGE_ID = "messageId";

    private static final String[] RAW_CONTACTS_PROJECTION = new String[] {
        RawContacts._ID, //0
        RawContacts.ACCOUNT_TYPE, //1
        Contacts._ID, // 2
        Contacts.LOOKUP_KEY, // 3
    };

    private static final int COLUMN_INDEX_RAW_CONTACT_ID = 0;
    private static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
    private static final int COLUMN_INDEX_CONTACT_ID = 2;
    private static final int COLUMN_INDEX_LOOKUP_KEY = 3;

    private final class RawContactLoader extends CursorLoader {
        private final Uri mContactUri;

        private RawContactLoader(Context context, Uri contactUri) {
            super(context, Uri.withAppendedPath(contactUri, Entity.CONTENT_DIRECTORY),
                    RAW_CONTACTS_PROJECTION, null, null, null);
            this.mContactUri = contactUri;
        }

        @Override
        public void deliverResult(Cursor data) {
            if (data == null || data.getCount() == 0) {
                Log.e(TAG, "No such contact: " + mContactUri);
                return;
            }

            showConfirmationDialog(data);
        }
    }

    private final class ConfirmationDialogClickListener implements DialogInterface.OnClickListener {
        private final Uri mContactUri;

        public ConfirmationDialogClickListener(Uri contactUri) {
            this.mContactUri = contactUri;
        }

        public void onClick(DialogInterface dialog, int which) {
            doDeleteContact(mContactUri);
        }
    }

    private Context mContext;
    private CursorLoader mLoader;

    public void attachToActivity(Activity activity) {
        setContext(activity);
    }

    /* Visible for testing */
    void setContext(Context context) {
        mContext = context;
    }

    public void deleteContact(Uri contactUri) {
        if (mLoader != null) {
            mLoader.destroy();
        }
        mLoader = new RawContactLoader(mContext, contactUri);
        startLoading(mLoader);
    }

    protected void showConfirmationDialog(Cursor cursor) {
        long contactId = 0;
        String lookupKey = null;

        // This cursor may contain duplicate raw contacts, so we need to de-dupe them first
        HashSet<Long>  readOnlyRawContacts = Sets.newHashSet();
        HashSet<Long>  writableRawContacts = Sets.newHashSet();

        AccountTypes sources = getSources();
        try {
            while (cursor.moveToNext()) {
                final long rawContactId = cursor.getLong(COLUMN_INDEX_RAW_CONTACT_ID);
                final String accountType = cursor.getString(COLUMN_INDEX_ACCOUNT_TYPE);
                contactId = cursor.getLong(COLUMN_INDEX_CONTACT_ID);
                lookupKey = cursor.getString(COLUMN_INDEX_LOOKUP_KEY);
                BaseAccountType contactsSource = sources.getInflatedSource(accountType,
                        BaseAccountType.LEVEL_SUMMARY);
                boolean readonly = contactsSource != null && contactsSource.readOnly;
                if (readonly) {
                    readOnlyRawContacts.add(rawContactId);
                } else {
                    writableRawContacts.add(rawContactId);
                }
            }
        } finally {
            cursor.close();
        }

        int messageId;
        int readOnlyCount = readOnlyRawContacts.size();
        int writableCount = writableRawContacts.size();
        if (readOnlyCount > 0 && writableCount > 0) {
            messageId = R.string.readOnlyContactDeleteConfirmation;
        } else if (readOnlyCount > 0 && writableCount == 0) {
            messageId = R.string.readOnlyContactWarning;
        } else if (readOnlyCount == 0 && writableCount > 1) {
            messageId = R.string.multipleContactDeleteConfirmation;
        } else {
            messageId = R.string.deleteConfirmation;
        }

        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_KEY_CONTACT_URI, Contacts.getLookupUri(contactId, lookupKey));
        bundle.putInt(EXTRA_KEY_MESSAGE_ID, messageId);

        showDialog(bundle);
    }

    /**
     * Creates a delete confirmation dialog and returns it.  Returns null if the
     * id is not for a deletion confirmation.
     */
    public Dialog onCreateDialog(int id, Bundle bundle) {
        if (id != R.id.dialog_delete_contact_confirmation) {
            return null;
        }

        return new AlertDialog.Builder(mContext)
                .setTitle(R.string.deleteConfirmation_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.deleteConfirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    public boolean onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        if (id != R.id.dialog_delete_contact_confirmation) {
            return false;
        }

        Uri contactUri = bundle.getParcelable(EXTRA_KEY_CONTACT_URI);
        int messageId = bundle.getInt(EXTRA_KEY_MESSAGE_ID, R.string.deleteConfirmation);

        ((AlertDialog)dialog).setMessage(mContext.getText(messageId));
        ((AlertDialog)dialog).setButton(DialogInterface.BUTTON_POSITIVE,
                mContext.getText(android.R.string.ok),
                new ConfirmationDialogClickListener(contactUri));

        return true;
    }

    protected void doDeleteContact(Uri contactUri) {
        mContext.getContentResolver().delete(contactUri, null, null);
    }

    /* Visible for testing */
    void startLoading(Loader<Cursor> loader) {
        loader.startLoading();
    }

    /* Visible for testing */
    AccountTypes getSources() {
        return AccountTypes.getInstance(mContext);
    }

    /* Visible for testing */
    void showDialog(Bundle bundle) {
        ((Activity)mContext).showDialog(R.id.dialog_delete_contact_confirmation, bundle);
    }
}