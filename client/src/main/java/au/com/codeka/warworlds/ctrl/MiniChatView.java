package au.com.codeka.warworlds.ctrl;

import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.navigation.fragment.NavHostFragment;

import au.com.codeka.warworlds.GlobalOptions;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.eventbus.EventHandler;
import au.com.codeka.warworlds.game.chat.ChatFragment;
import au.com.codeka.warworlds.model.ChatConversation;
import au.com.codeka.warworlds.model.ChatManager;
import au.com.codeka.warworlds.model.ChatMessage;
import au.com.codeka.warworlds.model.Empire;
import au.com.codeka.warworlds.model.EmpireManager;

/**
 * This control displays the mini chat window, which displays recent chat messages on each screen.
 */
public class MiniChatView extends RelativeLayout {
  public interface Callback {
    void onNavigateToChatFragment(Integer conversationID);
  }

  private ScrollView scrollView;
  private LinearLayout msgsContainer;
  private Button unreadMsgCount;
  private boolean autoTranslate;

  private Callback callback;

  private static final int MAX_ROWS = 10;

  public MiniChatView(Context context, AttributeSet attrs) {
    super(context, attrs);

    if (this.isInEditMode()) {
      return;
    }

    this.setBackgroundColor(Color.argb(0xaa, 0, 0, 0));

    View view = inflate(context, R.layout.chat_mini_ctrl, null);
    addView(view);

    scrollView = view.findViewById(R.id.scrollview);
    msgsContainer = view.findViewById(R.id.msgs_container);
    autoTranslate = new GlobalOptions().autoTranslateChatMessages();
    unreadMsgCount = view.findViewById(R.id.unread_btn);
    refreshUnreadCountButton();

    msgsContainer.setOnClickListener(v -> callback.onNavigateToChatFragment(-1));
    msgsContainer.setClickable(true);

    unreadMsgCount.setOnClickListener(v -> {
      // move to the first conversation with an unread message
      int conversationID = -1;
      List<ChatConversation> conversations = ChatManager.i.getConversations();
      for (int i = 0; i < conversations.size(); i++) {
        if (conversations.get(i).getUnreadCount() > 0) {
          conversationID = conversations.get(i).getID();
          break;
        }
      }

      callback.onNavigateToChatFragment(conversationID);
    });
  }

  public void setup(Callback callback) {
    this.callback = callback;
  }

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (this.isInEditMode()) {
      return;
    }

    ChatManager.eventBus.register(eventHandler);
    EmpireManager.eventBus.register(eventHandler);

    refreshMessages();
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (this.isInEditMode()) {
      return;
    }

    EmpireManager.eventBus.unregister(eventHandler);
    ChatManager.eventBus.unregister(eventHandler);
  }

  private void refreshMessages() {
    msgsContainer.removeAllViews();
    for (ChatMessage msg : ChatManager.i.getRecentMessages().getMessages()) {
      appendMessage(msg);
    }
  }

  private void appendMessage(final ChatMessage msg) {
    TextView tv = new TextView(getContext());
    tv.setText(Html.fromHtml(msg.format(true, false, autoTranslate)));
    tv.setTag(msg);

    while (msgsContainer.getChildCount() >= MAX_ROWS) {
      msgsContainer.removeViewAt(0);
    }
    msgsContainer.addView(tv);

    scrollToBottom();
  }

  private void scrollToBottom() {
    scrollView.postDelayed(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN), 1);
  }

  private static int getTotalUnreadCount() {
    int numUnread = 0;
    for (ChatConversation conversation : ChatManager.i.getConversations()) {
      numUnread += conversation.getUnreadCount();
    }
    return numUnread;
  }

  private void refreshUnreadCountButton() {
    int numUnread = getTotalUnreadCount();

    if (numUnread > 0) {
      unreadMsgCount.setVisibility(View.VISIBLE);
      unreadMsgCount.setText(String.format(Locale.ENGLISH, "  %d  ", numUnread));
    } else {
      unreadMsgCount.setVisibility(View.GONE);
    }
  }

  private final Object eventHandler = new Object() {
    @EventHandler
    public void onEmpireUpdated(Empire empire) {
      for (int i = 0; i < msgsContainer.getChildCount(); i++) {
        TextView tv = (TextView) msgsContainer.getChildAt(i);
        ChatMessage msg = (ChatMessage) tv.getTag();
        if (msg != null && msg.getEmpireID() == empire.getID()) {
          tv.setText(Html.fromHtml(msg.format(true, false, autoTranslate)));
        }
      }
      scrollToBottom();
    }

    @EventHandler
    public void onMessageAdded(ChatManager.MessageAddedEvent event) {
      appendMessage(event.msg);
      refreshUnreadCountButton();
    }

    @EventHandler
    public void onUnreadMessageCountUpdated(ChatManager.UnreadMessageCountUpdatedEvent event) {
      refreshUnreadCountButton();
    }
  };
}

