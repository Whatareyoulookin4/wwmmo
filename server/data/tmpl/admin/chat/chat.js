$("form input[type=text]").focus();

$("form#sendmsg").on("click", "input[type=submit]", function(evnt) {
  evnt.preventDefault();

  $val = $("form input[type=text]")
  sendMessage($val.val());
  $val.val("").focus();
});

var original_page_title = document.title;
var alliances = {};
var msgs = [];
var num_unread = 0;

$.ajax({
  "url": "/realms/{{realm}}/alliances",
  "dataType": "json",
  "success": function(data) {
    for (var i = 0; i < data.alliances.length; i++) {
      alliances[data.alliances[i].key] = data.alliances[i].name;
    }
    refreshMessages();
  }
});

function prependMessage(msg, refresh) {
  if (msg.action) {
    // TODO: this will be like "participant joined" or "participant left" etc.
    return;
  }

  msgs.unshift(msg);

  if (refresh) {
    refreshMessages();
  }
}

function appendMessage(msg, refresh) {
  if (msg.action) {
    // TODO: this will be like "participant joined" or "participant left" etc.
    return;
  }

  msgs.push(msg);

  num_unread++;
  document.title = "("+num_unread+") "+original_page_title;

  if (refresh) {
    refreshMessages();
  }
}

function refreshMessages() {
  $("section#messages").empty();
  $("section#messages").append($("<div class='fetch-older'><a href='javascript:fetchOlder()'>Fetch older messages</a></div>"));

  for (var i = 0; i < msgs.length; i++) {
    var div = formatMessage(msgs[i]);
    $("section#messages").append(div);
  }
}

function refreshMessage(msg) {
  var div = msg.div;
  div.replaceWith(formatMessage(msg));
}

function formatMessage(msg) {
  msg.loaded = false;
  if (typeof msg.empire == "undefined") {
    if (msg.empire_key) {
      msg.empire = "Loading...";
      empireStore.getEmpire(msg.empire_key, function(empire) {
        msg.empire = empire;
        if (msg.loaded) {
          refreshMessage(msg);
        }
      });
    } else {
      msg.empire = "[SERVER]";
    }
  }

  var dt = new Date();
  dt.setTime(msg.date_posted * 1000);

  function zeroPad(str, n) {
    var padded = "0000000000"+str;
    return padded.substr(padded.length - n);
  }
  var ampm = "am";
  var dtstr = dt.getFullYear()+"-"+zeroPad(dt.getMonth() + 1, 2)+"-"+zeroPad(dt.getDate(), 2);
  if (dt.getHours() == 12) {
    dtstr += " 12";
    ampm = "pm";
  } else if (dt.getHours() == 0) {
    dtstr += " 12";
    ampm = "am";
  } else if (dt.getHours() > 12) {
    dtstr += " "+zeroPad(dt.getHours() - 12, 2);
    ampm = "pm";
  } else {
    dtstr += " "+zeroPad(dt.getHours(), 2);
  }
  dtstr += ":"+zeroPad(dt.getMinutes(), 2)+" "+ampm;
  var msgstr = dtstr;

  var style = "";

  if (msg.alliance_key) {
    style += "background: #ccc;";
    if (typeof alliances[msg.alliance_key] != "undefined") {
      msgstr += " : <em>" + alliances[msg.alliance_key] + "</em>";
    } else {
      msgstr += " : <em>Alliance</em>";
    }
  } else if (msg.conversation_id) {
    style += "background: #fcc;";
    msgstr += " : <em>" + conversations[msg.conversation_id] + "</em>";
  } else {
    msgstr += " : <em>Global</em>";
  }

  if (msg.empire_key) {
    msgstr += " : <b><a href=\"/realms/{{realm}}/admin/empire/search#search=";
    msgstr += msg.empire_key+"\">"+msg.empire+"</a></b>";
  } else {
    msgstr += " : <b>"+msg.empire+"</b>";
  }
  msgstr += "</div><div class=\"msg-content\" style=\""+style+"\">";
  if (msg.message_en) {
    msgstr += "<span class=\"translated\">"+msg.message_en+"</span>";
  } else {
    msgstr += msg.message;
  }
  if (msg.profanity_level == 1) {
    msgstr = "<span class=\"profanity-mild\">"+msgstr+"</span>";
  } else if (msg.profanity_level == 2) {
    msgstr = "<span class=\"profanity-strong\">"+msgstr+"</span>";
  }

  var div = $("<div class=\"msg-header\" style=\""+style+"\">"+msgstr+"</div>");
  div.data("dt", msg.date_posted);
  msg.div = div;
  msg.loaded = true;
  return div;
}

function sendMessage(msg) {
  var url = "/realms/{{realm}}/chat";
  $.ajax({
    url: url, type: "POST", contentType: "application/json",
    data: JSON.stringify({
      "message": msg,
      "action": 0
    }),
    success: function(data) {
      // ??
    },
    error: function(xhr, status, err) {
      // todo: handle errors
      //appendMessage("[ERROR] An error occured sending the last message");
    }
  });
}

// The "newest" message we've fetched so far, which we'll use to check for new messages.
window.lastFetch = new Date();
window.lastFetch.setDate(lastFetch.getDate() - 4);

// The "oldest" message we've fetched so far, which we'll use to fetch older messages.
window.firstFetch = lastFetch;

function fetchMessages(scrollToBottom) {
  $.ajax({
    "url": "/realms/{{realm}}/chat?after="+parseInt(window.lastFetch.getTime() / 1000),
    "method": "get",
    "dataType": "json",
    "success": function(data) {
      window.lastFetch = new Date();

      if (typeof data.messages == "undefined") {
        return;
      }

      for (var i = data.messages.length - 1; i >= 0; i--) {
        var msg = data.messages[i];
        appendMessage(msg, false);
      }

      refreshMessages();
      if (scrollToBottom) {
        var $section = $("section#messages");
        $section.get(0).scrollTo(0, $section.get(0).scrollHeight);
      }
    }
  });
}

function fetchOlder() {
  $("section#messages div.fetch-older").replaceWith("<div class='spinner'></div>");

  var before = parseInt(window.firstFetch.getTime() / 1000);
  window.firstFetch.setDate(firstFetch.getDate() - 4);
  var after = parseInt(window.firstFetch.getTime() / 1000);
  $.ajax({
    "url": "/realms/{{realm}}/chat?before="+before+"&after="+after,
    "method": "get",
    "dataType": "json",
    "success": function(data) {
      if (data.messages) {
        for (var i = 0; i < data.messages.length; i++) {
          var msg = data.messages[i];
          prependMessage(msg, false);
        }
      }

      refreshMessages();
    }
  });
}

function checkScrollToBottom() {
  var $section = $("section#messages");
  if ($section.scrollTop() + $section.height() >= $section.get(0).scrollHeight) {
    num_unread = 0;
    document.title = original_page_title;
  }
}

$("section#messages").scroll(function() {
  checkScrollToBottom();
});

fetchMessages(true);
setInterval(fetchMessages, 15000);

