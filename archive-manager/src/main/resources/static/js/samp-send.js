/*
 * Sends data to TOPCAT, SPLAT, DS9, Aladin or another SAMP application running
 * on the viewer's own computer, through the SAMP Web Profile: XML-RPC to the
 * SAMP hub at http://localhost:21012/, which asks the viewer to allow this
 * page first. The data is sent by URL (a VOTable or FITS image the archive
 * serves), so the application fetches it itself, and to the named application
 * only, since several may accept the same kind of message.
 *
 * Buttons: <button data-samp-url="/api/data-objects/ID/votable" data-samp-name="Readings"
 *                  data-samp-mtype="table.load.votable" data-samp-client="topcat">
 *          with an element [data-samp-status] beside it for messages.
 * Script:  OaisSamp.send({url, name, mtype, client, say: function (message) {...}})
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

    /** The message for mtype: a VOTable by URL, as a table or as a spectrum, or a FITS image by URL. */
    function message(mtype, url, name) {
        var params = {"url": url, "name": name};
        if (mtype === "spectrum.load.ssa-generic") {
            params.meta = {"Access.Format": "application/x-votable+xml", "Target.Name": name};
        }
        if (mtype === "image.load.fits") {
            params["image-id"] = url;
        }
        return {"samp.mtype": mtype, "samp.params": params};
    }

    /** The id of the running application called like `client` that accepts mtype, or null. */
    function findClient(key, mtype, client) {
        return call("samp.webhub.getSubscribedClients", [key, mtype]).then(function (subscribed) {
            var ids = Object.keys(subscribed || {});
            return Promise.all(ids.map(function (id) {
                return call("samp.webhub.getMetadata", [key, id]).then(function (meta) {
                    return {id: id, name: String((meta || {})["samp.name"] || "")};
                });
            })).then(function (clients) {
                var match = clients.filter(function (c) {
                    return c.name.toLowerCase().indexOf(client.toLowerCase()) >= 0;
                })[0];
                return match ? match.id : null;
            });
        });
    }

    function send(options) {
        var say = options.say || function () {};
        var app = options.label || options.client || "the application";
        var url = new URL(options.url, window.location.href).href;
        var key = null;
        say("Contacting " + app + " (allow this page if it asks)...");
        return call("samp.webhub.register", [{"samp.name": "OAIS archive"}])
            .then(function (reg) {
                key = reg["samp.private-key"];
                return call("samp.webhub.declareMetadata", [key, {"samp.name": "OAIS archive",
                    "samp.description.text": "Sends Data Objects from the archive as VOTable or FITS"}]);
            })
            .then(function () {
                return options.client ? findClient(key, options.mtype, options.client) : null;
            })
            .then(function (target) {
                var msg = message(options.mtype, url, options.name || "Data");
                if (target) {
                    return call("samp.webhub.notify", [key, target, msg]);
                }
                if (options.client) {
                    throw new Error(app + " isn't running, or isn't connected to the SAMP hub");
                }
                return call("samp.webhub.notifyAll", [key, msg]);
            })
            .then(function () {
                say("Sent to " + app + ".");
                return call("samp.webhub.unregister", [key]);
            })
            .catch(function (e) {
                if (!key && e instanceof TypeError) {
                    // fetch itself failed: nothing is listening at the hub's address.
                    say("Couldn't send it: no SAMP hub is running on this computer. Start one -- TOPCAT and "
                        + "Aladin start their own; in SPLAT, start its internal hub from the Interop menu -- and "
                        + "try again. You can also open the data link in " + app + " yourself.");
                } else {
                    say("Couldn't send it: " + e.message + ". Is " + app + " running on this computer? You can "
                        + "also open the data link in it yourself.");
                }
                if (key) {
                    call("samp.webhub.unregister", [key]).catch(function () {});
                }
            });
    }

    window.OaisSamp = {send: send};

    document.addEventListener("click", function (event) {
        var button = event.target.closest("[data-samp-url], [data-samp-votable]");
        if (!button) {
            return;
        }
        event.preventDefault();
        var status = button.parentNode.querySelector("[data-samp-status]");
        send({
            url: button.getAttribute("data-samp-url") || button.getAttribute("data-samp-votable"),
            name: button.getAttribute("data-samp-name"),
            mtype: button.getAttribute("data-samp-mtype") || "table.load.votable",
            client: button.getAttribute("data-samp-client"),
            label: button.getAttribute("data-samp-label"),
            say: function (text) { if (status) { status.textContent = text; } }
        });
    });
})();
