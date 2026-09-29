package info.oais.archive.manager.model;

/**
 * An application a graph node's data can be viewed with, offered on its
 * right-click menu: the data is sent to the application, running on the
 * viewer's own computer, over SAMP.
 *
 * @param id          e.g. {@code topcat}
 * @param label       the menu item, e.g. "View with TOPCAT"
 * @param mtype       the SAMP message type to send, e.g. {@code table.load.votable}
 * @param clientName  the application's SAMP name (matched case-insensitively), to send it to that one only
 * @param votableUrl  the data as VOTable (relative to the site)
 * @param manifestUrl its Representation Information manifest (relative to the site)
 */
public record GraphViewer(String id, String label, String mtype, String clientName, String votableUrl,
                          String manifestUrl) {
}
