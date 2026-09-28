// This is a generated file! Please edit source .ksy file and use kaitai-struct-compiler to rebuild

package info.oais.infomodel.structure.kaitai.generated;

import io.kaitai.struct.ByteBufferKaitaiStream;
import io.kaitai.struct.KaitaiStruct;
import io.kaitai.struct.KaitaiStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;


/**
 * Tiny demo binary format used to exercise the Kaitai Struct adapter:
 * 
 *   4-byte big-endian signed int   x
 *   4-byte big-endian signed int   y
 *   1-byte unsigned int            label_len
 *   label_len bytes, ASCII         label
 * 
 * Deliberately the same layout as oais-structure-dfdl's point.dfdl.xsd, so
 * the two adapters can be pointed at the same bytes.
 */
public class Point2d extends KaitaiStruct.ReadOnly {
    public Map<String, Integer> _attrStart = new HashMap<String, Integer>();
    public Map<String, Integer> _attrEnd = new HashMap<String, Integer>();
    public Map<String, List<Integer>> _arrStart = new HashMap<String, List<Integer>>();
    public Map<String, List<Integer>> _arrEnd = new HashMap<String, List<Integer>>();

    public static Point2d fromFile(String fileName) throws IOException {
        return new Point2d(new ByteBufferKaitaiStream(fileName));
    }
    public static String[] _seqFields = new String[] { "x", "y", "labelLen", "label" };

    public Point2d(KaitaiStream _io) {
        this(_io, null, null);
    }

    public Point2d(KaitaiStream _io, KaitaiStruct.ReadOnly _parent) {
        this(_io, _parent, null);
    }

    public Point2d(KaitaiStream _io, KaitaiStruct.ReadOnly _parent, Point2d _root) {
        super(_io);
        this._parent = _parent;
        this._root = _root == null ? this : _root;
    }
    public void _read() {
        _attrStart.put("x", this._io.pos());
        this.x = this._io.readS4be();
        _attrEnd.put("x", this._io.pos());
        _attrStart.put("y", this._io.pos());
        this.y = this._io.readS4be();
        _attrEnd.put("y", this._io.pos());
        _attrStart.put("labelLen", this._io.pos());
        this.labelLen = this._io.readU1();
        _attrEnd.put("labelLen", this._io.pos());
        _attrStart.put("label", this._io.pos());
        this.label = new String(this._io.readBytes(labelLen()), StandardCharsets.US_ASCII);
        _attrEnd.put("label", this._io.pos());
    }

    public void _fetchInstances() {
    }
    private int x;
    private int y;
    private int labelLen;
    private String label;
    private Point2d _root;
    private KaitaiStruct.ReadOnly _parent;
    public int x() { return x; }
    public int y() { return y; }
    public int labelLen() { return labelLen; }
    public String label() { return label; }
    public Point2d _root() { return _root; }
    public KaitaiStruct.ReadOnly _parent() { return _parent; }
}
