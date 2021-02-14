package au.com.codeka.warworlds.model;

import android.content.Context;
import android.util.SparseArray;

import com.google.common.base.Preconditions;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import au.com.codeka.common.Log;
import au.com.codeka.common.model.BaseChatConversationParticipant;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.BackgroundDetector;
import au.com.codeka.warworlds.GlobalOptions;
import au.com.codeka.warworlds.StyledDialog;
import au.com.codeka.warworlds.api.ApiRequest;
import au.com.codeka.warworlds.api.RequestManager;
import au.com.codeka.warworlds.eventbus.EventBus;
import au.com.codeka.warworlds.eventbus.EventHandler;

/**
 * This class keeps track of chats and what-not.
 */
public class ChatManager {
  public static final ChatManager i = new ChatManager();
  private static final Log log = new Log("ChatManager");

  public static final EventBus eventBus = new EventBus();

  private final SparseArray<ChatConversation> conversations = new SparseArray<>();
  private DateTime mostRecentMsg;
  private boolean requesting;
  private boolean isConversationsRefreshing = false;
  private boolean isSetup = false;
  private ChatRecentMessages recentMessages = new ChatRecentMessages();

  public static final int GLOBAL_CONVERSATION_ID = 0;
  public static final int ALLIANCE_CONVERSATION_ID = -1;

  private ChatManager() {
  }

  /**
   * Called when the game starts up, we need to register with the channel and get
   * ready to start receiving chat messages.
   */
  public void setup() {
    BackgroundDetector.eventBus.register(eventHandler);

    clearCache();
    isSetup = true;
  }

  /** Posts a message from us to the server. */
  public void postMessage(final ChatMessage msg) {
    Messages.ChatMessage.Builder msgPb = Messages.ChatMessage.newBuilder()
        .setMessage(msg.getMessage())
        .setAllianceKey(msg.getAllianceKey() == null ? "" : msg.getAllianceKey());
    if (msg.getConversationID() != null) {
      msgPb.setConversationId(msg.getConversationID());
    }

    RequestManager.i.sendRequest(new ApiRequest.Builder("chat", "POST").body(msgPb.build())
        .completeCallback(request -> {
          ChatMessage respMsg = new ChatMessage();
          Messages.ChatMessage pb =
              Preconditions.checkNotNull(request.body(Messages.ChatMessage.class));
          respMsg.fromProtocolBuffer(pb);
          ChatConversation conv = getConversation(respMsg);
          if (conv != null) {
            if (recentMessages.addMessage(respMsg)) {
              conv.addMessage(respMsg);
              eventBus.publish(new MessageAddedEvent(conv, respMsg));
            }
          }
        })
        .errorCallback((request, error) -> {
          // If we get an error, we'll add it as a special message to the conversation so the user
          // at least knows what happened.
          Messages.ChatMessage.Builder pb = Messages.ChatMessage.newBuilder()
              .setAction(Messages.ChatMessage.MessageAction.ErrorMessage)
              .setMessage(error.getErrorMessage())
              .setDatePosted(System.currentTimeMillis() / 1000);
          if (msg.getConversationID() != null) {
            pb.setConversationId(msg.getConversationID());
          }

          ChatConversation conv = getConversation(msg);
          if (conv != null) {
            ChatMessage errMsg = new ChatMessage();
            errMsg.fromProtocolBuffer(pb.build());
            conv.addMessage(errMsg);
            eventBus.publish(new MessageAddedEvent(conv, errMsg));
          }
          log.error("Error in chat: %s", error.getErrorMessage());
        }).build());
  }

  public void addMessage(ChatConversation conv, ChatMessage msg) {
    if (recentMessages.addMessage(msg)) {
      conv.addMessage(msg);
      ChatManager.eventBus.publish(new ChatManager.MessageAddedEvent(conv, msg));
    }
  }

  public ChatRecentMessages getRecentMessages() {
    return recentMessages;
  }

  public void blockEmpire(final Context context, final ChatMessage msg, final Runnable completeRunnable) {
    RequestManager.i.sendRequest(new ApiRequest.Builder("chat/blocks", "POST")
        .body(Messages.ChatBlockRequest.newBuilder().setBlockedEmpireId(msg.getEmpireID()).build())
        .errorCallback((request, error) -> {
          String msg1 = error.getErrorMessage();
          if (msg1 == null || msg1.isEmpty()) {
            msg1 = "An error occurred blocking this empire. Try again later.";
          }

          new StyledDialog.Builder(context).setTitle("Error").setMessage(msg1)
              .setPositiveButton("OK", null).create().show();
        })
        .completeCallback(request -> {
          clearCache();
          completeRunnable.run();
        }).build());
  }

  public void unblockEmpire(final Context context, Empire empire, final Runnable completeRunnable) {
    RequestManager.i.sendRequest(new ApiRequest.Builder("chat/blocks", "DELETE")
        .body(Messages.ChatBlockRequest.newBuilder().setBlockedEmpireId(empire.getID()).build())
        .errorCallback((request, error) -> {
          String msg = error.getErrorMessage();
          if (msg == null || msg.isEmpty()) {
            msg = "An error occurred unblocking this empire. Try again later.";
          }

          new StyledDialog.Builder(context).setTitle("Error").setMessage(msg)
              .setPositiveButton("OK", null).create().show();
        })
        .completeCallback(request -> {
          clearCache();
          completeRunnable.run();
        }).build());
  }

  public void addParticipant(final ChatConversation conversation,
      final String empireName) {
    EmpireManager.i.searchEmpires(empireName, empires -> {
      if (empires.isEmpty()) {
        return;
      }

      addParticipant(conversation, empires.get(0));
    });
  }

  /**
   * Adds the given participant to the given conversation.
   */
  private void addParticipant(final ChatConversation conversation, final Empire empire) {
    Messages.ChatConversationParticipant participantPb =
        Messages.ChatConversationParticipant.newBuilder()
            .setEmpireId(Integer.parseInt(empire.getKey()))
            .build();

    String url = String.format(Locale.ENGLISH, "chat/conversations/%d/participants", conversation.getID());
    RequestManager.i.sendRequest(new ApiRequest.Builder(url, "POST")
        .body(participantPb)
        .completeCallback(new ApiRequest.CompleteCallback() {
          @Override
          public void onRequestComplete(ApiRequest request) {
            // Update our internal representation with the new empire.
            ChatConversation realConv = getConversationByID(conversation.getID());
            realConv.getParticipants()
                .add(new ChatConversationParticipant(Integer.parseInt(empire.getKey())));
            eventBus.publish(new ConversationsUpdatedEvent());
          }
        })
        .build());
  }

  public ChatConversation getConversation(ChatMessage msg) {
    if (!isSetup) {
      return null;
    }

    if (msg.getConversationID() == null) {
      if (msg.getAllianceKey() == null) {
        return getGlobalConversation();
      } else {
        return getAllianceConversation();
      }
    }

    return getConversationByID(msg.getConversationID());
  }

  public ChatConversation getGlobalConversation() {
    if (!isSetup) {
      return null;
    }
    return conversations.get(GLOBAL_CONVERSATION_ID);
  }

  public ChatConversation getAllianceConversation() {
    if (!isSetup) {
      return null;
    }
    return conversations.get(ALLIANCE_CONVERSATION_ID);
  }

  public ArrayList<ChatConversation> getConversations() {
    ArrayList<ChatConversation> convos = new ArrayList<>();
    synchronized (conversations) {
      for (int i = 0; i < conversations.size(); i++) {
        convos.add(conversations.valueAt(i));
      }
    }
    return convos;
  }

  public ChatConversation getConversationByID(int conversationID) {
    synchronized (conversations) {
      if (conversations.indexOfKey(conversationID) < 0) {
        log.info("Conversation #%d hasn't been created yet, creating now.", conversationID);
        conversations.append(conversationID, new ChatConversation(conversationID));

        // It's OK to call this, it won't do anything if a refresh is already happening.
        refreshConversations();
      }
    }
    return conversations.get(conversationID);
  }

  /**
   * Start a new conversation with the given empireID (can be null to start an empty conversation).
   */
  public void startConversation(final Integer empireID) {
    // if we already have a conversation going with this guy, just reuse that one.
    if (empireID != null) {
      for (int index = 0; index < conversations.size(); index++) {
        ChatConversation conversation = conversations.valueAt(index);
        List<BaseChatConversationParticipant> participants = conversation.getParticipants();
        if (participants == null || participants.size() != 2) {
          continue;
        }
        if (participants.get(0).getEmpireID() == empireID
            || participants.get(1).getEmpireID() == empireID) {
          eventBus.publish(new ConversationStartedEvent(conversation));
          return;
        }
      }
    }

    Messages.ChatConversation.Builder conversationPb = Messages.ChatConversation.newBuilder()
            .addParticipants(Messages.ChatConversationParticipant.newBuilder()
                .setEmpireId(Integer.parseInt(EmpireManager.i.getEmpire().getKey()))
                .setIsMuted(false));
    if (empireID != null) {
      conversationPb.addParticipants(Messages.ChatConversationParticipant.newBuilder()
          .setEmpireId(empireID)
          .setIsMuted(false));
    }

    RequestManager.i.sendRequest(new ApiRequest.Builder("chat/conversations", "POST")
        .body(conversationPb.build())
        .completeCallback(request -> {
          Messages.ChatConversation conversationPb1 = request.body(Messages.ChatConversation.class);
          ChatConversation conversation = new ChatConversation(conversationPb1.getId());
          conversation.fromProtocolBuffer(conversationPb1);
          conversations.put(conversation.getID(), conversation);
          eventBus.publish(new ConversationStartedEvent(conversation));
        })
        .build());
  }

  public void leaveConversation(final ChatConversation conversation) {
    String url = "chat/conversations/" + conversation.getID() + "/participants/"
        + EmpireManager.i.getEmpire().getKey();

    RequestManager.i.sendRequest(new ApiRequest.Builder(url, "DELETE")
        .completeCallback(request -> {
          synchronized (conversations) {
            log.info("Leaving conversation #%d.", conversation.getID());
            conversations.remove(conversation.getID());
          }

          eventBus.publish(new ConversationsUpdatedEvent());
        }).build());
  }

  public void muteConversation(ChatConversation conversation) {
    if (conversation.getID() <= 0) {
      return;
    }
    new GlobalOptions().muteConversation(conversation.getID(), true);
  }

  public void unmuteConversation(ChatConversation conversation) {
    if (conversation.getID() <= 0) {
      return;
    }
    new GlobalOptions().muteConversation(conversation.getID(), false);
  }

  private final Object eventHandler = new Object() {
    @EventHandler(thread=EventHandler.UI_THREAD)
    public void onBackgroundChangeEvent(BackgroundDetector.BackgroundChangeEvent event) {
      if (!event.isInBackground) {
        requestMessages(mostRecentMsg);
      }
    }
  };

  private void clearCache() {
    recentMessages.clear();

    conversations.clear();
    conversations.append(GLOBAL_CONVERSATION_ID, new ChatConversation(GLOBAL_CONVERSATION_ID));
    if (EmpireManager.i.getEmpire().getAlliance() != null) {
      conversations.append(ALLIANCE_CONVERSATION_ID, new ChatConversation
          (ALLIANCE_CONVERSATION_ID));
    }
    refreshConversations();

    // fetch all chats from the last 24 hours
    mostRecentMsg = (new DateTime()).minusDays(1);
    requestMessages(mostRecentMsg);
  }

  private void refreshConversations() {
    if (isConversationsRefreshing) {
      return;
    }
    isConversationsRefreshing = true;

    RequestManager.i.sendRequest(new ApiRequest.Builder("chat/conversations", "GET")
        .completeCallback(request -> {
          Messages.ChatConversations pb = request.body(Messages.ChatConversations.class);
          if (pb == null) {
            return;
          }
          // this comes back most recent first, but we work in the opposite order...
          for (Messages.ChatConversation conversation_pb : pb.getConversationsList()) {
            ChatConversation conversation = new ChatConversation(conversation_pb.getId());
            conversation.fromProtocolBuffer(conversation_pb);
            synchronized (conversations) {
              if (conversations.indexOfKey(conversation_pb.getId()) < 0) {
                conversations.append(conversation_pb.getId(), conversation);
              } else {
                conversations.get(conversation_pb.getId()).update(conversation);
              }
            }
          }

          // now that we've updated all of the conversations, if there's any left that are
          // "needs update" then we'll have to queue another one... :/
          boolean needsUpdate = false;
          synchronized (conversations) {
            for (int i = 0; i < conversations.size(); i++) {
              ChatConversation conversation = conversations.valueAt(i);
              if (conversation.getID() > 0 && conversation.needUpdate()) {
                needsUpdate = true;
                // However, we want to make sure this is the LAST time that "refreshConversations"
                // is called for this set of conversations (for example, if one of our
                // conversations doesn't exist on the server, it won't get updated by another
                // call) so we call this to make sure "needs update" is reset on all of them
                // first.
                conversation.update(conversation);
              }
            }
          }

          if (needsUpdate) {
            refreshConversations();
          } else {
            eventBus.publish(new ConversationsUpdatedEvent());
          }

          isConversationsRefreshing = false;
        }).build());
  }

  private void requestMessages(final DateTime after) {
    requestMessages(after, null, null, null, msgs -> {
      for (ChatMessage msg : msgs) {
        ChatConversation conversation = getConversation(msg);
        conversation.addMessage(msg);
        if (recentMessages.addMessage(msg)) {
          eventBus.publish(new MessageAddedEvent(conversation, msg));
        }
      }
    });
  }

  public void requestMessages(final DateTime after, final DateTime before, final Integer max,
      final Integer conversationID, final MessagesFetchedListener handler) {
    if (requesting) {
      return;
    }
    requesting = true;

    String url = "chat?after=" + (after.getMillis() / 1000);
    if (before != null) {
      url += "&before=" + (before.getMillis() / 1000);
    }
    if (max != null) {
      url += "&max=" + max;
    }
    if (conversationID != null) {
      url += "&conversation=" + conversationID;
    }

    RequestManager.i.sendRequest(new ApiRequest.Builder(url, "GET")
        .completeCallback(request -> {
          Messages.ChatMessages pb = request.body(Messages.ChatMessages.class);
          if (pb == null) {
            return;
          }
          ArrayList<ChatMessage> msgs = new ArrayList<>();

          // these comes back most recent first, but we work in the opposite order...
          for (int i = pb.getMessagesCount() - 1; i >= 0; i--) {
            ChatMessage msg = new ChatMessage();
            msg.fromProtocolBuffer(pb.getMessages(i));
            msgs.add(msg);
          }

          handler.onMessagesFetched(msgs);
          requesting = false;
        })
        .build());
  }

  public static class MessageAddedEvent {
    public ChatConversation conversation;
    public ChatMessage msg;

    public MessageAddedEvent(ChatConversation conversation, ChatMessage msg) {
      this.conversation = conversation;
      this.msg = msg;
    }
  }

  public static class ConversationStartedEvent {
    public ChatConversation conversation;

    public ConversationStartedEvent(ChatConversation conversation) {
      this.conversation = conversation;
    }
  }

  /**
   * Event fired when conversation list updates.
   */
  public static class ConversationsUpdatedEvent {
  }

  public static class UnreadMessageCountUpdatedEvent {
    public int numUnread;

    public UnreadMessageCountUpdatedEvent(int numUnread) {
      this.numUnread = numUnread;
    }
  }

  public interface MessagesFetchedListener {
    void onMessagesFetched(List<ChatMessage> msgs);
  }
}
