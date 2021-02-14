package au.com.codeka.warworlds.game.build;

import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import au.com.codeka.common.Log;
import au.com.codeka.common.model.Design;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.App;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.api.ApiException;
import au.com.codeka.warworlds.api.ApiRequest;
import au.com.codeka.warworlds.api.RequestManager;
import au.com.codeka.warworlds.concurrency.Threads;
import au.com.codeka.warworlds.model.BuildRequest;
import au.com.codeka.warworlds.model.DesignManager;
import au.com.codeka.warworlds.model.EmpireManager;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarManager;
import okhttp3.Response;

import com.google.protobuf.InvalidProtocolBufferException;

import static com.google.common.base.Preconditions.checkNotNull;

public class BuildAccelerateDialog extends DialogFragment {
  private static final Log log = new Log("BuildAccelerateDialog");
  private BuildRequest buildRequest;
  private Star star;
  private View view;

  public void setBuildRequest(Star star, BuildRequest buildRequest) {
    this.buildRequest = buildRequest;
    this.star = star;
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle state) {
    super.onSaveInstanceState(state);
    if (buildRequest != null) {
      Messages.BuildRequest.Builder build_request_pb = Messages.BuildRequest.newBuilder();
      buildRequest.toProtocolBuffer(build_request_pb);
      state.putByteArray(
          "au.com.codeka.warworlds.BuildRequest",
          build_request_pb.build().toByteArray());
    }
    if (star != null) {
      Messages.Star.Builder star_pb = Messages.Star.newBuilder();
      star.toProtocolBuffer(star_pb);
      state.putByteArray("au.com.codeka.warworlds.Star", star_pb.build().toByteArray());
    }
  }

  private void restoreSavedInstanceState(Bundle savedInstanceState) {
    byte[] bytes = savedInstanceState.getByteArray("au.com.codeka.warworlds.Star");
    if (bytes != null) {
      try {
        Messages.Star star_pb;
        star_pb = Messages.Star.parseFrom(bytes);
        star = new Star();
        star.fromProtocolBuffer(star_pb);
      } catch (InvalidProtocolBufferException e) {
        // Ignore.
      }
    }

    bytes = savedInstanceState.getByteArray("au.com.codeka.warworlds.BuildRequest");
    if (bytes != null) {
      try {
        Messages.BuildRequest build_request_pb = Messages.BuildRequest.parseFrom(bytes);
        buildRequest = new BuildRequest();
        buildRequest.fromProtocolBuffer(build_request_pb);
      } catch (InvalidProtocolBufferException e) {
        // Ignore.
      }
    }
  }

  @SuppressLint("InflateParams")
  @Override
  @NonNull
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final Activity activity = requireActivity();
    LayoutInflater inflater = activity.getLayoutInflater();
    view = inflater.inflate(R.layout.build_accelerate_dlg, null);

    if (savedInstanceState != null) {
      restoreSavedInstanceState(savedInstanceState);
    }

    SeekBar accelerateAmount = view.findViewById(R.id.accelerate_amount);
    accelerateAmount.setMax(50);
    accelerateAmount.setProgress(50);
    accelerateAmount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        updatePercentAndCost();
      }
    });
    updatePercentAndCost();

    return new StyledDialog.Builder(getActivity())
        .setView(view)
        .setPositiveButton("Accelerate", (dialog, which) -> accelerateBuild())
        .setNegativeButton("Cancel", null)
        .create();
  }

  /**
   * This algorithm needs to be kept in sync with the on for accelerateBuild() in ctrl/empire.py
   * in the server.
   */
  private void updatePercentAndCost() {
    double accelerateAmount = getAccelerateAmount();

    TextView acceleratePct = view.findViewById(R.id.accelerate_pct);
    acceleratePct.setText(String.format(Locale.ENGLISH, "%d %%", (int) (accelerateAmount * 100)));

    double remainingProgress = 1.0 - buildRequest.getProgress(true);
    double progressToComplete = remainingProgress * accelerateAmount;

    Design design =
        DesignManager.i.getDesign(buildRequest.getDesignKind(), buildRequest.getDesignID());
    double mineralsToUse = design.getBuildCost().getCostInMinerals() * progressToComplete;
    double cost = mineralsToUse * buildRequest.getCount();

    TextView accelerateCost = view.findViewById(R.id.accelerate_cost);
    if (cost < EmpireManager.i.getEmpire().getCash()) {
      accelerateCost.setText(String.format(Locale.ENGLISH, "$%d", (int) cost));
    } else {
      accelerateCost.setText(Html.fromHtml(String.format(Locale.ENGLISH,
          "<font color=\"red\">$%d</font>", (int) cost)));
    }
  }

  private double getAccelerateAmount() {
    SeekBar seekBar = view.findViewById(R.id.accelerate_amount);
    return ((double) seekBar.getProgress() + 50.0) / 100.0;
  }

  private void accelerateBuild() {
    final Activity activity = getActivity();
    dismiss();

    App.i.getTaskRunner().runTask(() -> {
      String url = "stars/" + star.getKey() + "/build/" + buildRequest.getKey() + "/accelerate";
      url += "?amount=" + getAccelerateAmount();

      try {
        ApiRequest request = new ApiRequest.Builder(url, "POST").body(null).build();
        Response resp = RequestManager.i.sendRequestSync(request);
        if (resp != null && !resp.isSuccessful()) {
          throw new ApiException();
        }

        Messages.BuildRequest pb = request.body(Messages.BuildRequest.class);
        if (pb == null) {
          return null;
        }

        BuildRequest br = new BuildRequest();
        br.fromProtocolBuffer(pb);
        App.i.getTaskRunner().runTask(() -> {
          log.debug("Accelerate complete, notifying StarManager to refresh star.");
          // Tell the StarManager that this star has been updated.
          StarManager.i.refreshStar(Integer.parseInt(star.getKey()));
        }, Threads.UI);
      } catch (ApiException e) {
        if (e.getServerErrorCode() > 0) {
          App.i.getTaskRunner().runTask(() -> {
            if (activity != null) {
              new StyledDialog.Builder(activity.getApplicationContext())
                  .setMessage(e.getServerErrorMessage())
                  .setTitle("Error accelerating")
                  .setNeutralButton("OK", null)
                  .create().show(false);
            }
          }, Threads.UI);
        }
      }

      return null;
    }, Threads.BACKGROUND);
  }
}
