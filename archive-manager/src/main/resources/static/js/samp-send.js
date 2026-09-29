/*
 * Sends a table to TOPCAT (or any other SAMP client) running on the viewer's
 * own computer, through the SAMP Web Profile: XML-RPC to the SAMP hub at
 * http://localhost:21012/, which asks the viewer to allow this page first.
 * The table is sent by URL (mtype table.load.votable), so TOPCAT fetches the
 * VOTable itself.
 *
 * Usage: <button data-samp-votable="/api/data-objects/ID/votable" data-samp-name="Readings">
 *        with an element <span data-samp-status> after it for messages.
 */
(function () {
    "use strict";
    var HUB = "http://localhost:21012/";

    function escape(s) {
        return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    }

    function value(v) {
        if (Array.isArray(v)) {
            return "<value><array><data>" + v.map(value).join("") + "</data></array></value>";
        }
        if (v !== null && typeof v === "object") {
            return "<value><struct>" + Object.keys(v).map(function (k) {
                return "<member><name>" + escape(k) + "</name>" + value(v[k]) + "</member>";
            }).join("") + "</struct></value>";
        }
        return "<value><string>" + escape(v) + "</string></value>";
    }

    function parse(v) {
        var c = v.firstElementChild;
        if (!c) {
            return v.textContent;
        }
        if (c.tagName === "struct") {
            var o = {};
            Array.prototype.forEach.call(c.children, function (m) {
                o[m.getElementsByTagName("name")[0].textContent] = parse(m.getElementsByTagName("value")[0]);
            });
            return o;
        }
        if (c.tagName === "array") {
            return Array.prototype.map.call(c.getElementsByTagName("data")[0].children, parse);
        }
        return c.textContent;
    }

    function call(method, params) {
        var body = '<?xml version="1.0"?><methodCall><methodName>' + method + "</methodName><params>"
            + params.map(function (p) { return "<param>" + value(p) + "</param>"; }).join("")
            + "</params></methodCall>";
        return fetch(HUB, {method: "POST", headers: {"Content-Type": "text/xml"}, body: body})
            .then(function (r) {
                if (!r.ok) {
                    throw new Error("the SAMP hub answered HTTP " + r.status);
                }
                return r.text();
            })
            .then(function (text) {
                var doc = new DOMParser().parseFromString(text, "text/xml");
                if (doc.getElementsByTagName("fault").length) {
                    var fault = parse(doc.getElementsByTagName("fault")[0].getElementsByTagName("value")[0]);
                    throw new Error(fault.faultString || "the SAMP hub refused");
                }
                return parse(doc.getElementsByTagName("param")[0].getElementsByTagName("value")[0]);
            });
    }

    function send(button) {
        var status = button.parentNode.querySelector("[data-samp-status]");
        var say = function (text) { if (status) { status.textContent = text; } };
        var url = new URL(button.getAttribute("data-samp-votable"), window.location.href).href;
        var name = button.getAttribute("data-samp-name") || "Data";
        var key = null;
        say("Contacting TOPCAT (allow this page if it asks)...");
        call("samp.webhub.register", [{"samp.name": "OAIS archive"}])
            .then(function (reg) {
                key = reg["samp.private-key"];
                return call("samp.webhub.declareMetadata", [key, {"samp.name": "OAIS archive",
                    "samp.description.text": "Sends Data Objects from the archive as VOTable"}]);
            })
            .then(function () {
                return call("samp.webhub.notifyAll", [key, {"samp.mtype": "table.load.votable",
                    "samp.params": {"url": url, "name": name}}]);
            })
            .then(function () {
                say("Sent to TOPCAT.");
                return call("samp.webhub.unregister", [key]);
            })
            .catch(function (e) {
                say("Couldn't send it: " + e.message + ". Is TOPCAT (or another SAMP hub) running on this "
                    + "computer? You can also open the VOTable link in TOPCAT yourself.");
            });
    }

    document.addEventListener("click", function (event) {
        var button = event.target.closest("[data-samp-votable]");
        if (button) {
            event.preventDefault();
            send(button);
        }
    });
})();
