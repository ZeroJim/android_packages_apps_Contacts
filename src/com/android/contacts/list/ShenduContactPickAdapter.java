
package com.android.contacts.list;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.android.contacts.activities.ShenDuContactSelectionActivity;
import com.android.contacts.activities.ShenDuContactSelectionActivity.Option;
import com.android.contacts.activities.ShenDuContactSelectionActivity.OptionChangedListener;
import com.android.contacts.group.GroupEditorFragment.Member;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class ShenduContactPickAdapter extends ContactEntryListAdapter implements
        OptionChangedListener {

    protected static class ContactsQuery {
        public static final String[] PROJECTION_CONTACTS = new String[] {
                Contacts._ID, // 0
                Contacts.DISPLAY_NAME_PRIMARY, // 1
                Contacts.CONTACT_PRESENCE, // 2
                Contacts.CONTACT_STATUS, // 3
                Contacts.PHOTO_ID, // 4
                Contacts.PHOTO_THUMBNAIL_URI, // 5
                Contacts.LOOKUP_KEY, // 6
                Contacts.IS_USER_PROFILE, // 7
        };
        public static final int CONTACT_ID = 0;
        public static final int CONTACT_DISPLAY_NAME = 1;
        public static final int CONTACT_PRESENCE_STATUS = 2;
        public static final int CONTACT_CONTACT_STATUS = 3;
        public static final int CONTACT_PHOTO_ID = 4;
        public static final int CONTACT_PHOTO_URI = 5;
        public static final int CONTACT_LOOKUP_KEY = 6;
        public static final int CONTACT_IS_USER_PROFILE = 7;
    }

    private String mExcludedContactIds;
    private final CharSequence mUnknownNameText;
    private ConcurrentHashMap<Long, MemberWithoutRawContactId> mSelecteds = new ConcurrentHashMap<Long, MemberWithoutRawContactId>();

    public ShenduContactPickAdapter(Context context) {
        super(context);
        mUnknownNameText = context.getText(android.R.string.unknownName);
        try {
            ShenDuContactSelectionActivity activity = (ShenDuContactSelectionActivity) context;
            activity.setOptionChangedListener(this);
        } catch (ClassCastException e) {
            log("ClassCastException !!!!");
            return ;
        }
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        // ShenduSelectionContactListItemView item =
        // (ShenduSelectionContactListItemView)itemView;
        ContactListItemView view = (ContactListItemView) itemView;
        // ContactListItemView view = item.getContactListItemView();
        bindSectionHeaderAndDivider(view, position, cursor);
        bindName(view, cursor);
        bindPhoto(view, cursor);
        /*------------------*/
        bindCheckBox(view, cursor, position);
    }

    private void bindCheckBox(ContactListItemView view, Cursor cursor, final int position) {
        // log(">>bindCheckBox  position = "+position+"<<");
        final long contactId = cursor.getLong(ContactsQuery.CONTACT_ID);
        final String displayName = cursor.getString(ContactsQuery.CONTACT_DISPLAY_NAME);
        // log("   displayName => "+displayName);
        final String lookupKey = cursor.getString(ContactsQuery.CONTACT_LOOKUP_KEY);
        final String photoUri = cursor.getString(ContactsQuery.CONTACT_PHOTO_URI);
        CheckBox box = view.getCheckBox();
        box.setChecked(mSelecteds.containsKey(contactId) ? true : false);
        box.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    CompoundButton checkBox = (CompoundButton) v;
                    boolean isChecked = checkBox.isChecked();
                    if (isChecked) {
                        if (!mSelecteds.containsKey(contactId)) {
                            MemberWithoutRawContactId member = new MemberWithoutRawContactId(
                                    contactId, lookupKey, displayName, photoUri);
                            mSelecteds.put(contactId, member);
                        }
                    } else {
                        if (mSelecteds.containsKey(contactId)) {
                            mSelecteds.remove(contactId);
                        }
                    }
                    onUpdateCountView(Option.Normal);
                } catch (ClassCastException e) {
                    log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!CheckBox ClassCastException!");
                    return;
                }

            }
        });
    }
    /**
     * Select All Mode
     * @author Wang
     * @date 2012-9-10
     */
    private void onSelectAll(){
        Cursor cursor = getCursor(0);
        if(cursor == null) return;
        cursor.moveToPosition(-1);
        while(cursor.moveToNext()){
            final long contactId = cursor.getLong(ContactsQuery.CONTACT_ID);
            final String displayName = cursor.getString(ContactsQuery.CONTACT_DISPLAY_NAME);
            // log("   displayName => "+displayName);
            final String lookupKey = cursor.getString(ContactsQuery.CONTACT_LOOKUP_KEY);
            final String photoUri = cursor.getString(ContactsQuery.CONTACT_PHOTO_URI);
            MemberWithoutRawContactId member = new MemberWithoutRawContactId(
                    contactId, lookupKey, displayName, photoUri);
            mSelecteds.put(contactId, member);
        }
        notifyDataSetChanged();
        /*Wang:should not change state because onItemClick method has changed state in activity*/
        onUpdateCountView(null);

    }
    
    /**
     * Deselect All Mode
     * @author Wang
     * @date 2012-9-10
     */
    private void onDeselectAll(){
        mSelecteds.clear();
        notifyDataSetChanged();
        onUpdateCountView(null);
    }
    
    /**
     * Update CountView in Selection Activity.
     * @author Wang
     * @param state The Option state to set up. If null will not change option.
     * @date 2012-9-10
     */
    private void onUpdateCountView(Option state){
        try {
            ShenDuContactSelectionActivity activity = (ShenDuContactSelectionActivity) mContext ;
            activity.updateSelectedCountInSpinner(mSelecteds.size());
            if(state != null)activity.changeOptionState(Option.Normal);
        } catch (ClassCastException e) {
            return;
        }
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        return view;
    }

    private void bindName(ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, ContactsQuery.CONTACT_DISPLAY_NAME,
                getContactNameDisplayOrder());
    }

    private void bindPhoto(ContactListItemView view, Cursor cursor) {
        long photoId = 0;
        if (!cursor.isNull(ContactsQuery.CONTACT_PHOTO_ID)) {
            photoId = cursor.getLong(ContactsQuery.CONTACT_PHOTO_ID);
        }
        /*Wang: 2012-11-15*/
        getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId,false, view.getNameTextView().getText().toString(), -1);
    }

    private void bindSectionHeaderAndDivider(ContactListItemView view, int position, Cursor cursor) {
        if (isSectionHeaderDisplayEnabled()) {
            Placement placement = getItemPlacementInSection(position);
            view.setSectionHeader(placement.firstInSection ? placement.sectionHeader : null);
            view.setDividerVisible(true);
        } else {
            view.setSectionHeader(null);
            view.setDividerVisible(true);
            view.setCountView(null);
        }
    }

    /**
     * Set the ContactId  those are in group.
     * @author Wang
     * @date 2012-9-4
     */
    public void setExcludedContactId(long[] ids) {
        if (ids == null)
            return;
        int end = ids.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            sb.append(ids[i]);
            if (i != end - 1) {
                sb.append(",");
            }
        }
        log(" =======IDs String =>" + sb.toString());
        setExcludedContactId(sb.toString());
    }

    /**
     * Set the ContactId  those are in group.
     * @author Wang
     * @date 2012-9-4
     */
    public void setExcludedContactId(String ids) {
        if (ids == null || TextUtils.isEmpty(ids) || ids.equals(mExcludedContactIds)) {
            return;
        }
        mExcludedContactIds = ids;
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        configureUri(loader, directoryId);
        loader.setProjection(ContactsQuery.PROJECTION_CONTACTS);
        configureSelection(loader);
        loader.setSortOrder(Contacts.SORT_KEY_PRIMARY);
    }

    /**
     * @author Wang
     * @date 2012-9-4
     */
    private void configureSelection(CursorLoader loader) {
        log(">>configureSelection<<");
        if (TextUtils.isEmpty(mExcludedContactIds)) {
            return;
        }
        String selection = Contacts._ID + " not in(" + mExcludedContactIds + ")";
        log(" configureSelection =>" + selection);
        loader.setSelection(selection);
    }

    /**
     * @author Wang
     * @date 2012-9-4
     */
    private void configureUri(CursorLoader loader, long directoryId) {
        Uri uri = Contacts.CONTENT_URI;
        uri = Contacts.CONTENT_URI.buildUpon().appendQueryParameter(
                ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                .build();
        if (directoryId == Directory.DEFAULT && isSectionHeaderDisplayEnabled()) {
            uri = buildSectionIndexerUri(uri);
        }
        loader.setUri(uri);
    }

    protected static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(ContactsQuery.CONTACT_DISPLAY_NAME);
    }
    
    public Collection<MemberWithoutRawContactId> getNewMembers(){
        return mSelecteds.values();
    }

    public static class MemberWithoutRawContactId  implements Parcelable{
        private static final MemberWithoutRawContactId[] EMPTY_ARRAY = new MemberWithoutRawContactId[0];
        
        private final long mContactId;
        private final String mLookupKey;
        private final String mDisplayName;
        private final String mPhotoUri;

        public MemberWithoutRawContactId(long contactId, String lookupKey, String displayName,
                String photoUri) {
            this.mContactId = contactId;
            this.mLookupKey = lookupKey;
            this.mDisplayName = displayName;
            this.mPhotoUri = photoUri;
        }

        public long getContactId() {
            return mContactId;
        }

        public String getLookupKey() {
            return mLookupKey;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public String getPhotoUri() {
            return mPhotoUri;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mContactId);
            dest.writeString(mLookupKey);
            dest.writeString(mDisplayName);
            dest.writeString(mPhotoUri);
        }

        private MemberWithoutRawContactId(Parcel in) {
            mContactId = in.readLong();
            mLookupKey = in.readString();
            mDisplayName = in.readString();
            mPhotoUri =in.readString();
        }
        public static final Parcelable.Creator<MemberWithoutRawContactId> CREATOR = new Creator() {
            public MemberWithoutRawContactId createFromParcel(Parcel source) {
                return new MemberWithoutRawContactId(source);
            }
            public MemberWithoutRawContactId[] newArray(int size) {
                return new MemberWithoutRawContactId[size];
            }  
         
        };  
    }

    @Override
    public void onOptionChanged(Option op) {
        switch (op) {
            case Normal:
                onDeselectAll();
                break;

            case SelectAll:
                onSelectAll();
                break;
        }
    }

    private static final boolean debug = false;

    private static void log(String msg) {
        msg = "ShenduPickAdapter  --> " + msg;
        if (debug)
            Log.i("shenduGroup", msg);
    }

}
