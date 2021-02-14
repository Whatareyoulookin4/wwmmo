package au.com.codeka.warworlds.game.starfield;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Locale;

import au.com.codeka.common.Log;
import au.com.codeka.common.model.BaseEmpire;
import au.com.codeka.common.model.BaseFleet;
import au.com.codeka.common.model.BaseStar;
import au.com.codeka.common.model.BaseStar.StarType;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.ServerGreeter;
import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.eventbus.EventHandler;
import au.com.codeka.warworlds.game.ScoutReportDialog;
import au.com.codeka.warworlds.game.SitrepActivity;
import au.com.codeka.warworlds.game.StarRenameDialog;
import au.com.codeka.warworlds.game.alliance.AllianceActivity;
import au.com.codeka.warworlds.game.empire.EmpireActivity;
import au.com.codeka.warworlds.game.solarsystem.SolarSystemActivity;
import au.com.codeka.warworlds.game.wormhole.WormholeFragment;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.EmpireShieldManager;
import au.com.codeka.warworlds.model.Fleet;
import au.com.codeka.warworlds.model.MyEmpire;
import au.com.codeka.warworlds.model.Planet;
import au.com.codeka.warworlds.model.PurchaseManager;
import au.com.codeka.warworlds.model.ShieldManager;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarManager;
import au.com.codeka.warworlds.model.billing.IabException;
import au.com.codeka.warworlds.model.billing.IabHelper;
import au.com.codeka.warworlds.model.billing.Purchase;
import au.com.codeka.warworlds.model.billing.SkuDetails;
import au.com.codeka.warworlds.ui.BaseFragment;

/**
 * The {@link StarfieldFragment} is the "home" screen of the game, and displays the
 * starfield where you scroll around and interact with stars, etc.
 */
public class StarfieldFragment extends BaseFragment {
  private static final Log log = new Log("StarfieldActivity");
  private Star selectedStar;
  private Fleet selectedFleet;
  private Star homeStar;
  private View bottomPane;
  private Button allianceBtn;
  private SelectionDetailsView selectionDetailsView;

  private Purchase starRenamePurchase;

  private Star starToSelect;
  private String starKeyToSelect;
  private Fleet fleetToSelect;

  private static final int EMPIRE_REQUEST = 2;
  private static final int SITREP_REQUEST = 3;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.starfield, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    bottomPane = view.findViewById(R.id.bottom_pane);
    selectionDetailsView = (SelectionDetailsView) view.findViewById(R.id.selection_details);

//    if (isPortrait()) {
//      InfobarView infobar = (InfobarView) view.findViewById(R.id.infobar);
//      infobar.hideEmpireName();
//    }

    // "Rename" button
    // "View" button
    // "Intel" button
    selectionDetailsView.setHandlers(
        planet -> navigateToPlanet(selectedStar, planet, false),
        fleet -> {
          if (selectedStar == null) {
            return; //??
          }

          openEmpireActivityAtFleet(selectedStar, fleet);
        },
        v -> onRenameClick(), v -> {
          if (selectedStar == null) {
            return;
          }


          Bundle args = new Bundle();
          args.putInt("au.com.codeka.warworlds.StarID", selectedStar.getID());
          NavHostFragment.findNavController(this).navigate(R.id.solarSystemFragment, args);
        }, v -> {
          if (selectedStar != null) {
            ScoutReportDialog dialog = new ScoutReportDialog();
            dialog.setStar(selectedStar);
            dialog.show(getChildFragmentManager(), "");
          }
        }, star -> {
          //starfield.scrollTo(star.getSectorX(), star.getSectorY(),
          //    star.getOffsetX(), Sector.SECTOR_SIZE - star.getOffsetY())
        } );

    view.findViewById(R.id.empire_btn).setOnClickListener((View.OnClickListener) v -> openEmpireActivity());
    view.findViewById(R.id.sitrep_btn).setOnClickListener((View.OnClickListener) v -> openSitrepActivity());

    allianceBtn = view.findViewById(R.id.alliance_btn);
    allianceBtn.setOnClickListener(v -> onAllianceClick());

    if (savedInstanceState != null) {
      Star selectedStar = null;
      Fleet selectedFleet = null;

      try {
        byte[] star_bytes = savedInstanceState.getByteArray("au.com.codeka.warworlds.SelectedStar");
        if (star_bytes != null) {
          Messages.Star star_pb = Messages.Star.parseFrom(star_bytes);
          selectedStar = new Star();
          selectedStar.fromProtocolBuffer(star_pb);
        }
      } catch (InvalidProtocolBufferException ignore) {
      }

      try {
        byte[] fleet_bytes =
            savedInstanceState.getByteArray("au.com.codeka.warworlds.SelectedFleet");
        if (fleet_bytes != null) {
          Messages.Fleet fleet_pb = Messages.Fleet.parseFrom(fleet_bytes);
          selectedFleet = new Fleet();
          selectedFleet.fromProtocolBuffer(fleet_pb);
        }
      } catch (InvalidProtocolBufferException ignore) {
      }

      starToSelect = selectedStar;
      fleetToSelect = selectedFleet;
    }

    hideBottomPane(true);
  }

  private boolean processIntent() {/*
    Intent intent = getIntent();
    if (intent != null && intent.getExtras() != null) {
      String starKey = intent.getExtras().getString("au.com.codeka.warworlds.StarKey");
      if (starKey != null) {
        long sectorX = intent.getExtras().getLong("au.com.codeka.warworlds.SectorX");
        long sectorY = intent.getExtras().getLong("au.com.codeka.warworlds.SectorY");
        int offsetX = intent.getExtras().getInt("au.com.codeka.warworlds.OffsetX");
        int offsetY = intent.getExtras().getInt("au.com.codeka.warworlds.OffsetY");
        starfield.scrollTo(sectorX, sectorY, offsetX, Sector.SECTOR_SIZE - offsetY);
      }
      setIntent(null);
      return true;
    }*/
    return false;
  }

  @Override
  public void onResume() {
    super.onResume();
    ServerGreeter.waitForHello(
        requireActivity(), (ServerGreeter.HelloCompleteHandler) (success, greeting) -> {
      if (!success) {
        return;
      }
/*
      if (starToSelect != null && starfield.getScene() != null) {
        selectedStar = starToSelect;
        starfield.getScene().selectStar(starToSelect.getKey());
        starfield.scrollTo(starToSelect);
        starToSelect = null;
      }
*//*
      if (fleetToSelect != null && starfield.getScene() != null) {
        starfield.getScene().selectFleet(fleetToSelect.getKey());
        fleetToSelect = null;
      }
*/
      MyEmpire myEmpire = EmpireManager.i.getEmpire();
      if (myEmpire == null) {
        return;
      }

      BaseStar homeStar = myEmpire.getHomeStar();

      boolean doNotNavigateToHomeStar = processIntent();
      if (homeStar != null && (
          StarfieldFragment.this.homeStar == null || !StarfieldFragment.this.homeStar.getKey()
          .equals(homeStar.getKey()))) {
        StarfieldFragment.this.homeStar = (Star) homeStar;
        if (!doNotNavigateToHomeStar) {
//          starfield.scrollTo(homeStar);
        }
      }
    });

    if (starRenamePurchase != null) {
      showStarRenamePopup(starRenamePurchase);
      starRenamePurchase = null;
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    StarManager.eventBus.register(eventHandler);
    ShieldManager.eventBus.register(eventHandler);
    requireMainActivity().getStarfieldManager().addTapListener(tapListener);
  }

  @Override
  public void onStop() {
    super.onStop();
    requireMainActivity().getStarfieldManager().removeTapListener(tapListener);
    StarManager.eventBus.unregister(eventHandler);
    ShieldManager.eventBus.unregister(eventHandler);
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    if (selectedStar != null) {
      Messages.Star.Builder star_pb = Messages.Star.newBuilder();
      selectedStar.toProtocolBuffer(star_pb);
      state.putByteArray("au.com.codeka.warworlds.SelectedStar", star_pb.build().toByteArray());
    }

    if (selectedFleet != null) {
      Messages.Fleet.Builder fleet_pb = Messages.Fleet.newBuilder();
      selectedFleet.toProtocolBuffer(fleet_pb);
      state.putByteArray("au.com.codeka.warworlds.SelectedFleet", fleet_pb.build().toByteArray());
    }
  }

  public void openEmpireActivityAtFleet(Star star, Fleet fleet) {
    Intent intent = new Intent(requireContext(), EmpireActivity.class);
    intent.putExtra("au.com.codeka.warworlds.StarID", star.getID());
    intent.putExtra("au.com.codeka.warworlds.FleetID", Integer.parseInt(fleet.getKey()));
    startActivityForResult(intent, EMPIRE_REQUEST);
  }

  public void openEmpireActivity() {
    Intent intent = new Intent(requireContext(), EmpireActivity.class);
    startActivityForResult(intent, EMPIRE_REQUEST);
  }

  public void openSitrepActivity() {
    Intent intent = new Intent(requireContext(), SitrepActivity.class);
    startActivityForResult(intent, SITREP_REQUEST);
  }

  public void onAllianceClick() {
    Intent intent = new Intent(requireContext(), AllianceActivity.class);
    startActivity(intent);
  }

  public int getBottomPaneHeight() {
    return bottomPane.getHeight();
  }

  private void hideBottomPane(boolean instant) {
    applyBottomPaneAnimation(false, instant);
  }

  private void showBottomPane() {
    applyBottomPaneAnimation(true, false);
  }

  private void applyBottomPaneAnimation(boolean isOpen, boolean instant) {
    float dp;
//    if (isPortrait()) {
      if (isOpen) {
        dp = 180;
      } else {
        dp = 34;
      }
//    } else {
//      if (isOpen) {
//        dp = 200;
//      } else {
//        dp = 100;
//      }
//    }

    Resources r = getResources();
    float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());

//    if (isPortrait()) {
      if (instant) {
        RelativeLayout.LayoutParams lp =
            (RelativeLayout.LayoutParams) bottomPane.getLayoutParams();
        lp.height = (int) px;
        bottomPane.setLayoutParams(lp);
      } else {
        applyBottomPaneAnimationPortrait(px);
      }
/*    } else {
      RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) allianceBtn.getLayoutParams();
      if (isOpen) {
        // NB: removeRule is not available until API level 17 :/
        lp.addRule(RelativeLayout.BELOW, 0);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 1);
        lp.topMargin =
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 34, r.getDisplayMetrics());
      } else {
        lp.addRule(RelativeLayout.BELOW, R.id.empire_btn);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
        lp.topMargin =
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, r.getDisplayMetrics());
      }
      allianceBtn.setLayoutParams(lp);

      if (instant) {
        lp = (RelativeLayout.LayoutParams) bottomPane.getLayoutParams();
        lp.width = (int) px;
        bottomPane.setLayoutParams(lp);
      } else {
        applyBottomPaneAnimationLandscape(px);
      }
    }*/
  }

  private void applyBottomPaneAnimationLandscape(final float pxWidth) {
    Animation a = new Animation() {
      private int initialWidth;

      @Override
      protected void applyTransformation(float interpolatedTime, Transformation t) {
        final int newWidth = initialWidth + (int) ((pxWidth - initialWidth) * interpolatedTime);
        bottomPane.getLayoutParams().width = newWidth;
        bottomPane.requestLayout();
      }

      @Override
      public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        initialWidth = width;
      }

      @Override
      public boolean willChangeBounds() {
        return true;
      }
    };
    a.setDuration(500);
    bottomPane.setAnimation(a);

  }

  private void applyBottomPaneAnimationPortrait(final float pxHeight) {
    Animation a = new Animation() {
      private int initialHeight;

      @Override
      protected void applyTransformation(float interpolatedTime, Transformation t) {
        final int newHeight =
            initialHeight + (int) ((pxHeight - initialHeight) * interpolatedTime);
        bottomPane.getLayoutParams().height = newHeight;
        bottomPane.requestLayout();
      }

      @Override
      public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        initialHeight = height;
      }

      @Override
      public boolean willChangeBounds() {
        return true;
      }
    };
    a.setDuration(500);
    bottomPane.setAnimation(a);
  }

  /**
   * Navigates to the given planet in the given star. Starts the SolarSystemActivity.
   *
   * @param scrollView If {@code true}, we'll also scroll the current view so that given star is
   *                   centered on the given star.
   */
  public void navigateToPlanet(Star star, Planet planet, boolean scrollView) {
    navigateToPlanet(star.getStarType(), star.getSectorX(), star.getSectorY(), star.getID(),
        star.getOffsetX(), star.getOffsetY(), planet.getIndex(), scrollView);
  }

  private void navigateToPlanet(StarType starType, long sectorX, long sectorY, int starID,
      int starOffsetX, int starOffsetY, int planetIndex, boolean scrollView) {
    if (scrollView) {
 //     starfield.scrollTo(sectorX, sectorY, starOffsetX, Sector.SECTOR_SIZE - starOffsetY);
    }

    Bundle args = new Bundle();
    args.putInt("au.com.codeka.warworlds.StarID", starID);
    args.putInt("au.com.codeka.warworlds.PlanetIndex", planetIndex);
    if (starType.getType() == Star.Type.Wormhole) {
      NavHostFragment.findNavController(this).navigate(R.id.wormholeFragment, args);
    } else {
      NavHostFragment.findNavController(this).navigate(R.id.solarSystemFragment, args);
    }
  }

  public void navigateToFleet(final String starKey, final String fleetKey) {
    Star star = StarManager.i.getStar(Integer.parseInt(starKey));
    if (star == null) {
      StarManager.eventBus.register(new Object() {
        @EventHandler
        public void onStarUpdated(Star star) {
          if (star.getKey().equals(starKey)) {
            navigateToFleet(star, star.findFleet(fleetKey));
            StarManager.eventBus.unregister(this);
          }
        }
      });

    } else {
      BaseFleet fleet = star.findFleet(fleetKey);
      if (fleet != null) {
        navigateToFleet(star, star.findFleet(fleetKey));
      }
    }
  }

  public void navigateToFleet(Star star, BaseFleet fleet) {
    int offsetX = star.getOffsetX();
    int offsetY = star.getOffsetY();

//    starfield
//        .scrollTo(star.getSectorX(), star.getSectorY(), offsetX, Sector.SECTOR_SIZE - offsetY);

//    if (starfield.getScene() == null) {
//      // TODO: we should probably remember the fleet then navigate when the scene is ready.
//      return;
//    }
//    if (fleet.getState() == Fleet.State.MOVING) {
//      starfield.getScene().selectFleet(fleet.getKey());
 //   } else {
 //     starfield.getScene().selectStar(star.getKey());
 //   }
  }

  public void onRenameClick() {
    // If you're an Empire-level patron, skip the cost stuff, it's free for you!
    if (EmpireManager.i.getEmpire().getPatreonLevel() == BaseEmpire.PatreonLevel.EMPIRE) {
      doRenameStar();
      return;
    }

    SkuDetails starRenameSku;
    try {
      starRenameSku = PurchaseManager.i.getInventory().getSkuDetails("star_rename");
    } catch (IabException e) {
      log.error("Couldn't get SKU details!", e);
      return;
    }

    new StyledDialog.Builder(requireContext()).setMessage(String.format(Locale.ENGLISH,
        "Renaming stars costs %s. If you wish to continue, you'll be directed " +
            "to the Play Store where you can purchase a one-time code to rename this " +
            "star. Are you sure you want to continue?", starRenameSku.getPrice()))
        .setTitle("Rename Star").setNegativeButton("Cancel", null)
        .setPositiveButton("Rename", (dialog, which) -> {
          doRenameStar();
          dialog.dismiss();
        }).create().show();
  }

  public void doRenameStar() {
    if (selectedStar == null) {
      return;
    }

    try {
      PurchaseManager.i
          .launchPurchaseFlow(requireActivity(), "star_rename", (result, info) -> {
            if (selectedStar == null) {
              return;
            }

            Purchase purchase = info;
            boolean isSuccess = result.isSuccess();
            if (result.isFailure()
                && result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
              // if they've already purchased a star-renamed, but not reclaimed it, then
              // we let them through anyway.
              log.debug("Already purchased a star-rename, we'll just show the popup.");
              isSuccess = true;
              try {
                purchase = PurchaseManager.i.getInventory().getPurchase("star_rename");
              } catch (IabException e) {
                log.warning("Got an exception getting the purchase details.", e);
              }
            }

            if (isSuccess) {
              try {
                showStarRenamePopup(purchase);
              } catch (IllegalStateException e) {
                // this can be called before the activity is resumed, so we just set a
                // flag that'll cause us to pop up the dialog when the activity is resumed.
                log.warning(
                    "Got an error trying to show the popup, we'll try again in a second...");
                starRenamePurchase = purchase;
              }
            }
          });
    } catch (IabException e) {
      log.error("Couldn't get SKU details!", e);
    }
  }

  private void showStarRenamePopup(Purchase purchase) {
    StarRenameDialog dialog = new StarRenameDialog();
    dialog.setPurchaseInfo(purchase);
    dialog.setStar(selectedStar);
    dialog.show(getChildFragmentManager(), "");
  }

  /*

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);

    if (requestCode == SOLAR_SYSTEM_REQUEST && intent != null) {
      boolean wasSectorUpdated =
          intent.getBooleanExtra("au.com.codeka.warworlds.SectorUpdated", false);
      long sectorX = intent.getLongExtra("au.com.codeka.warworlds.SectorX", 0);
      long sectorY = intent.getLongExtra("au.com.codeka.warworlds.SectorY", 0);
      String starKey = intent.getStringExtra("au.com.codeka.warworlds.StarKey");

      if (wasSectorUpdated) {
        SectorManager.i.refreshSector(sectorX, sectorY);
      } else if (starKey != null && starfield.getScene() != null) {
        // make sure we re-select the star you had selected before.
        starfield.getScene().selectStar(starKey);
      } else if (starKey != null) {
        starKeyToSelect = starKey;
      }
    } else if (requestCode == EMPIRE_REQUEST && intent != null) {
      EmpireActivity.EmpireActivityResult res = EmpireActivity.EmpireActivityResult
          .fromValue(intent.getIntExtra("au.com.codeka.warworlds.Result", 0));

      final long sectorX = intent.getLongExtra("au.com.codeka.warworlds.SectorX", 0);
      final long sectorY = intent.getLongExtra("au.com.codeka.warworlds.SectorY", 0);
      final int starOffsetX = intent.getIntExtra("au.com.codeka.warworlds.StarOffsetX", 0);
      final int starOffsetY = intent.getIntExtra("au.com.codeka.warworlds.StarOffsetY", 0);
      final String starKey = intent.getStringExtra("au.com.codeka.warworlds.StarKey");

      if (res == EmpireActivity.EmpireActivityResult.NavigateToPlanet) {
        final int planetIndex = intent.getIntExtra("au.com.codeka.warworlds.PlanetIndex", 0);

        Star star = StarManager.i.getStar(Integer.parseInt(starKey));
        if (star == null) {
          StarManager.eventBus.register(new Object() {
            @EventHandler
            public void onStarUpdated(Star star) {
              if (star.getKey().equals(starKey)) {
                navigateToPlanet(star.getStarType(), sectorX, sectorY, starKey, starOffsetX,
                    starOffsetY, planetIndex, true);
                StarManager.eventBus.unregister(this);
              }
            }
          });
        } else {
          navigateToPlanet(star.getStarType(), sectorX, sectorY, starKey, starOffsetX, starOffsetY,
              planetIndex, true);
        }
      } else if (res == EmpireActivity.EmpireActivityResult.NavigateToFleet) {
        String fleetKey = intent.getStringExtra("au.com.codeka.warworlds.FleetKey");

        navigateToFleet(starKey, fleetKey);
      }
    }
  }
*/
  private void handleDeselect() {
    selectedStar = null;
    selectedFleet = null;
    selectionDetailsView.deselect();
    hideBottomPane(false);
  }

  public Object eventHandler = new Object() {
    @EventHandler
    public void onStarFetched(Star star) {
      if (selectedStar != null && selectedStar.getID() == star.getID()) {
        selectedStar = star;
        selectionDetailsView.showStar(selectedStar);
      }
    }

    @EventHandler
    public void onShieldUpdated(ShieldManager.ShieldUpdatedEvent event) {
      EmpireShieldManager.i.clearTextureCache();

      if (selectedFleet != null) {/*
        StarfieldActivity.this.getEngine().runOnUpdateThread(new Runnable() {
          @Override
          public void run() {
            // this will cause the selected fleet info to redraw and hence the shield
            StarfieldActivity.this.onFleetSelected(selectedFleet);
          }
        });*/
      }
    }
  };

  private final StarfieldManager.TapListener tapListener = new StarfieldManager.TapListener() {
    @Override
    public void onStarTapped(@Nullable Star star) {
      onStarSelected(star);
    }

    @Override
    public void onFleetTapped(@Nullable Star star, @Nullable Fleet fleet) {
      onFleetSelected(fleet);
    }
  };

  private void onStarSelected(Star star) {
    if (star == null) {
      handleDeselect();
      return;
    }

    if (selectedStar != null && selectedStar.getKey().equals(star.getKey())) {
      selectionDetailsView.showStar(selectedStar);
      return;
    }
    selectedStar = star;
    selectedFleet = null;

    selectionDetailsView.loading();
    showBottomPane();

    // force the star to refresh itself
    StarManager.i.refreshStar(star.getID());
  }

  private void onFleetSelected(final Fleet fleet) {
    if (fleet == null) {
      handleDeselect();
      return;
    }

    selectedStar = null;
    selectedFleet = fleet;

    selectionDetailsView.showFleet(selectedFleet);
    showBottomPane();
  }
}
