/*
 * Created by Itzik Braun on 12/3/2015.
 * Copyright (c) 2015 deluge. All rights reserved.
 *
 * Last Modification at: 3/12/15 4:27 PM
 */

package co.chatsdk.ui.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;

import com.astuetz.PagerSlidingTabStrip;

import org.apache.commons.lang3.StringUtils;

import co.chatsdk.core.Tab;
import co.chatsdk.core.events.EventType;
import co.chatsdk.core.events.NetworkEvent;
import co.chatsdk.core.interfaces.ThreadType;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.session.NM;
import co.chatsdk.core.utils.DisposableList;
import co.chatsdk.core.utils.PermissionRequestHandler;
import co.chatsdk.ui.manager.BaseInterfaceAdapter;
import co.chatsdk.ui.manager.InterfaceManager;
import co.chatsdk.ui.R;
import co.chatsdk.ui.helpers.ExitHelper;
import co.chatsdk.ui.helpers.NotificationUtils;
import co.chatsdk.ui.helpers.OpenFromPushChecker;
import co.chatsdk.ui.threads.PrivateThreadsFragment;
import io.reactivex.Completable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;


public class MainActivity extends BaseActivity {

    private ExitHelper exitHelper;
    private PagerSlidingTabStrip tabs;
    private ViewPager pager;
    protected PagerAdapterTabs adapter;
    private OpenFromPushChecker mOpenFromPushChecker;

    DisposableList disposables = new DisposableList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        exitHelper = new ExitHelper(this);

        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            // Activity was brought to front and not created,
            // Thus finishing this will get us to the last viewed context
            finish();
            return;
        }

        setContentView(R.layout.chat_sdk_activity_view_pager);

        initViews();

        mOpenFromPushChecker = new OpenFromPushChecker();
        if(mOpenFromPushChecker.checkOnCreate(getIntent(), savedInstanceState)) {
            String threadEntityID = getIntent().getExtras().getString(BaseInterfaceAdapter.THREAD_ENTITY_ID);
            InterfaceManager.shared().a.startChatActivityForID(this, threadEntityID);
        }

        requestPermissionSafely(requestExternalStorage().doFinally(new Action() {
            @Override
            public void run() throws Exception {
                requestPermissionSafely(requestMicrophoneAccess().doFinally(new Action() {
                    @Override
                    public void run() throws Exception {
                        requestPermissionSafely(requestReadContacts().doFinally(new Action() {
                            @Override
                            public void run() throws Exception {
                                //requestVideoAccess().subscribe();
                            }
                        }));
                    }
                }));
            }
        }));

    }

    public void requestPermissionSafely (Completable c) {
        c.subscribe(new Action() {
            @Override
            public void run() throws Exception {

            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                throwable.printStackTrace();
            }
        });
    }

    public Completable requestMicrophoneAccess () {
        if(NM.audioMessage() != null) {
            return PermissionRequestHandler.shared().requestRecordAudio(this);
        }
        return Completable.complete();
    }

    public Completable requestExternalStorage () {
//        if(NM.audioMessage() != null) {
            return PermissionRequestHandler.shared().requestReadExternalStorage(this);
//        }
//        return Completable.complete();
    }

    public Completable requestVideoAccess () {
        if(NM.videoMessage() != null) {
            return PermissionRequestHandler.shared().requestVideoAccess(this);
        }
        return Completable.complete();
    }

    public Completable requestReadContacts () {
        if(NM.contact() != null) {
            return PermissionRequestHandler.shared().requestReadContact(this);
        }
        return Completable.complete();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        PermissionRequestHandler.shared().onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();

        disposables.dispose();

        // TODO: Check this
        disposables.add(NM.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.MessageAdded))
                .filter(NetworkEvent.filterThreadType(ThreadType.Private))
                .subscribe(new Consumer<NetworkEvent>() {
                    @Override
                    public void accept(NetworkEvent networkEvent) throws Exception {
                        if(networkEvent.message != null) {
                            if(!networkEvent.message.getSender().isMe()) {
                                // Only show the alert if we'recyclerView not on the private threads tab
                                if(!(adapter.getTabs().get(pager.getCurrentItem()).fragment instanceof PrivateThreadsFragment)) {
                                    NotificationUtils.createMessageNotification(MainActivity.this, networkEvent.message);
                                }
                            }
                        }
                    }
                }));

        disposables.add(NM.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.Logout))
                .subscribe(new Consumer<NetworkEvent>() {
                    @Override
                    public void accept(NetworkEvent networkEvent) throws Exception {
                        clearData();
                    }
                }));


        reloadData();

    }

    @Override
    protected void onPause () {
        super.onPause();
        disposables.dispose();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        if (mOpenFromPushChecker == null) {
            mOpenFromPushChecker = new OpenFromPushChecker();
        }

        if (mOpenFromPushChecker.checkOnNewIntent(intent)) {
            String threadEntityID = intent.getExtras().getString(BaseInterfaceAdapter.THREAD_ENTITY_ID);
            InterfaceManager.shared().a.startChatActivityForID(this, threadEntityID);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //outState.putInt(PAGE_ADAPTER_POS, pageAdapterPos);
        mOpenFromPushChecker.onSaveInstanceState(outState);
    }

    private void initViews() {

        pager = (ViewPager) findViewById(R.id.pager);

        tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);

        // Only creates the adapter if it wasn't initiated already
        if (adapter == null) {
            adapter = new PagerAdapterTabs(getSupportFragmentManager());
        }

        pager.setAdapter(adapter);

        tabs.setViewPager(pager);

        pager.setOffscreenPageLimit(3);


    }

    public void clearData () {
        for(Tab t : adapter.getTabs()) {
            if(t.fragment instanceof BaseFragment) {
                ((BaseFragment) t.fragment).clearData();
            }
        }
    }

    public void reloadData () {
        for(Tab t : adapter.getTabs()) {
            if(t.fragment instanceof BaseFragment) {
                ((BaseFragment) t.fragment).safeReloadData();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds threads to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_chat_sdk, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO: update this
        if (item.getItemId() == R.id.android_settings) {

            // FIXME Clearing the cache, Just for debug.
            /*final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

            VolleyUtils.getBitmapCache().resize(1);
            VolleyUtils.getBitmapCache().resize(maxMemory / 8);*/
            return true;
        }
        else if (item.getItemId() == R.id.contact_developer) {

            String emailAddress = ChatSDK.config().contactDeveloperEmailAddress;
            String subject = ChatSDK.config().contactDeveloperEmailSubject;
            String dialogTitle = ChatSDK.config().contactDeveloperDialogTitle;

            if(StringUtils.isNotEmpty(emailAddress))
            {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                        "mailto", emailAddress, null));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                startActivity(Intent.createChooser(emailIntent, dialogTitle));
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // If the signUp did not had a chance to signUp due to orientation change.
        try{
            unregisterReceiver(mainReceiver);
        }catch (IllegalArgumentException e){
            // No need to handle the exception.
        }
    }

    /** Refresh the contacts fragment when a contact added action is received.
     *  Clear Fragments bundle when logged out.
     *  Refresh Fragment when wanted.*/
    private BroadcastReceiver mainReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
//            if (intent.getAction().equals(Action_Contacts_Added))
//            {
//                BaseFragment contacts = getFragment(FragmentIDs.Contacts);
//
//                if (contacts != null)
//                    contacts.reloadData();
//
//                // TODO: This should be handled by the list already no?
////                if (intent.getExtras().containsKey(SearchActivity.USER_IDS_LIST))
////                {
////                    String[] ids = intent.getStringArrayExtra(SearchActivity.USER_IDS_LIST);
////                    for (String id : ids) {
////
////                        new UserWrapper(id);
////                    }
////
////                        NetworkManager.getCoreInterface().getEventManager().userMetaOn(id, null);
////                }
//            }
//            else if (intent.getAction().equals(Action_clear_data))
//            {
//                clearData();
//            }
//            else if (intent.getAction().equals(Action_Refresh_Fragment))
//            {
//                if (intent.getExtras() == null)
//                    return;
//
//                if (!intent.getExtras().containsKey(PAGE_ADAPTER_POS))
//                    return;
//
//                int fragment = intent.getExtras().getInt(PAGE_ADAPTER_POS);
//
//                BaseFragment frag = getFragment(fragment);
//
//                if (frag!= null)
//                    frag.refresh();
//
//            }
//            else if (intent.getAction().equals(ChatActivity.ACTION_CHAT_CLOSED))
//            {
//                getFragment(FragmentIDs.Conversations).reloadData();
//            }
        }
    };

//    private void clearData () {
//        BaseFragment contacts = getFragment(FragmentIDs.Contacts);
//
//        if (contacts != null)
//            contacts.clearData();
//
//        BaseFragment conv = getFragment(FragmentIDs.Conversations);
//
//        if (conv != null)
//            conv.clearData();
//
//        BaseFragment pro = getFragment(FragmentIDs.Profile);
//
//        if (pro != null)
//            pro.clearData();
//    }

    /* Exit Stuff*/
    @Override
    public void onBackPressed() {
        exitHelper.triggerExit();
    }

    /** After screen orientation chage the getItem from the fragment page adapter is no null but it is not visible to the user
     *  so we have to use this workaround so when we call any method on the wanted fragment the fragment will respond.
     *  http://stackoverflow.com/a/7393477/2568492*/
    private BaseFragment getFragment(int index){
        return ((BaseFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + pager + ":" + index));
    }
}
