// This is a generated file! Please edit source .ksy file and use kaitai-struct-compiler to rebuild

package info.oais.infomodel.structure.kaitai.generated;

import io.kaitai.struct.ByteBufferKaitaiStream;
import io.kaitai.struct.KaitaiStruct;
import io.kaitai.struct.KaitaiStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;


/**
 * Tiny demo CSV format used to exercise the Kaitai Struct adapter on a
 * variable number of repeated, delimited (rather than fixed-width binary)
 * records:
 * 
 *   one or more lines, each   x,y,label\n
 * 
 * x and y are kept as raw comma-terminated text here, the same way Kaitai
 * Struct's own str/terminator idiom for delimited text always does - a .ksy
 * wanting them as actual integers would add a computed `instance` calling
 * `.to_i` on the raw text (left out here to keep the example simple;
 * TableCombiner's row-selector coerces
 * the text automatically - see StructureNodeBackedTable's Javadoc on
 * "Coercion"). label is the rest of the line up to the newline.
 * 
 * Deliberately the same three-column shape (x, y, label) as point2d.ksy /
 * point.dfdl.xsd's single point record, so the same TableSemanticRepInfo
 * columns used there also describe a whole CSV file's worth of rows here -
 * see oais-structure-demo's points-table-view-kaitai.xml. Unlike point2d.ksy,
 * this format repeats (`repeat: eos`), so it also exercises
 * StructureNodeKind.ARRAY - see that enum's Javadoc on ARRAY vs. repeated
 * COMPOSITE siblings, and TableViewSpecificationReader's "array" row select
 * mode added to read rows out of one.
 */
public class CsvPoints extends KaitaiStruct.ReadOnly {
    public Map<String, Integer> _attrStart = new HashMap<String, Integer>();
    public Map<String, Integer> _attrEnd = new HashMap<String, Integer>();
    public Map<String, List<Integer>> _arrStart = new HashMap<String, List<Integer>>();
    public Map<String, List<Integer>> _arrEnd = new HashMap<String, List<Integer>>();

    public static CsvPoints fromFile(String fileName) throws IOException {
        return new CsvPoints(new ByteBufferKaitaiStream(fileName));
    }
    public static String[] _seqFields = new String[] { "rows" };

    public CsvPoints(KaitaiStream _io) {
        this(_io, null, null);
    }

    public CsvPoints(KaitaiStream _io, KaitaiStruct.ReadOnly _parent) {
        this(_io, _parent, null);
    }

    public CsvPoints(KaitaiStream _io, KaitaiStruct.ReadOnly _parent, CsvPoints _root) {
        super(_io);
        this._parent = _parent;
        this._root = _root == null ? this : _root;
    }
    public void _read() {
        _attrStart.put("rows", this._io.pos());
        this.rows = new ArrayList<Row>();
        {
            int i = 0;
            while (!this._io.isEof()) {
                {
                    List<Integer> _posList = _arrStart.get("rows");
                    if (_posList == null) {
                        _posList = new ArrayList<Integer>();
                        _arrStart.put("rows", _posList);
                    }
                    _posList.add(this._io.pos());
                }
                Row _t_rows = new Row(this._io, this, _root);
                try {
                    _t_rows._read();
                } finally {
                    this.rows.add(_t_rows);
                }
                {
                    List<Integer> _posList = _arrEnd.get("rows");
                    if (_posList == null) {
                        _posList = new ArrayList<Integer>();
                        _arrEnd.put("rows", _posList);
                    }
                    _posList.add(this._io.pos());
                }
                i++;
            }
        }
        _attrEnd.put("rows", this._io.pos());
    }

    public void _fetchInstances() {
        for (int i = 0; i < this.rows.size(); i++) {
            this.rows.get(((Number) (i)).intValue())._fetchInstances();
        }
    }
    public static class Row extends KaitaiStruct.ReadOnly {
        public Map<String, Integer> _attrStart = new HashMap<String, Integer>();
        public Map<String, Integer> _attrEnd = new HashMap<String, Integer>();
        public Map<String, List<Integer>> _arrStart = new HashMap<String, List<Integer>>();
        public Map<String, List<Integer>> _arrEnd = new HashMap<String, List<Integer>>();

        public static Row fromFile(String fileName) throws IOException {
            return new Row(new ByteBufferKaitaiStream(fileName));
        }
        public static String[] _seqFields = new String[] { "x", "y", "label" };

        public Row(KaitaiStream _io) {
            this(_io, null, null);
        }

        public Row(KaitaiStream _io, CsvPoints _parent) {
            this(_io, _parent, null);
        }

        public Row(KaitaiStream _io, CsvPoints _parent, CsvPoints _root) {
            super(_io);
            this._parent = _parent;
            this._root = _root;
        }
        public void _read() {
            _attrStart.put("x", this._io.pos());
            this.x = new String(this._io.readBytesTerm((byte) 44, false, true, true), StandardCharsets.US_ASCII);
            _attrEnd.put("x", this._io.pos());
            _attrStart.put("y", this._io.pos());
            this.y = new String(this._io.readBytesTerm((byte) 44, false, true, true), StandardCharsets.US_ASCII);
            _attrEnd.put("y", this._io.pos());
            _attrStart.put("label", this._io.pos());
            this.label = new String(this._io.readBytesTerm((byte) 10, false, true, false), StandardCharsets.US_ASCII);
            _attrEnd.put("label", this._io.pos());
        }

        public void _fetchInstances() {
        }
        private String x;
        private String y;
        private String label;
        private CsvPoints _root;
        private CsvPoints _parent;
        public String x() { return x; }
        public String y() { return y; }
        public String label() { return label; }
        public CsvPoints _root() { return _root; }
        public CsvPoints _parent() { return _parent; }
    }
    private List<Row> rows;
    private CsvPoints _root;
    private KaitaiStruct.ReadOnly _parent;
    public List<Row> rows() { return rows; }
    public CsvPoints _root() { return _root; }
    public KaitaiStruct.ReadOnly _parent() { return _parent; }
}
