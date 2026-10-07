package info.oais.infomodel.structure.description;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link EastReader} on the examples of the EAST specification (CCSDS
 * 644.0-B-3), with the size clauses some of them leave out added.
 */
class EastReaderTest {

	/** Example 3-11's packet format, with sizes, read big-endian. */
	static final String PACKET = """
			package PACKET_DESCRIPTION is
			   east_version : constant STRING := "3.0";
			   -- basic data types used in the first branch
			   type VERSION is (VERSION_1, VERSION_2);
			   for VERSION'size use 3;
			   type PACKET_TYPE is (TELEMETRY , TELECOMMAND);
			   for PACKET_TYPE'size use 1;
			   type PRESENCE_FLAG is (ABSENT , PRESENT);
			   for PRESENCE_FLAG'size use 1;
			   type PROCESS_IDENTIFICATION is range 0 .. 2047;
			   for PROCESS_IDENTIFICATION'size use 11;
			   type PACKET_IDENTIFICATION_TYPE is record
			      VERSION_NUMBER: VERSION;
			      TYPE_ID: PACKET_TYPE;
			      SECONDARY_HEADER_FLAG: PRESENCE_FLAG;
			      APPLICATION_PROCESS_ID: PROCESS_IDENTIFICATION;
			   end record;
			   for PACKET_IDENTIFICATION_TYPE'size use 16;
			   type STATUS is (CONTINUATION_SEGMENT, FIRST_SEGMENT, LAST_SEGMENT, UNSEGMENTED_PACKET);
			   for STATUS'size use 2;
			   type COUNTER is range 0 .. 16383;
			   for COUNTER'size use 14;
			   type PACKET_SEQUENCE_CONTROL_TYPE is record
			      SEGMENTATION_FLAG: STATUS;
			      SOURCE_SEQUENCE_COUNT: COUNTER;
			   end record;
			   type NUMBER is range 0 .. 65535;
			   for NUMBER'size use 16;
			   type OCTET is range 0 .. 255;
			   for OCTET'size use 8;
			   type DATA_ARRAY is array (NUMBER range <>) of OCTET;
			   type SECONDARY_HEADER_TYPE is array (1 .. 4) of OCTET;
			   type PRIMARY_HEADER_TYPE is record
			      PACKET_IDENTIFICATION: PACKET_IDENTIFICATION_TYPE;
			      PACKET_SEQUENCE_CONTROL: PACKET_SEQUENCE_CONTROL_TYPE;
			      SOURCE_DATA_LENGTH: NUMBER;
			   end record;
			   type PACKET_FORMAT_TYPE(
			      VIRTUAL_SECONDARY_HEADER_FLAG: PRESENCE_FLAG := PRESENT;
			      VIRTUAL_SOURCE_DATA_LENGTH: NUMBER := 256)
			   is record
			      PRIMARY_HEADER: PRIMARY_HEADER_TYPE;
			      case VIRTUAL_SECONDARY_HEADER_FLAG is
			         when ABSENT =>
			            SOURCE_DATA_0: DATA_ARRAY (1 .. VIRTUAL_SOURCE_DATA_LENGTH);
			         when PRESENT =>
			            SECONDARY_HEADER: SECONDARY_HEADER_TYPE;
			            SOURCE_DATA_1: DATA_ARRAY (1 .. VIRTUAL_SOURCE_DATA_LENGTH);
			      end case;
			   end record;
			   FLAG : PRESENCE_FLAG;
			   LENGTH : NUMBER;
			   PACKET : PACKET_FORMAT_TYPE;
			   -- Actual values of discriminants
			   PACKET.VIRTUAL_SECONDARY_HEADER_FLAG : virtual PRESENCE_FLAG := FLAG;
			   PACKET.VIRTUAL_SOURCE_DATA_LENGTH : virtual NUMBER := LENGTH;
			end PACKET_DESCRIPTION;

			package PACKET_PHYSICAL is
			   type BIT_ORDER is (HIGH_ORDER_FIRST, LOW_ORDER_FIRST);
			   OCTET_STORAGE: constant BIT_ORDER := HIGH_ORDER_FIRST;
			end PACKET_PHYSICAL;
			""";

	@Test
	void readsThePacketFormatOfExample3_11() {
		FormatDescription format = EastReader.read(PACKET);
		assertEquals(List.of(), DescriptionValidator.validate(format));
		assertEquals("PACKET_DESCRIPTION", format.name());
		assertEquals(ByteOrder.BIG_ENDIAN, format.defaultByteOrder());
		assertTrue(format.notes().contains("EAST version 3.0"), format.notes());
		assertEquals(List.of(
				"packet_description {}",
				"packet_description.set {} until end",
				"packet_description.set.flag : BITS(1) {0=ABSENT, 1=PRESENT}",
				"packet_description.set.length : BITS(16)",
				"packet_description.set.packet {}",
				"packet_description.set.packet.primary_header {}",
				"packet_description.set.packet.primary_header.packet_identification {}",
				"packet_description.set.packet.primary_header.packet_identification.version_number : BITS(3) {0=VERSION_1, 1=VERSION_2}",
				"packet_description.set.packet.primary_header.packet_identification.type_id : BITS(1) {0=TELEMETRY, 1=TELECOMMAND}",
				"packet_description.set.packet.primary_header.packet_identification.secondary_header_flag : BITS(1) {0=ABSENT, 1=PRESENT}",
				"packet_description.set.packet.primary_header.packet_identification.application_process_id : BITS(11)",
				"packet_description.set.packet.primary_header.packet_sequence_control {}",
				"packet_description.set.packet.primary_header.packet_sequence_control.segmentation_flag : BITS(2) {0=CONTINUATION_SEGMENT, 1=FIRST_SEGMENT, 2=LAST_SEGMENT, 3=UNSEGMENTED_PACKET}",
				"packet_description.set.packet.primary_header.packet_sequence_control.source_sequence_count : BITS(14)",
				"packet_description.set.packet.primary_header.source_data_length : BITS(16)",
				"packet_description.set.packet.case_secondary_header_flag choice on flag",
				"packet_description.set.packet.case_secondary_header_flag [0]",
				"packet_description.set.packet.case_secondary_header_flag.when_absent {}",
				"packet_description.set.packet.case_secondary_header_flag.when_absent.source_data_0 : BITS(8) x length",
				"packet_description.set.packet.case_secondary_header_flag [1]",
				"packet_description.set.packet.case_secondary_header_flag.when_present {}",
				"packet_description.set.packet.case_secondary_header_flag.when_present.secondary_header : BITS(8) x 4",
				"packet_description.set.packet.case_secondary_header_flag.when_present.source_data_1 : BITS(8) x length"), outline(format.root()));
	}

	/** Example 3-25's types, for the records of examples 3-26 to 3-29. */
	static final String RECORD_TYPES = """
			   type DAY is (MON, TUE, WED, THU, FRI, SAT, SUN);
			   for DAY'size use 8;
			   type MONTH is range 1 .. 12;
			   for MONTH'size use 8;
			   type YEAR is range 1900 .. 2100;
			   for YEAR'size use 16;
			   type NUMBER is range 1 .. 10;
			   for NUMBER'size use 8;
			   type ALPHA is range 1 .. 10;
			   for ALPHA'size use 8;
			   type BETA is range 1 .. 10;
			   for BETA'size use 8;
			   type GAMMA is range 1 .. 10;
			   for GAMMA'size use 8;
			   type DELTA is range 1 .. 10;
			   for DELTA'size use 8;
			   type VALUE is digits 5;
			   for VALUE'size use 32;
			   type VECTOR is array(NUMBER range <>) of VALUE;
			""";

	static String logical(String types, String variables) {
		return "package L is\n" + types + variables + "end L;\npackage P is\nend P;\n";
	}

	@Test
	void placesComponentsByTheirRecordRepresentationClause() {
		String text = logical(RECORD_TYPES + """
				   type FIRST_RECORD is record
				      THE_DAY_OF_MONTH: DAY;
				      THE_MONTH: MONTH;
				      THE_YEAR: YEAR;
				      THE_MEASUREMENT: VALUE;
				   end record;
				   for FIRST_RECORD use record
				      THE_YEAR at 0 range 16 .. 31;
				      THE_DAY_OF_MONTH at 0 range 0 .. 7;
				      THE_MONTH at 0 range 8 .. 15;
				      THE_MEASUREMENT at 1 * WORD_32_BITS range 0 .. 31;
				   end record;
				   for FIRST_RECORD'size use 64;
				   type SECOND_RECORD(THE_NUMBER: NUMBER := 1) is record
				      THE_YEAR: YEAR;
				      THE_MEASUREMENT: VECTOR(1 .. THE_NUMBER);
				      THE_MONTH: MONTH;
				      THE_DAY_OF_MONTH: DAY;
				   end record;
				   for SECOND_RECORD use record
				      THE_NUMBER at 0 range 0 .. 7;
				      THE_YEAR at 0 range 8 .. 23;
				   end record;
				""", """
				   FIRST : FIRST_RECORD;
				   SECOND : SECOND_RECORD;
				""");
		FormatDescription format = EastReader.read(text);
		assertEquals(List.of(), DescriptionValidator.validate(format));
		assertEquals(List.of(
				"l {}",
				"l.set {} until end",
				"l.set.first {}",
				"l.set.first.the_day_of_month : UINT8 {0=MON, 1=TUE, 2=WED, 3=THU, 4=FRI, 5=SAT, 6=SUN}",
				"l.set.first.the_month : UINT8",
				"l.set.first.the_year : UINT16",
				"l.set.first.the_measurement : FLOAT32",
				"l.set.second {}",
				"l.set.second.the_number : UINT8",
				"l.set.second.the_year : UINT16",
				"l.set.second.the_measurement : FLOAT32 x the_number",
				"l.set.second.the_month : UINT8",
				"l.set.second.the_day_of_month : UINT8 {0=MON, 1=TUE, 2=WED, 3=THU, 4=FRI, 5=SAT, 6=SUN}"), outline(format.root()));
	}

	@Test
	void overlappingAlternativesAndAFixedComponentAfterThem() {
		String text = logical(RECORD_TYPES + """
				   type THIRD_RECORD(THE_DAY_OF_MONTH: DAY := MON) is record
				      THE_MONTH: MONTH;
				      THE_YEAR: YEAR;
				      case THE_DAY_OF_MONTH is
				         when MON =>
				            THE_MEASUREMENT: VALUE;
				         when others =>
				            THE_ALPHA_VALUE: ALPHA;
				            THE_BETA_VALUE: BETA;
				            THE_GAMMA_VALUE: GAMMA;
				            THE_DELTA_VALUE: DELTA;
				      end case;
				   end record;
				   for THIRD_RECORD use record
				      THE_DAY_OF_MONTH at 0 range 0 .. 7;
				      THE_MONTH at 0 range 8 .. 15;
				      THE_YEAR at 0 range 16 .. 31;
				      THE_MEASUREMENT at 0 range 32 .. 63;
				      THE_ALPHA_VALUE at 0 range 32 .. 39;
				      THE_BETA_VALUE at 0 range 40 .. 47;
				      THE_GAMMA_VALUE at 0 range 48 .. 55;
				      THE_DELTA_VALUE at 0 range 56 .. 63;
				   end record;
				   for THIRD_RECORD'size use 64;
				   type FOURTH_RECORD (THE_DAY_OF_MONTH: DAY := MON) is record
				      THE_MONTH: MONTH;
				      THE_YEAR: YEAR;
				      case THE_DAY_OF_MONTH is
				         when MON =>
				            THE_MEASUREMENT: VALUE;
				         when TUE .. THU | SAT =>
				            THE_ALPHA_VALUE: ALPHA;
				            THE_BETA_VALUE: BETA;
				         when others =>
				            null;
				      end case;
				   end record;
				   for FOURTH_RECORD use record
				      THE_DAY_OF_MONTH at 0 range 0 .. 7;
				      THE_MONTH at 0 range 8 .. 15;
				      THE_MEASUREMENT at 0 range 16 .. 47;
				      THE_ALPHA_VALUE at 0 range 16 .. 23;
				      THE_BETA_VALUE at 0 range 24 .. 31;
				      THE_YEAR at 0 range 48 .. 63;
				   end record;
				   for FOURTH_RECORD'size use 64;
				""", """
				   THIRD : THIRD_RECORD;
				   FOURTH : FOURTH_RECORD;
				""");
		FormatDescription format = EastReader.read(text);
		assertEquals(List.of(), DescriptionValidator.validate(format));
		assertEquals(List.of(
				"l {}",
				"l.set {} until end",
				"l.set.third {}",
				"l.set.third.the_day_of_month : UINT8 {0=MON, 1=TUE, 2=WED, 3=THU, 4=FRI, 5=SAT, 6=SUN}",
				"l.set.third.the_month : UINT8",
				"l.set.third.the_year : UINT16",
				"l.set.third.when_mon {} if (the_day_of_month = 0)",
				"l.set.third.when_mon.the_measurement : FLOAT32",
				"l.set.third.when_others {} if not (the_day_of_month = 0)",
				"l.set.third.when_others.the_alpha_value : UINT8",
				"l.set.third.when_others.the_beta_value : UINT8",
				"l.set.third.when_others.the_gamma_value : UINT8",
				"l.set.third.when_others.the_delta_value : UINT8",
				"l.set.fourth {}",
				"l.set.fourth.the_day_of_month : UINT8 {0=MON, 1=TUE, 2=WED, 3=THU, 4=FRI, 5=SAT, 6=SUN}",
				"l.set.fourth.the_month : UINT8",
				"l.set.fourth.when_mon {} if (the_day_of_month = 0)",
				"l.set.fourth.when_mon.the_measurement : FLOAT32",
				"l.set.fourth.when_tue_to_thu_or_sat {} if (((the_day_of_month >= 1) and (the_day_of_month <= 3)) or (the_day_of_month = 5))",
				"l.set.fourth.when_tue_to_thu_or_sat.the_alpha_value : UINT8",
				"l.set.fourth.when_tue_to_thu_or_sat.the_beta_value : UINT8",
				"l.set.fourth.when_tue_to_thu_or_sat.spare_1 : BYTES(2)",
				"l.set.fourth.when_others {} if not (((the_day_of_month = 0) or ((the_day_of_month >= 1) and (the_day_of_month <= 3))) or (the_day_of_month = 5))",
				"l.set.fourth.when_others.spare_1 : BYTES(4)",
				"l.set.fourth.the_year : UINT16"), outline(format.root()));
	}

	@Test
	void aCalculatedPresenceConditionAndAnEofMarker() {
		String text = logical("""
				   type A_RESULT is range 0 .. 100;
				   for A_RESULT'size use 8;
				   type RESULTS (VIRTUAL_BONUS_FLAG : BOOLEAN := TRUE) is record
				      RESULT_1 : A_RESULT;
				      RESULT_2 : A_RESULT;
				      case VIRTUAL_BONUS_FLAG is
				         when TRUE => BONUS : A_RESULT;
				         when FALSE => null;
				      end case;
				   end record;
				""", """
				   PREVIOUS_WEEK : A_RESULT;
				   THIS_WEEK : RESULTS;
				   END_OF_WEEKS : constant EOF;
				   -- Actual values of discriminant
				   THIS_WEEK.VIRTUAL_BONUS_FLAG : virtual BOOLEAN
				      := (THIS_WEEK.RESULT_2 - THIS_WEEK.RESULT_1) > PREVIOUS_WEEK;
				""");
		FormatDescription format = EastReader.read(text);
		assertEquals(List.of(), DescriptionValidator.validate(format));
		assertEquals(List.of(
				"l {}",
				"l.previous_week : UINT8",
				"l.this_week {} until end",
				"l.this_week.result_1 : UINT8",
				"l.this_week.result_2 : UINT8",
				"l.this_week.when_true {} if ((result_2 - result_1) > previous_week)",
				"l.this_week.when_true.bonus : UINT8"), outline(format.root()));
	}

	@Test
	void codesAsciiRepresentationsAndLittleEndianReals() {
		String text = """
				package L is
				   type CODE is (ADD , SUB , MUL);
				   for CODE use (ADD => 2#1#, SUB => 2#10#, MUL => 16#0C#);
				   for CODE'size use 8;
				   type PROCESS_IDENTIFICATION is (WORKING, IDLE);
				   for PROCESS_IDENTIFICATION'size use 56;
				   type COUNTER is range -1 .. 16383;
				   for COUNTER'size use 40;
				   type KILOMETERS is digits 5;
				   for KILOMETERS'size use 32;
				   type LEVEL is range -32768 .. 32767;
				   for LEVEL'size use 16;
				   NAME : STRING (1 .. 8);
				   OPERATION : CODE;
				   PROCESS : PROCESS_IDENTIFICATION;
				   COUNT : COUNTER;
				   DISTANCE : KILOMETERS;
				   HEIGHT : LEVEL;
				end L;
				package P is
				   type BIT_ORDER is ( HIGH_ORDER_FIRST, LOW_ORDER_FIRST);
				   OCTET_STORAGE: constant BIT_ORDER := LOW_ORDER_FIRST;
				   Binary_Representation_01: constant INTEGER_PHYSICAL_DESCRIPTION :=
				      (NUMBER_OF_SUBFIELDS => 1, COMPLEMENT => TWOS_COMPLEMENT, LOCATION => (1 => (0,15)));
				   Binary_Representation_03: constant REAL_PHYSICAL_DESCRIPTION :=
				      (NUMBER_OF_SUBFIELDS_IN_EXPONENT => 2, NUMBER_OF_SUBFIELDS_IN_MANTISSA => 3,
				       CONVENTION_USED => FCSTC000, SIGN_BIT_NUMBER => 24, COMPLEMENT => SIGN_AND_MAGNITUDE,
				       EXPONENT_BASE => 2, BIAS => 127,
				       LOCATION_OF_EXPONENT => ( 1 => (25,31), 2 => (16,16)),
				       LOCATION_OF_MANTISSA => ( 1 => (17,23), 2 => (8,15), 3 => (0,7)));
				   ASCII_Rep_01: constant ASCII_ENUMERATION_PHYSICAL_DESCRIPTION :=
				      (NUMBER_OF_OCCURRENCES => 2, NUMBER_OF_CHARACTERS => 7, REPRESENTATION => ("WORKING" , "IDLE"));
				   ASCII_Rep_02: constant ASCII_NUMERIC_PHYSICAL_DESCRIPTION := (NUMBER_OF_CHARACTERS => 5);
				   type BASIC_TYPE_NAMES is (USER_TYPE_LEVEL, USER_TYPE_KILOMETERS, USER_TYPE_PROCESS_IDENTIFICATION,
				                             USER_TYPE_COUNTER);
				   type RELATION(choice: BASIC_TYPE_NAMES) is record
				      case choice is
				         when USER_TYPE_LEVEL => PHYS_1: INTEGER_PHYSICAL_DESCRIPTION := Binary_Representation_01;
				         when USER_TYPE_KILOMETERS => PHYS_2: REAL_PHYSICAL_DESCRIPTION := Binary_Representation_03;
				         when USER_TYPE_PROCESS_IDENTIFICATION =>
				            PHYS_3: ASCII_ENUMERATION_PHYSICAL_DESCRIPTION := ASCII_Rep_01;
				         when USER_TYPE_COUNTER => PHYS_4: ASCII_NUMERIC_PHYSICAL_DESCRIPTION := ASCII_Rep_02;
				      end case;
				   end record;
				end P;
				""";
		FormatDescription format = EastReader.read(text);
		assertEquals(List.of(), DescriptionValidator.validate(format));
		assertEquals(ByteOrder.LITTLE_ENDIAN, format.defaultByteOrder());
		assertEquals(List.of(
				"l {}",
				"l.set {} until end",
				"l.set.name : STRING(8)",
				"l.set.operation : UINT8 {1=ADD, 2=SUB, 12=MUL}",
				"l.set.process : STRING(7) {WORKING=WORKING, IDLE   =IDLE}",
				"l.set.count : STRING(5)",
				"l.set.distance : FLOAT32",
				"l.set.height : INT16"), outline(format.root()));
	}

	@Test
	void rangesBecomeValidMinimumsAndMaximums() {
		FormatDescription format = EastReader.read(logical("""
				   MAX : constant := 2**4 - 1;
				   type SMALL is range 0 .. MAX;
				   for SMALL'size use 8;
				   subtype SMALLER is SMALL range 1 .. 5;
				""", """
				   A : SMALL;
				   B : SMALLER;
				"""));
		FieldDescription a = (FieldDescription) child(format, "a");
		FieldDescription b = (FieldDescription) child(format, "b");
		assertEquals(new BigDecimal(0), a.semantics().validMin());
		assertEquals(new BigDecimal(15), a.semantics().validMax());
		assertEquals(new BigDecimal(1), b.semantics().validMin());
		assertEquals(new BigDecimal(5), b.semantics().validMax());
		assertEquals(Map.of(), a.semantics().codes());
	}

	@Test
	void multiDimensionalArraysVaryTheirFirstIndexFastest() {
		FormatDescription format = EastReader.read(logical("""
				   type N is range 0 .. 255;
				   for N'size use 8;
				   type MATRIX is array (N range <>, N range <>) of N;
				   type LINE is array (1 .. 3) of CHARACTER;
				""", """
				   M : MATRIX (1 .. 2, 1 .. 3);
				   L : LINE;
				"""));
		assertEquals(List.of("l {}", "l.set {} until end", "l.set.m {} x 3", "l.set.m.element : UINT8 x 2",
				"l.set.l : STRING(3)"), outline(format.root()));
	}

	/** Example 3-12: an array's size calculated from fields inside records read earlier. */
	@Test
	void pathsIntoRecordsReadEarlierBecomeDottedReferences() {
		FormatDescription format = EastReader.read(logical("""
				   type A_DAY is range 1 .. 1000;
				   for A_DAY'size use 16;
				   type A_DATE is record
				      DAY : A_DAY;
				   end record;
				   type T is digits 6;
				   for T'size use 32;
				   type TS is array (A_DAY range <>) of T;
				   type DATA_RECORD (VIRTUAL_SIZE : A_DAY := 1) is record
				      MEASUREMENTS : TS (1 .. VIRTUAL_SIZE);
				   end record;
				""", """
				   FIRST_DATE : A_DATE;
				   LAST_DATE : A_DATE;
				   DATA : DATA_RECORD;
				   DATA.VIRTUAL_SIZE : virtual A_DAY := LAST_DATE.DAY - FIRST_DATE.DAY;
				"""));
		assertEquals(List.of(), DescriptionValidator.validate(format));
		assertEquals(List.of("l {}", "l.set {} until end", "l.set.first_date {}", "l.set.first_date.day : UINT16",
				"l.set.last_date {}", "l.set.last_date.day : UINT16", "l.set.data {}",
				"l.set.data.measurements : FLOAT32 x (last_date.day - first_date.day)"), outline(format.root()));
	}

	/** A packet as CCSDS packets are usually described: its own header says whether there's a secondary header. */
	@Test
	void pathsIntoAnEarlierComponentOfTheSameRecord() {
		FormatDescription format = EastReader.read(logical("""
				   type FLAG is (ABSENT, PRESENT);
				   for FLAG'size use 8;
				   type LENGTH is range 0 .. 65535;
				   for LENGTH'size use 16;
				   type OCTET is range 0 .. 255;
				   for OCTET'size use 8;
				   type OCTETS is array (LENGTH range <>) of OCTET;
				   type ID is record
				      SECONDARY_HEADER_FLAG : FLAG;
				   end record;
				   type HEADER is record
				      IDENTIFICATION : ID;
				      DATA_LENGTH : LENGTH;
				   end record;
				   type PACKET_TYPE (VIRTUAL_FLAG : FLAG := PRESENT; VIRTUAL_LENGTH : LENGTH := 1) is record
				      PRIMARY_HEADER : HEADER;
				      case VIRTUAL_FLAG is
				         when PRESENT =>
				            SECONDARY_HEADER : OCTETS (1 .. 4);
				            DATA_1 : OCTETS (1 .. VIRTUAL_LENGTH);
				         when ABSENT =>
				            DATA_0 : OCTETS (1 .. VIRTUAL_LENGTH);
				      end case;
				   end record;
				""", """
				   PACKET : PACKET_TYPE;
				   PACKET.VIRTUAL_FLAG : virtual FLAG := PACKET.PRIMARY_HEADER.IDENTIFICATION.SECONDARY_HEADER_FLAG;
				   PACKET.VIRTUAL_LENGTH : virtual LENGTH := PACKET.PRIMARY_HEADER.DATA_LENGTH + 1;
				"""));
		assertEquals(List.of(), DescriptionValidator.validate(format));
		List<String> outline = outline(format.root());
		assertTrue(outline.contains("l.set.packet.case_flag choice on primary_header.identification.secondary_header_flag"),
				outline.toString());
		assertTrue(outline.contains("l.set.packet.case_flag.when_present.data_1 : UINT8 x (primary_header.data_length + 1)"),
				outline.toString());
	}

	@Test
	void refusesWhatTheModelCantExpressSayingWhere() {
		assertRefused(logical("""
				   type C is digits 10;
				   for C'size use 32;
				""", """
				   VALUE : C;
				   END_OF_COEFFICIENTS : constant STRING := "END";
				"""), 5, "Markers other than EOF");
		assertRefused(logical("""
				   type N is range 0 .. 10;
				""", """
				   X : N;
				"""), 2, "N'size use");
		assertRefused(logical("""
				   type N is range -8 .. 7;
				   for N'size use 4;
				""", """
				   X : N;
				"""), 4, "signed bit fields");
		assertRefused("""
				package L is
				   type R is digits 6;
				   for R'size use 32;
				   X : R;
				end L;
				package P is
				   Rep: constant REAL_PHYSICAL_DESCRIPTION := (CONVENTION_USED => FCSTC001);
				   type BASIC_TYPE_NAMES is (USER_TYPE_R);
				   type RELATION(choice: BASIC_TYPE_NAMES) is record
				      case choice is
				         when USER_TYPE_R => PHYS_R: REAL_PHYSICAL_DESCRIPTION := Rep;
				      end case;
				   end record;
				end P;
				""", 7, "IEEE 754");
		assertRefused(logical("type N is range 0 .. 10;\nfor N'size use 8;\n", "X : N;\nY := 1;\n"), 5,
				"Expected ':'");
	}

	private static void assertRefused(String text, int line, String why) {
		EastReader.EastException e = assertThrows(EastReader.EastException.class, () -> EastReader.read(text));
		assertTrue(e.getMessage().contains(why), e.getMessage());
		assertEquals(line, e.line(), e.getMessage());
	}

	private static ElementDescription child(FormatDescription format, String name) {
		RecordDescription set = (RecordDescription) format.root().children().get(0);
		return set.children().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
	}

	/** One line per element: its path, what it is, and how often it occurs. */
	static List<String> outline(ElementDescription root) {
		List<String> lines = new ArrayList<>();
		outline(root, "", lines);
		return lines;
	}

	private static void outline(ElementDescription e, String parent, List<String> lines) {
		String path = parent.isEmpty() ? e.name() : parent + "." + e.name();
		String occurrence = e.occurrence() instanceof Occurrence.Repeated r ? " x " + r.count().text()
				: e.occurrence() instanceof Occurrence.Optional o ? " if " + o.condition().text()
						: e.occurrence() instanceof Occurrence.UntilEnd ? " until end" : "";
		if (e instanceof FieldDescription f) {
			String what = f.type() + (f.length() != null ? "(" + f.length().text() + ")" : "");
			String codes = f.semantics().codes().isEmpty() ? "" : " " + f.semantics().codes();
			lines.add(path + " : " + what + occurrence + codes);
		} else if (e instanceof RecordDescription r) {
			lines.add(path + " {}" + occurrence);
			r.children().forEach(c -> outline(c, path, lines));
		} else if (e instanceof ChoiceDescription c) {
			lines.add(path + " choice on " + c.discriminator().text() + occurrence);
			for (ChoiceDescription.Branch b : c.branches()) {
				lines.add(path + " [" + b.key() + "]");
				outline(b.record(), path, lines);
			}
		}
	}
}
