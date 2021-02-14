package au.com.codeka.warworlds.game.chat;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.navigation.fragment.NavHostFragment;

import au.com.codeka.Clipboard;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.game.empire.EmpireFragment;
import au.com.codeka.warworlds.game.empire.EnemyEmpireActivity;
import au.com.codeka.warworlds.model.ChatManager;
import au.com.codeka.warworlds.model.ChatMessage;
import au.com.codeka.warworlds.model.Empire;
import au.com.codeka.warworlds.model.EmpireManager;

import com.google.protobuf.InvalidProtocolBufferException;

public class ChatMessageDialog extends DialogFragment {
  private View mView;
  private ChatMessage mChatMessage;
  private Empire mEmpire;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater inflater = getActivity().getLayoutInflater();
    mView = inflater.inflate(R.layout.chat_popup_dlg, null);

    Bundle args = getArguments();
    mEmpire = new Empire();
    mChatMessage = new ChatMessage();
    try {
      mEmpire.fromProtocolBuffer(Messages.Empire.parseFrom(args.getByteArray("au.com.codeka.warworlds.Empire")));
      mChatMessage.fromProtocolBuffer(Messages.ChatMessage.parseFrom(args.getByteArray("au.com.codeka.warworlds.ChatMessage")));
    } catch (InvalidProtocolBufferException e) {
    }

    Button copyMessageTextBtn = mView.findViewById(R.id.copy_text_btn);
    copyMessageTextBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Clipboard.copyText(getActivity(), "message", mChatMessage.getMessage());
        Toast.makeText(getActivity(), "Message copied to clipboard.", Toast.LENGTH_SHORT).show();
        dismiss();
      }
    });

    Button viewEmpireBtn = mView.findViewById(R.id.view_empire_btn);
    viewEmpireBtn.setOnClickListener(v -> {
      if (mEmpire.getKey().equals(EmpireManager.i.getEmpire().getKey())) {
        Intent intent = new Intent(getActivity(), EmpireFragment.class);
        startActivity(intent);
      } else {
        // TODO
//        NavHostFragment.findNavController(this).navigate(R.id.empireFragment, TODO);
//        Intent intent = new Intent(getActivity(), EnemyEmpireActivity.class);
//        intent.putExtra("au.com.codeka.warworlds.EmpireKey", mEmpire.getKey());
//        getActivity().startActivity(intent);
      }

      dismiss();
    });

    Button blockBtn = mView.findViewById(R.id.block_btn);
    blockBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onBlockClick();
        dismiss();
      }
    });

    Button privateMessageBtn = mView.findViewById(R.id.private_message_btn);
    privateMessageBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ChatManager.i.startConversation(mEmpire.getKey());
        dismiss();
      }
    });

    return new StyledDialog.Builder(getActivity())
        .setTitle(mEmpire.getDisplayName())
        .setView(mView)
        .create();
  }

  private void onBlockClick() {
    final Context context = getActivity();
    new StyledDialog.Builder(context)
        .setTitle("Block empire")
        .setMessage("Blocking an empire means you won't hear from this empire any more. You can "
            + " unblock them again from the 'Blocked empires' button above."
            + " Are you sure you want to block " + mEmpire.getDisplayName() + "?")
        .setPositiveButton("Block", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            doBlock(context);
            dialog.dismiss();
          }
        }).setNegativeButton("Cancel", null)
        .create().show();
  }

  private void doBlock(final Context context) {
    ChatManager.i.blockEmpire(context, mChatMessage, new Runnable() {
      @Override
      public void run() {
        new StyledDialog.Builder(context)
            .setTitle("Blocked")
            .setMessage(
                String.format(
                    "%s has been blocked, you will not see any more messages from them.",
                    mEmpire.getDisplayName()))
            .setPositiveButton("OK", null)
            .create()
            .show();
      }
    });
  }
}
