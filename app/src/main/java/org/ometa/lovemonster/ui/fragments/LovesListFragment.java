package org.ometa.lovemonster.ui.fragments;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import org.ometa.lovemonster.Logger;
import org.ometa.lovemonster.R;
import org.ometa.lovemonster.models.Love;
import org.ometa.lovemonster.models.User;
import org.ometa.lovemonster.service.LoveMonsterClient;
import org.ometa.lovemonster.service.NetworkHelper;
import org.ometa.lovemonster.ui.adapters.LoveArrayAdapter;
import org.ometa.lovemonster.ui.listeners.EndlessScrollListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by bschmeckpeper on 11/18/15.
 */
public abstract class LovesListFragment extends Fragment {

    // these methods must be implemented from children classes
    protected abstract void onSwipeUp(int page);
    protected abstract void onSwipeDown();
    protected abstract void initialLoadNoInternet(LoveArrayAdapter lovesArrayAdapter);
    protected abstract void initialLoadWithInternet();

    private Logger logger;

    ArrayList<Love> lovesList;
    LoveArrayAdapter lovesArrayAdapter;

    public class ViewHolder {
        public ListView lvLoves;
        public SwipeRefreshLayout swipeContainer;
        public View noLovesMessage;
    }

    public ViewHolder viewHolder;
    protected User currentUser;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logger = new Logger(LovesListFragment.class);
        super.onCreate(savedInstanceState);
        lovesList = new ArrayList<>();
        lovesArrayAdapter = new LoveArrayAdapter(getContext(), lovesList, currentUser);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.loves_list_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        viewHolder = new ViewHolder();
        viewHolder.lvLoves = (ListView) view.findViewById(R.id.lvLoves);
        viewHolder.lvLoves.setAdapter(lovesArrayAdapter);

        viewHolder.noLovesMessage = view.findViewById(R.id.lvLovesNoLoves);
        toggleNoLoveMessage();

        // Swipe Up (load older loves)
        viewHolder.lvLoves.setOnScrollListener(new EndlessScrollListener() {
            @Override
            public boolean onLoadMore(int page, int totalItemsCount) {
                onSwipeUp(page);
                return true;
            }
        });

        // Swipe Down (load new loves)
        viewHolder.swipeContainer = (SwipeRefreshLayout) view.findViewById(R.id.swipeContainer);
        viewHolder.swipeContainer.setColorSchemeResources(
                android.R.color.holo_blue_bright, android.R.color.holo_green_light,
                android.R.color.holo_orange_light, android.R.color.holo_red_light
        );
        viewHolder.swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (NetworkHelper.isUp(getActivity())) {
                    onSwipeDown();
                } else {
                    viewHolder.swipeContainer.setRefreshing(false);
                    Toast.makeText(getActivity(), R.string.check_internet, Toast.LENGTH_SHORT).show();
                }
            }
        });


        // Load initial Data (has to happen after we instantiate the client in the child onCreate()
        if (NetworkHelper.isUp(getActivity())) {
            initialLoadWithInternet();
        } else {
            Toast.makeText(getActivity(), R.string.check_internet, Toast.LENGTH_SHORT).show();
            initialLoadNoInternet(lovesArrayAdapter);
        }
    }


    /**
     *  Toggles whether the no love message is displayed or not.
     */
    private void toggleNoLoveMessage() {
        if (lovesArrayAdapter.isEmpty()) {
            viewHolder.noLovesMessage.setVisibility(View.VISIBLE);
        } else {
            viewHolder.noLovesMessage.setVisibility(View.GONE);
        }
    }

    public void setCurrentUser(User user) {
        // We sometimes get called before the array adapter has been created.  In those cases,
        // cache the currentUser in an instance var and set it on the adapter when we finally
        // get around to creating the adapter.
        if (lovesArrayAdapter == null) {
            currentUser = user;
        } else {
            lovesArrayAdapter.currentUser = user;
        }
    }


    public void addLove(Love love) {
        lovesArrayAdapter.add(love);
        toggleNoLoveMessage();
    }


    // Generic swipe up handler that adds loves to the end of the arraylist.
    // Override this method in child classes if you need more control, validation, etc.
    protected LoveMonsterClient.LoveListResponseHandler getSwipeUpHandler() {
        return new LoveMonsterClient.LoveListResponseHandler() {
            @Override
            public void onSuccess(@NonNull List<Love> loves, int totalPages) {
                lovesArrayAdapter.addAllToEnd(loves);
                toggleNoLoveMessage();
            }

            @Override
            public void onFail() {
                Toast.makeText(getContext(), "Unable to retrieve loves", Toast.LENGTH_SHORT).show();
            }
        };
    }

    // Generic swipe down handler that adds loves to the beginning of the arraylist.
    // Override this method in child classes if you need more control, validation, etc.
    protected LoveMonsterClient.LoveListResponseHandler getSwipeDownHandler() {
        return new LoveMonsterClient.LoveListResponseHandler() {
            @Override
            public void onSuccess(@NonNull List<Love> loves, int totalPages) {

                // todo: implement loading just the new loves and add them to the beginning
                // instead of resetting the arraylist
                // lovesArrayAdapter.addAllToBeginning(loves);
                lovesArrayAdapter.addAllToEnd(loves);
                toggleNoLoveMessage();
                viewHolder.swipeContainer.setRefreshing(false);
            }

            @Override
            public void onFail() {
                Toast.makeText(getContext(), "Unable to retrieve loves", Toast.LENGTH_SHORT).show();
            }
        };
    }
}