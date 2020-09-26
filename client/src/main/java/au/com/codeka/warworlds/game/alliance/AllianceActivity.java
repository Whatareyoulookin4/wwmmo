package au.com.codeka.warworlds.game.alliance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import au.com.codeka.common.Log;
import au.com.codeka.common.TimeFormatter;
import au.com.codeka.warworlds.ImageHelper;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.ServerGreeter;
import au.com.codeka.warworlds.TabFragmentActivity;
import au.com.codeka.warworlds.TabManager;
import au.com.codeka.warworlds.WarWorldsActivity;
import au.com.codeka.warworlds.eventbus.EventHandler;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.model.Alliance;
import au.com.codeka.warworlds.model.AllianceManager;
import au.com.codeka.warworlds.model.AllianceRequest;
import au.com.codeka.warworlds.model.AllianceShieldManager;
import au.com.codeka.warworlds.model.Empire;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.EmpireShieldManager;
import au.com.codeka.warworlds.model.MyEmpire;
import au.com.codeka.warworlds.model.ShieldManager;

public class AllianceActivity extends TabFragmentActivity {
  private final static Log log = new Log("AllianceActivity");
  private Context context = this;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ServerGreeter.waitForHello(this, (success, greeting) -> {
      if (!success) {
        startActivity(new Intent(AllianceActivity.this, WarWorldsActivity.class));
      } else {
        MyEmpire myEmpire = EmpireManager.i.getEmpire();
        if (myEmpire.getAlliance() != null) {
          getTabManager().addTab(context, new TabInfo(AllianceActivity.this, "Overview",
              AllianceDetailsFragment.class, null));
        }

        getTabManager().addTab(context, new TabInfo(AllianceActivity.this, "Alliances",
            AllianceListFragment.class, null));

        if (myEmpire.getAlliance() != null) {
          Integer numPendingRequests = myEmpire.getAlliance().getNumPendingRequests();
          String pending = "";
          if (numPendingRequests != null && numPendingRequests > 0) {
            pending = " (<font color=\"red\">" + numPendingRequests + "</font>)";
          }
          getTabManager().addTab(context, new TabInfo(AllianceActivity.this,
              "Requests" + pending,
              RequestsFragment.class, null));
        }
      }
    });
  }

  @Override
  public void onStart() {
    super.onStart();
    EmpireManager.eventBus.register(eventHandler);
  }

  @Override
  public void onStop() {
    super.onStop();
    EmpireManager.eventBus.unregister(eventHandler);
  }

  private Object eventHandler = new Object() {
    @EventHandler
    public void onEmpireUpdated(Empire empire) {
      MyEmpire myEmpire = EmpireManager.i.getEmpire();
      if (myEmpire.getID() == empire.getID()) {
        getTabManager().reloadTab();
      }
    }
  };

  public static class BaseFragment extends Fragment {
  }

  public static class AllianceListFragment extends BaseFragment {
    private View view;
    private RankListAdapter rankListAdapter;

    @Override
    public View onCreateView(
        @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      view = inflater.inflate(R.layout.alliance_overview_tab, container, false);
      rankListAdapter = new RankListAdapter();

      final Button createBtn = view.findViewById(R.id.create_alliance_btn);
      createBtn.setOnClickListener(v -> onAllianceCreate());

      final CheckBox showInactiveChk = view.findViewById(R.id.show_inactive);
      showInactiveChk.setOnCheckedChangeListener((compoundButton, b) -> refresh());

      ListView alliancesList = view.findViewById(R.id.alliances);
      alliancesList.setAdapter(rankListAdapter);
      alliancesList.setOnItemClickListener((parent, view, position, id) -> {
        RankListAdapter.ItemEntry item =
            (RankListAdapter.ItemEntry) rankListAdapter.getItem(position);
        if (item.alliance != null) {
          Intent intent = new Intent(getActivity(), AllianceDetailsActivity.class);
          intent.putExtra("au.com.codeka.warworlds.AllianceKey", item.alliance.getKey());

          Messages.Alliance.Builder alliance_pb = Messages.Alliance.newBuilder();
          item.alliance.toProtocolBuffer(alliance_pb);
          intent.putExtra("au.com.codeka.warworlds.Alliance", alliance_pb.build().toByteArray());

          getActivity().startActivity(intent);
        }
      });

      refresh();
      return view;
    }

    @Override
    public void onAttach(Context context) {
      super.onAttach(context);
      AllianceManager.eventBus.register(eventHandler);
      ShieldManager.eventBus.register(eventHandler);
    }

    @Override
    public void onDetach() {
      super.onDetach();
      AllianceManager.eventBus.unregister(eventHandler);
      ShieldManager.eventBus.unregister(eventHandler);
    }

    private Object eventHandler = new Object() {
      @EventHandler
      public void onAllianceUpdated(Alliance alliance) {
        refresh();
      }

      @EventHandler
      public void onShieldUpdated(ShieldManager.ShieldUpdatedEvent event) {
        rankListAdapter.notifyDataSetChanged();
      }
    };

    private void onAllianceCreate() {
      AllianceCreateDialog dialog = new AllianceCreateDialog();
      dialog.show(getActivity().getSupportFragmentManager(), "");
    }

    private void refresh() {
      final ProgressBar progressBar = view.findViewById(R.id.loading);
      final ListView alliancesList = view.findViewById(R.id.alliances);
      alliancesList.setVisibility(View.GONE);
      progressBar.setVisibility(View.VISIBLE);

      boolean hideDead = !((CheckBox) view.findViewById(R.id.show_inactive)).isChecked();
      AllianceManager.i.fetchAlliances(hideDead,
          new AllianceManager.FetchAlliancesCompleteHandler() {
            @Override
            public void onAlliancesFetched(List<Alliance> alliances) {
              rankListAdapter.setAlliances(alliances);

              alliancesList.setVisibility(View.VISIBLE);
              progressBar.setVisibility(View.GONE);
            }
          });
    }

    private class RankListAdapter extends BaseAdapter {
      private ArrayList<ItemEntry> entries;

      public void setAlliances(List<Alliance> alliances) {
        Alliance myAlliance = (Alliance) EmpireManager.i.getEmpire().getAlliance();
        // remove my alliance from the list, it'll always go at the front
        if (myAlliance != null) {
          for (int i = 0; i < alliances.size(); i++) {
            if (alliances.get(i).getKey().equals(myAlliance.getKey())) {
              myAlliance = alliances.get(i); // this'll ensure it's the most recent
              alliances.remove(i);
              break;
            }
          }
        }

        ArrayList<Alliance> sorted = new ArrayList<>(alliances);
        Collections.sort(sorted, new Comparator<Alliance>() {
          @Override
          public int compare(Alliance lhs, Alliance rhs) {
            if (lhs.getNumMembers() == rhs.getNumMembers()) {
              return lhs.getName().compareTo(rhs.getName());
            } else {
              return rhs.getNumMembers() - lhs.getNumMembers();
            }
          }
        });

        entries = new ArrayList<>();
        if (myAlliance != null) {
          entries.add(new ItemEntry(myAlliance));
          entries.add(new ItemEntry(null));
        }
        for (Alliance alliance : sorted) {
          entries.add(new ItemEntry(alliance));
        }

        notifyDataSetChanged();
      }

      @Override
      public int getViewTypeCount() {
        return 2;
      }

      @Override
      public int getItemViewType(int position) {
        ItemEntry entry = entries.get(position);
        return (entry.alliance == null ? 0 : 1);
      }

      @Override
      public int getCount() {
        if (entries == null)
          return 0;
        return entries.size();
      }

      @Override
      public boolean isEnabled(int position) {
        return entries != null && (entries.get(position).alliance != null);
      }

      @Override
      public Object getItem(int position) {
        if (entries == null)
          return null;
        return entries.get(position);
      }

      @Override
      public long getItemId(int position) {
        return position;
      }

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        ItemEntry entry = entries.get(position);
        Activity activity = getActivity();
        View view = convertView;

        if (view == null) {
          LayoutInflater inflater = (LayoutInflater) activity.getSystemService
              (Context.LAYOUT_INFLATER_SERVICE);
          if (entry.alliance == null) {
            view = new View(activity);
            view.setLayoutParams(
                new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 10));
          } else {
            view = inflater.inflate(R.layout.alliance_overview_rank_row, parent, false);
          }
        }

        if (entry.alliance != null) {
          TextView allianceName = view.findViewById(R.id.alliance_name);
          allianceName.setText(entry.alliance.getName());

          TextView allianceMembers = view.findViewById(R.id.alliance_num_members);
          allianceMembers.setText(
              String.format(Locale.US, "Members: %d • Stars: %d",
                  entry.alliance.getNumMembers(), entry.alliance.getTotalStars()));

          ImageView allianceIcon = view.findViewById(R.id.alliance_icon);
          allianceIcon.setImageBitmap(
              AllianceShieldManager.i.getShield(getActivity(), entry.alliance));
        }

        return view;
      }

      public class ItemEntry {
        public Alliance alliance;

        public ItemEntry(Alliance alliance) {
          this.alliance = alliance;
        }
      }
    }

  }

  public static class RequestsFragment extends BaseFragment
      implements TabManager.Reloadable {
    private View view;
    private RequestListAdapter requestListAdapter;
    private Alliance alliance;
    private Handler handler = new Handler();
    private String cursor;
    private boolean fetching;

    @Override
    public void onAttach(Context context) {
      super.onAttach(context);
      AllianceManager.eventBus.register(mEventHandler);
      ShieldManager.eventBus.register(mEventHandler);
    }

    @Override
    public void onDetach() {
      super.onDetach();
      AllianceManager.eventBus.unregister(mEventHandler);
      ShieldManager.eventBus.unregister(mEventHandler);
    }

    @Override
    public View onCreateView(
        LayoutInflater inflator, ViewGroup container, Bundle savedInstanceState) {
      view = inflator.inflate(R.layout.alliance_requests_tab, container, false);
      requestListAdapter = new RequestListAdapter();

      ListView joinRequestsList = view.findViewById(R.id.join_requests);
      joinRequestsList.setAdapter(requestListAdapter);

      joinRequestsList.setOnItemClickListener((parent, view, position, id) -> {
        RequestListAdapter.ItemEntry entry =
            (RequestListAdapter.ItemEntry) requestListAdapter.getItem(position);
        RequestVoteDialog dialog = RequestVoteDialog.newInstance(alliance, entry.request);
        dialog.show(getActivity().getSupportFragmentManager(), "");
      });

      refresh();
      return view;
    }

    private Object mEventHandler = new Object() {
      @EventHandler
      public void onShieldUpdated(ShieldManager.ShieldUpdatedEvent event) {
        requestListAdapter.notifyDataSetChanged();
      }

      @EventHandler
      public void onAllianceUpdated(Alliance alliance) {
        if (RequestsFragment.this.alliance == null || RequestsFragment.this.alliance.getKey().equals(alliance.getKey())) {
          RequestsFragment.this.alliance = alliance;
        }
        refreshRequests();
      }
    };

    private void refresh() {
      final ProgressBar progressBar = view.findViewById(R.id.loading);
      final ListView joinRequestsList = view.findViewById(R.id.join_requests);
      joinRequestsList.setVisibility(View.GONE);
      progressBar.setVisibility(View.VISIBLE);

      if (alliance == null) {
        MyEmpire myEmpire = EmpireManager.i.getEmpire();
        if (myEmpire != null && myEmpire.getAlliance() != null) {
          AllianceManager.i.fetchAlliance(Integer.parseInt(myEmpire.getAlliance().getKey()), null);
        }
      } else {
        refreshRequests();
      }
    }

    private void refreshRequests() {
      fetchRequests(true);
    }

    private void fetchNextRequests() {
      fetchRequests(false);
    }

    private void fetchRequests(boolean clear) {
      if (fetching) {
        return;
      }
      fetching = true;

      final ProgressBar progressBar = view.findViewById(R.id.loading);
      final ListView joinRequestsList = view.findViewById(R.id.join_requests);

      if (clear) {
        cursor = null;
        requestListAdapter.clearRequests();
      }

      AllianceManager.i.fetchRequests(Integer.parseInt(alliance.getKey()), cursor,
          (empires, requests, cursor) -> {
            fetching = false;
            this.cursor = cursor;
            requestListAdapter.appendRequests(empires, requests);

            joinRequestsList.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
          });
    }

    private class RequestListAdapter extends BaseAdapter {
      private ArrayList<ItemEntry> entries;

      public RequestListAdapter() {
        entries = new ArrayList<>();
      }

      public void clearRequests() {
        entries = new ArrayList<>();
        notifyDataSetChanged();
      }

      public void appendRequests(Map<Integer, Empire> empires, List<AllianceRequest> requests) {
        for (AllianceRequest request : requests) {
          Empire empire;
          if (request.getTargetEmpireID() != null) {
            empire = empires.get(request.getTargetEmpireID());
          } else {
            empire = empires.get(request.getRequestEmpireID());
          }
          if (empire == null) {
            log.error("Empire for %d not found!", request.getTargetEmpireID());
            continue;
          }
          entries.add(new ItemEntry(empire, request));
        }

        notifyDataSetChanged();
      }

      @Override
      public int getViewTypeCount() {
        // The other type is the "please wait..." at the bottom
        return 2;
      }

      @Override
      public int getCount() {
        if (entries == null)
          return 0;
        return entries.size() + (cursor == null ? 0 : 1);
      }

      @Override
      public Object getItem(int position) {
        if (entries == null)
          return null;
        if (entries.size() <= position) {
          return null;
        }
        return entries.get(position);
      }

      @Override
      public int getItemViewType(int position) {
        if (getItem(position) == null) {
          return 1;
        } else {
          return 0;
        }
      }

      @Override
      public long getItemId(int position) {
        ItemEntry entry = (ItemEntry) getItem(position);
        if (entry == null) {
          return 0;
        }
        return entry.request.getID();
      }

      @Override
      public boolean isEnabled(int position) {
        return getItem(position) != null;
      }

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        ItemEntry entry = (ItemEntry) getItem(position);
        Activity activity = getActivity();
        View view = convertView;

        if (view == null) {
          LayoutInflater inflater =
              (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
          if (entry != null) {
            view = inflater.inflate(R.layout.alliance_requests_row, parent, false);
          } else {
            view = inflater.inflate(R.layout.alliance_requests_row_loading, parent, false);
          }
        }

        if (entry == null) {
          //  once this view comes into... view, we'll want to load the next
          // lot of requests
          handler.postDelayed(() -> fetchNextRequests(), 100);

          return view;
        }

        TextView empireName = view.findViewById(R.id.empire_name);
        ImageView empireIcon = view.findViewById(R.id.empire_icon);
        TextView requestDescription = view.findViewById(R.id.request_description);
        ImageView requestStatus = view.findViewById(R.id.request_status);
        TextView requestVotes = view.findViewById(R.id.request_votes);
        TextView message = view.findViewById(R.id.message);
        ImageView pngImage = view.findViewById(R.id.png_image);

        if (entry.empire == null) {
          empireName.setText("...");
          empireIcon.setImageBitmap(null);
        } else {
          empireName.setText(entry.empire.getDisplayName());
          empireIcon.setImageBitmap(EmpireShieldManager.i.getShield(getActivity(), entry.empire));
        }
        requestDescription.setText(String.format(Locale.ENGLISH, "%s requested %s",
            entry.request.getDescription(),
            TimeFormatter.create().format(entry.request.getRequestDate())));
        message.setText(entry.request.getMessage());

        if (entry.request.getPngImage() != null) {
          pngImage.setVisibility(View.VISIBLE);
          pngImage.setImageBitmap(new ImageHelper(entry.request.getPngImage()).getImage());
        } else {
          pngImage.setVisibility(View.GONE);
        }

        if (entry.request.getState().equals(AllianceRequest.RequestState.PENDING)) {
          requestStatus.setVisibility(View.GONE);
          requestVotes.setVisibility(View.VISIBLE);
          if (entry.request.getNumVotes() == 0) {
            requestVotes.setText("0");
          } else {
            requestVotes.setText(String.format(Locale.ENGLISH, "%s%d",
                entry.request.getNumVotes() < 0
                    ? "-"
                    : "+", Math.abs(entry.request.getNumVotes())));
          }
        } else if (entry.request.getState().equals(AllianceRequest.RequestState.ACCEPTED)) {
          requestStatus.setVisibility(View.VISIBLE);
          requestVotes.setVisibility(View.GONE);
          requestStatus.setImageResource(R.drawable.tick);
        } else if (entry.request.getState().equals(AllianceRequest.RequestState.REJECTED)) {
          requestStatus.setVisibility(View.VISIBLE);
          requestVotes.setVisibility(View.GONE);
          requestStatus.setImageResource(R.drawable.cross);
        } else if (entry.request.getState().equals(AllianceRequest.RequestState.WITHDRAWN)) {
          requestStatus.setVisibility(View.VISIBLE);
          requestVotes.setVisibility(View.GONE);
          // TODO: use a different graphic
          requestStatus.setImageResource(R.drawable.cross);
        }

        return view;
      }

      public class ItemEntry {
        // May be null if we have to refresh the empire.
        @Nullable
        public Empire empire;
        public AllianceRequest request;

        public ItemEntry(@Nullable Empire empire, AllianceRequest request) {
          this.empire = empire;
          this.request = request;
        }
      }
    }

    @Override
    public void reloadTab() {
    }
  }
}
