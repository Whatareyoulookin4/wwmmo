
const empireStore = (function() {
  let storedEmpires = window.localStorage.getItem("empires");
  if (!storedEmpires) {
    storedEmpires = {};
  } else {
    storedEmpires = JSON.parse(storedEmpires);
  }
  const empires = {};

  function defaultCallback(empire) {
    $("span[data-empireid], img[data-empireid]").each(function(i, elem) {
      const $this = $(this);
      if ($this.data("empireid") === empire.id) {
        if (this.tagName === "SPAN") {
          if ($this.data("nolink") === "1") {
            $this.html(empire.display_name);
          } else {
            $this.html("<a href=\"/admin/empires/" + empire.id + "\">" + empire.display_name + "</a>");
          }
        } else if (this.tagName === "IMG") {
          $this.attr("src", "/render/empire/"
              + empire.id + "/"
              + $this.attr("width") + "x" + $this.attr("height")
              + "/mdpi.png");
        }
      }
    });
  }

  const callbacks = {};

  return {
    getEmpire: function(empireId, callback) {
      if (typeof callback === "undefined") {
        callback = defaultCallback;
      }
      if (!empireId) {
        setTimeout(function() {
          callback({
            id: 0,
            display_name: "Native"
          });
        });
        return;
      }

      // If we have a cached empire already, just return that and we're done.
      if (typeof empires[empireId] != "undefined") {
        setTimeout(function() { callback(empires[empireId]); }, 0);
        return;
      }

      // If we have one stored, we can return that for now, but we'll want to re-fetch from the
      // server anyway, to ensure we have the freshest.
      if (typeof storedEmpires[empireId] != "undefined") {
        setTimeout(function() { callback(storedEmpires[empireId]); }, 0);
      }

      // If we already have a callback for this empireId, it means we're already fetching it, so
      // just add to the list for when we get the fresh data.
      if (typeof callbacks[empireId] != "undefined") {
        setTimeout(function() { callback(empires[empireId]); }, 0);
        return;
      }

      callbacks[empireId] = [callback];
      $.ajax({
        url: "/admin/ajax/empire",
        data: {
          id: empireId
        },
        success: function(data) {
          empires[data.id] = data;
          window.localStorage.setItem("empires", JSON.stringify(empires));
          for (let i = 0; i < callbacks[empireId].length; i++) {
            callbacks[empireId][i](data);
          }
        }
      });
    },

    // Gets all of the empires that we have stored.
    getAllEmpires: function() {
      return empires;
    }
  }
})();
