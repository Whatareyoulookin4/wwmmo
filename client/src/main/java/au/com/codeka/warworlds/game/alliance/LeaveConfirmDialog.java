package au.com.codeka.warworlds.game.alliance;

import android.app.Dialog;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.model.AllianceManager;

public class LeaveConfirmDialog extends DialogFragment {
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {

    return new StyledDialog.Builder(getActivity())
        .setMessage("Are you sure you want to leave your alliance? Once you leave, you can apply " +
            "for membership to another alliance but you will not be able to re-join this alliance " +
            "without also re-applying.")
        .setTitle("Leave Alliance")
        .setPositiveButton("Leave", (dialog, which) -> {
          onLeaveClick();
          dismiss();
        })
        .setNegativeButton("Cancel", null)
        .create();
  }

  private void onLeaveClick() {
    AllianceManager.i.requestLeave();
  }
}
