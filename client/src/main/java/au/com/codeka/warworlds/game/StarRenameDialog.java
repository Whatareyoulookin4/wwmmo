package au.com.codeka.warworlds.game;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.android.billingclient.api.Purchase;

import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.model.PurchaseManager;
import au.com.codeka.warworlds.model.Sprite;
import au.com.codeka.warworlds.model.SpriteDrawable;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarImageManager;
import au.com.codeka.warworlds.model.StarManager;

public class StarRenameDialog extends DialogFragment {
  private Purchase mPurchase;
  private View mView;
  private Star mStar;

  public void setPurchaseInfo(Purchase purchase) {
    mPurchase = purchase;
  }

  public void setStar(Star star) {
    mStar = star;
  }

  @SuppressLint("InflateParams")
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater inflater = getActivity().getLayoutInflater();
    mView = inflater.inflate(R.layout.star_rename_dlg, null);

    EditText starNewName = mView.findViewById(R.id.star_newname);
    TextView starName = mView.findViewById(R.id.star_name);
    ImageView starIcon = mView.findViewById(R.id.star_icon);

    starName.setText(mStar.getName());
    starNewName.setText(mStar.getName());

    int imageSize = (int) (mStar.getSize() * mStar.getStarType().getImageScale() * 2);
    Sprite starSprite = StarImageManager.getInstance().getSprite(mStar, imageSize, true);
    starIcon.setImageDrawable(new SpriteDrawable(starSprite));

    starNewName.requestFocus();

    StyledDialog.Builder b = new StyledDialog.Builder(getActivity());
    b.setView(mView);
    b.setNeutralButton("Rename", (dialog, which) -> {
      onRenameClicked();
      dialog.dismiss();
    });
    return b.create();
  }

  public void onRenameClicked() {
    EditText starNewName = mView.findViewById(R.id.star_newname);
    final String newStarName = starNewName.getText().toString();

    StarManager.i.renameStar(mPurchase, mStar, newStarName,
        (star, successful, errorMessage) ->
            PurchaseManager.i.consume(mPurchase, (billingResult, purchase) -> {
              // TODO: check result?
            }));
  }
}
