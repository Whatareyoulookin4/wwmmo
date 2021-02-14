package au.com.codeka.warworlds.game.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import au.com.codeka.common.TimeFormatter;
import au.com.codeka.common.model.BaseBuildRequest;
import au.com.codeka.common.model.BaseFleet;
import au.com.codeka.common.model.Design;
import au.com.codeka.common.model.DesignKind;
import au.com.codeka.common.model.ShipDesign;
import au.com.codeka.warworlds.FeatureFlags;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.ctrl.FleetListRow;
import au.com.codeka.warworlds.game.DesignHelper;
import au.com.codeka.warworlds.game.NotesDialog;
import au.com.codeka.warworlds.game.build.BuildFragment.BaseTabFragment;
import au.com.codeka.warworlds.model.BuildManager;
import au.com.codeka.warworlds.model.BuildRequest;
import au.com.codeka.warworlds.model.Colony;
import au.com.codeka.warworlds.model.DesignManager;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.Fleet;
import au.com.codeka.warworlds.model.FleetManager;
import au.com.codeka.warworlds.model.Star;

public class ShipsFragment extends BaseTabFragment {
  private Star star;
  private Colony colony;
  private ShipListAdapter shipListAdapter;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    View v = inflater.inflate(R.layout.build_ships_tab, container, false);

    shipListAdapter = new ShipListAdapter();

    final ListView availableDesignsList = v.findViewById(R.id.ship_list);
    availableDesignsList.setAdapter(shipListAdapter);
    availableDesignsList.setOnItemClickListener((parent, view, position, id) -> {
      ShipListAdapter.ItemEntry entry =
          (ShipListAdapter.ItemEntry) shipListAdapter.getItem(position);
      if (entry.fleet == null && entry.buildRequest == null) {
        BuildConfirmDialog dialog = new BuildConfirmDialog();
        dialog.setup(entry.design, star, colony);
        dialog.show(getChildFragmentManager(), "");
      } else if (entry.fleet != null && entry.buildRequest == null) {
        if (ShipDesign.Upgrade.getAvailableUpgrades(
            entry.fleet.getDesign(), entry.fleet, FeatureFlags.get()).isEmpty()) {
          // No available upgrades, don't show the dialog.
          return;
        }

        ShipUpgradeDialog dialog = new ShipUpgradeDialog();
        dialog.setup(star, colony, entry.fleet);
        dialog.show(getChildFragmentManager(), "");
      }
    });

    availableDesignsList.setOnItemLongClickListener((adapterView, view, position, id) -> {
      final ShipListAdapter.ItemEntry entry =
          (ShipListAdapter.ItemEntry) shipListAdapter.getItem(position);

      NotesDialog dialog = new NotesDialog();
      dialog.setup(entry.fleet == null ? entry.buildRequest.getNotes() : entry.fleet.getNotes(),
          notes -> {
            if (entry.fleet != null) {
              entry.fleet.setNotes(notes);
            } else if (entry.buildRequest != null) {
              entry.buildRequest.setNotes(notes);
            }
            shipListAdapter.notifyDataSetChanged();

            if (entry.fleet != null) {
              FleetManager.i.updateNotes(entry.fleet);
            } else {
              BuildManager.i.updateNotes(entry.buildRequest.getKey(), notes);
            }
          });

      dialog.show(getChildFragmentManager(), "");
      return true;
    });

    return v;
  }

  @Override
  protected void refresh(Star star, Colony colony) {
    this.star = star;
    this.colony = colony;

    ArrayList<Fleet> fleets = new ArrayList<>();
    for (BaseFleet baseFleet : star.getFleets()) {
      if (baseFleet.getEmpireKey() != null
          && baseFleet.getEmpireKey().equals(EmpireManager.i.getEmpire().getKey())) {
        fleets.add((Fleet) baseFleet);
      }
    }

    ArrayList<BuildRequest> buildRequests = new ArrayList<>();
    for (BaseBuildRequest baseBuildRequest : star.getBuildRequests()) {
      if (baseBuildRequest.getEmpireKey().equals(EmpireManager.i.getEmpire().getKey())
          && baseBuildRequest.getDesignKind() == DesignKind.SHIP) {
        buildRequests.add((BuildRequest) baseBuildRequest);
      }
    }

    shipListAdapter.setShips(DesignManager.i.getDesigns(DesignKind.SHIP), fleets, buildRequests);
  }

  /** This adapter is used to populate the list of ship designs in our view. */
  private class ShipListAdapter extends BaseAdapter {
    private List<ItemEntry> entries;

    private static final int HEADING_TYPE = 0;
    private static final int EXISTING_SHIP_TYPE = 1;
    private static final int NEW_SHIP_TYPE = 2;

    public void setShips(Map<String, Design> designs, ArrayList<Fleet> fleets,
        ArrayList<BuildRequest> buildRequests) {
      entries = new ArrayList<>();

      entries.add(new ItemEntry("New Ships"));
      for (Design design : designs.values()) {
        entries.add(new ItemEntry((ShipDesign) design));
      }

      entries.add(new ItemEntry("Existing Ships"));
      for (Fleet fleet : fleets) {
        if (fleet.getState() != Fleet.State.IDLE) {
          continue;
        }
        ItemEntry entry = new ItemEntry(fleet);
        for (BuildRequest buildRequest : buildRequests) {
          if (buildRequest.getExistingFleetID() != null
              && ((int) buildRequest.getExistingFleetID()) == Integer.parseInt(fleet.getKey())) {
            entry.buildRequest = buildRequest;
          }
        }
        entries.add(entry);
      }
      for (BuildRequest buildRequest : buildRequests) {
        if (buildRequest.getExistingFleetID() != null) {
          continue;
        }
        entries.add(new ItemEntry(buildRequest));
      }

      notifyDataSetChanged();
    }

    /**
     * We have three types of items, the "headings", the list of existing
     * buildings and the list of building designs.
     */
    @Override
    public int getViewTypeCount() {
      return 3;
    }

    @Override
    public boolean isEnabled(int position) {
      if (getItemViewType(position) == HEADING_TYPE) {
        return false;
      }

      return true;
    }

    @Override
    public int getCount() {
      if (entries == null)
        return 0;
      return entries.size();
    }

    @Override
    public int getItemViewType(int position) {
      if (entries == null)
        return 0;

      if (entries.get(position).heading != null)
        return HEADING_TYPE;
      if (entries.get(position).design != null)
        return NEW_SHIP_TYPE;
      return EXISTING_SHIP_TYPE;
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

      View view = convertView;
      if (view == null) {
        LayoutInflater inflater = (LayoutInflater) requireActivity().getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);
        if (entry.heading != null) {
          view = new TextView(getActivity());
        } else {
          view = inflater.inflate(R.layout.buildings_design, parent, false);
        }
      }

      if (entry.heading != null) {
        TextView tv = (TextView) view;
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setText(entry.heading);
      } else if (entry.fleet != null || entry.buildRequest != null) {
        // existing fleet/upgrading fleet
        ImageView icon = view.findViewById(R.id.building_icon);
        LinearLayout row1 = view.findViewById(R.id.building_row1);
        TextView row2 = view.findViewById(R.id.building_row2);
        TextView row3 = view.findViewById(R.id.building_row3);
        TextView level = view.findViewById(R.id.building_level);
        TextView levelLabel = view.findViewById(R.id.building_level_label);
        ProgressBar progress = view.findViewById(R.id.building_progress);
        TextView notes = view.findViewById(R.id.notes);

        Fleet fleet = entry.fleet;
        BuildRequest buildRequest = entry.buildRequest;
        ShipDesign design = (ShipDesign) DesignManager.i.getDesign(DesignKind.SHIP,
            (fleet != null ? fleet.getDesignID() : buildRequest.getDesignID()));

        DesignHelper.setDesignIcon(design, icon);

        level.setVisibility(View.GONE);
        levelLabel.setVisibility(View.GONE);

        row1.removeAllViews();
        FleetListRow.populateFleetNameRow(getActivity(), row1, fleet, design);
        if (buildRequest != null) {
          String verb = (fleet == null ? "Building" : "Upgrading");
          row2.setText(Html.fromHtml(String.format(Locale.ENGLISH,
              "<font color=\"#0c6476\">%s:</font> %d %%, %s left", verb,
              (int) buildRequest.getPercentComplete(),
              TimeFormatter.create().format(buildRequest.getRemainingTime()))));

          row3.setVisibility(View.GONE);
          progress.setVisibility(View.VISIBLE);
          progress.setProgress((int) buildRequest.getPercentComplete());
        } else {
          StringBuilder upgrades = new StringBuilder();
          ArrayList<ShipDesign.Upgrade> availableUpgrades =
              ShipDesign.Upgrade.getAvailableUpgrades(design, fleet, FeatureFlags.get());
          for (ShipDesign.Upgrade upgrade : availableUpgrades) {
            if (upgrades.length() > 0) {
              upgrades.append(", ");
            }
            upgrades.append(upgrade.getDisplayName());
          }

          progress.setVisibility(View.GONE);
          if (upgrades.length() == 0) {
            row2.setText(Html.fromHtml("Available upgrades: <i>none</i>"));
          } else {
            row2.setText("Available upgrades: " + upgrades);
          }

          String requiredHtml = design.getDependenciesHtml(colony);
          row3.setVisibility(View.VISIBLE);
          row3.setText(Html.fromHtml(requiredHtml));
        }

        if (fleet != null && fleet.getNotes() != null) {
          notes.setText(fleet.getNotes());
          notes.setVisibility(View.VISIBLE);
        } else if (buildRequest != null && buildRequest.getNotes() != null) {
          notes.setText(buildRequest.getNotes());
          notes.setVisibility(View.VISIBLE);
        } else {
          notes.setText("");
          notes.setVisibility(View.GONE);
        }
      } else {
        // new fleet
        ImageView icon = view.findViewById(R.id.building_icon);
        LinearLayout row1 = view.findViewById(R.id.building_row1);
        TextView row2 = view.findViewById(R.id.building_row2);
        TextView row3 = view.findViewById(R.id.building_row3);

        view.findViewById(R.id.building_progress).setVisibility(View.GONE);
        view.findViewById(R.id.building_level).setVisibility(View.GONE);
        view.findViewById(R.id.building_level_label).setVisibility(View.GONE);
        view.findViewById(R.id.notes).setVisibility(View.GONE);

        ShipDesign design = entry.design;
        DesignHelper.setDesignIcon(design, icon);

        row1.removeAllViews();
        FleetListRow.populateFleetNameRow(getActivity(), row1, null, design);
        String requiredHtml = design.getDependenciesHtml(colony);
        row2.setText(Html.fromHtml(requiredHtml));

        row3.setVisibility(View.GONE);
      }

      return view;
    }

    public class ItemEntry {
      public ShipDesign design;
      public Fleet fleet;
      public BuildRequest buildRequest;
      public String heading;

      public ItemEntry(ShipDesign design) {
        this.design = design;
      }

      public ItemEntry(BuildRequest buildRequest) {
        this.buildRequest = buildRequest;
      }

      public ItemEntry(Fleet fleet) {
        this.fleet = fleet;
      }

      public ItemEntry(String heading) {
        this.heading = heading;
      }
    }
  }
}
