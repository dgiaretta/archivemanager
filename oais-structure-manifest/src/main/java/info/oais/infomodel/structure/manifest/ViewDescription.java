package info.oais.infomodel.structure.manifest;

import java.net.URI;

/**
 * A view specification: how to view the decoded data as a table, time series
 * or vector (see oais-structure-api's {@code TableViewSpecificationReader}
 * and its siblings for the file formats).
 *
 * @param iri      its IRI in the manifest
 * @param kind     {@link #TABLE}, {@link #TIME_SERIES} or {@link #VECTOR}
 * @param location the view specification file
 */
public record ViewDescription(String iri, String kind, URI location) {

	public static final String TABLE = "table";
	public static final String TIME_SERIES = "timeSeries";
	public static final String VECTOR = "vector";
}
