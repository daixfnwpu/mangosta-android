package inaka.com.mangosta.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;

import org.jivesoftware.smack.packet.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;
import inaka.com.mangosta.R;
import inaka.com.mangosta.activities.ChatActivity;
import inaka.com.mangosta.adapters.ChatListAdapter;
import inaka.com.mangosta.chat.RoomManager;
import inaka.com.mangosta.chat.RoomManagerListener;
import inaka.com.mangosta.models.Chat;
import inaka.com.mangosta.models.ChatMessage;
import inaka.com.mangosta.models.Event;
import inaka.com.mangosta.realm.RealmManager;
import inaka.com.mangosta.xmpp.XMPPSession;
import inaka.com.mangosta.xmpp.XMPPUtils;
import rx.Subscription;
import rx.functions.Action1;


public class ChatsListFragment extends BaseFragment {

    @Bind(R.id.chatListRecyclerView)
    RecyclerView chatListRecyclerView;

    @Bind(R.id.swipeRefreshLayout)
    public SwipeRefreshLayout swipeRefreshLayout;

    private RoomManager mRoomManager;
    private List<Chat> mChats;
    private ChatListAdapter mChatListAdapter;

    Subscription mMessageSubscription;
    Subscription mMessageSentAlertSubscription;

    public final static int ONE_TO_ONE_CHATS_POSITION = 0;
    public final static int MUC_LIGHT_CHATS_POSITION = 1;
    public final static int MUC_CHATS_POSITION = 2;

    public int mPosition = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);
        ButterKnife.bind(this, view);

        Bundle bundle = getArguments();
        mPosition = bundle.getInt("position");

        mChats = new ArrayList<>();

        mRoomManager = RoomManager.getInstance(new RoomManagerChatListListener(getActivity()));

        mChatListAdapter = getChatListAdapter();

        chatListRecyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        chatListRecyclerView.setLayoutManager(layoutManager);
        chatListRecyclerView.setAdapter(mChatListAdapter);

        mMessageSubscription = XMPPSession.getInstance().subscribeToMessages(new Action1<Message>() {
            @Override
            public void call(Message message) {
                loadChats();
            }
        });

        mMessageSentAlertSubscription = XMPPSession.getInstance().subscribeToMessageSent(new Action1<Message>() {
            @Override
            public void call(Message message) {
                loadChats();
            }
        });

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Refresh items
                loadChatsBackgroundTask();
            }
        });

        swipeRefreshLayout.setColorSchemeResources(
                R.color.blue_light_background,
                R.color.colorPrimaryLight,
                R.color.colorPrimary);

        loadChatsBackgroundTask();

        return view;
    }

    public ChatListAdapter getChatListAdapter() {
        return new ChatListAdapter(mChats, getActivity(),
                new ChatListAdapter.ChatClickListener() {
                    @Override
                    public void onChatClicked(Chat chat) {
                        Intent intent = new Intent(getActivity(), ChatActivity.class);
                        intent.putExtra(ChatActivity.CHAT_JID_PARAMETER, chat.getJid());
                        intent.putExtra(ChatActivity.CHAT_NAME_PARAMETER, XMPPUtils.getChatName(chat));
                        intent.putExtra(ChatActivity.IS_NEW_CHAT_PARAMETER, false);
                        getActivity().startActivity(intent);
                    }
                });
    }

    public void loadChats() {
        if (getActivity() == null) {
            changeChatsList();
        } else {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    changeChatsList();
                }
            });
        }
    }

    private void changeChatsList() {
        mChats.clear();

        switch (mPosition) {
            case ONE_TO_ONE_CHATS_POSITION: // load 1 to 1 chats
                mChats.addAll(RealmManager.get1to1Chats(getRealm()));
                break;

            case MUC_LIGHT_CHATS_POSITION: // load muc chats
                mChats.addAll(RealmManager.getMUCLights(getRealm()));
                break;

            case MUC_CHATS_POSITION: // load muc light chats
                mChats.addAll(RealmManager.getMUCs(getRealm()));
                break;
        }

        Collections.sort(mChats, new ChatOrderComparator());

        if (mChatListAdapter == null) {
            mChatListAdapter = getChatListAdapter();
        }

        mChatListAdapter.notifyDataSetChanged();
        swipeRefreshLayout.setRefreshing(false);
    }

    private void loadChatsAfterRoomLeft() {
        loadChatsBackgroundTask();
    }

    public void loadChatsBackgroundTask() {
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    swipeRefreshLayout.setRefreshing(true);
                }
            });
        }

        if (mRoomManager == null) {
            return;
        }

        Tasks.executeInBackground(getActivity(), new BackgroundWork<Object>() {
            @Override
            public Object doInBackground() throws Exception {
                switch (mPosition) {
                    case MUC_LIGHT_CHATS_POSITION: // load muc chats
                        mRoomManager.loadMUCLightRooms();
                        break;

                    case MUC_CHATS_POSITION: // load muc light chats
                        mRoomManager.loadMUCRooms();
                        break;

                    case ONE_TO_ONE_CHATS_POSITION: // load 1 to 1 chats from friends
                        mRoomManager.loadRosterFriendsChats();
                        break;
                }
                return null;
            }
        }, new Completion<Object>() {
            @Override
            public void onSuccess(Context context, Object object) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadChats();
                    }
                });
            }

            @Override
            public void onError(Context context, Exception e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadChats();
                    }
                });
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    // receives events from EventBus
    public void onEvent(Event event) {
        switch (event.getType()) {
            case GO_BACK_FROM_CHAT:
                loadChats();
                break;
        }
    }

    private class RoomManagerChatListListener extends RoomManagerListener {

        public RoomManagerChatListListener(Context context) {
            super(context);
        }

        @Override
        public void onRoomLeft() {
            loadChatsAfterRoomLeft();
        }

        @Override
        public void onRoomsLoaded() {
            loadChats();
        }
    }

    static class ChatOrderComparator implements Comparator<Chat> {
        @Override
        public int compare(Chat chat1, Chat chat2) {
            ChatMessage chatMessage1 = RealmManager.getLastMessageForChat(chat1.getJid());
            ChatMessage chatMessage2 = RealmManager.getLastMessageForChat(chat2.getJid());

            Date date1 = null;
            Date date2 = null;

            if (chatMessage1 != null) {
                date1 = chatMessage1.getDate();
            }

            if (chatMessage2 != null) {
                date2 = chatMessage2.getDate();
            }

            if (date1 == null) {
                return 1;
            } else if (date2 == null) {
                return 0;
            } else {
                return date2.compareTo(date1);
            }
        }
    }

}
