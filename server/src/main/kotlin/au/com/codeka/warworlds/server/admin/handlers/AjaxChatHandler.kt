package au.com.codeka.warworlds.server.admin.handlers

import au.com.codeka.warworlds.common.proto.ChatMessage
import au.com.codeka.warworlds.common.proto.ChatMessagesPacket
import au.com.codeka.warworlds.server.handlers.RequestException
import au.com.codeka.warworlds.server.world.chat.ChatManager

/**
 * Handler for /admin/ajax/chat which lets us send messages from the special 'server' user.
 */
class AjaxChatHandler : AjaxHandler() {
  public override fun get() {
    when (request.getParameter("action")) {
      "recv" -> {
        var roomId: Long? = null
        if (request.getParameter("roomId") != null) {
          roomId = request.getParameter("roomId").toLong()
        }
        val lastMsgTime = request.getParameter("lastMsgTime").toLong()
        handleRecvRequest(roomId, lastMsgTime)
      }
      else -> throw RequestException(400, "Unknown action: ${request.getParameter("action")}")
    }
  }

  public override fun post() {
    when (request.getParameter("action")) {
      "send" -> {
        val msg = request.getParameter("msg")
        handleSendRequest(msg)
      }
      else -> throw RequestException(400, "Unknown action: ${request.getParameter("action")}")
    }
  }

  private fun handleSendRequest(msg: String) {
    ChatManager.i.send(null, ChatMessage(
        action = ChatMessage.MessageAction.Normal,
        message = msg,
        date_posted = System.currentTimeMillis()))
  }

  private fun handleRecvRequest(roomId: Long?, lastMsgId: Long) {
    val messages = ChatManager.i.getMessages(roomId, lastMsgId, System.currentTimeMillis())
    if (messages.isEmpty()) {
      // TODO: wait for a message before returning...
    }
    setResponseJson(ChatMessagesPacket(messages = messages))
  }
}