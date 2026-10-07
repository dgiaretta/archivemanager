package info.oais.infomodel.structure.east;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * {@link EastInterpreter} reading data with EAST descriptions, including
 * what the element tree can't express: markers, bits stored least
 * significant first, integers in subfields, sign conventions, the real
 * conventions of CCSDS 646.0-G-1, ASCII numbers and EAST's operators.
 */
class EastInterpreterTest {

	private static List<String> decode(String east, byte[] data) {
		return lines(new EastInterpreter(east).decode(data));
	}

	/** One line per value: its path (with indexes in arrays) and value, and its meaning if it has one. */
	static List<String> lines(StructureNode root) {
		List<String> out = new ArrayList<>();
		lines(root, root.getName(), out);
		return out;
	}

	private static void lines(StructureNode n, String path, List<String> out) {
		if (n.getKind() == StructureNodeKind.LEAF) {
			Object meaning = n.getAttributes().get("meaning");
			out.add(path + " = " + n.getValue().orElse(null) + (meaning == null ? "" : " (" + meaning + ")"));
			return;
		}
		List<StructureNode> children = n.getChildren();
		for (int i = 0; i < children.size(); i++) {
			StructureNode c = children.get(i);
			boolean indexed = n.getKind() == StructureNodeKind.ARRAY || c.getName().equals("set");
			lines(c, indexed ? path + "[" + i + "]" : path + "." + c.getName(), out);
		}
	}

	@Test
	void readsRecordsPlacedByTheirRepresentationClauses() {
		String east = """
				package RECORDS is
				   type DAY is (MON, TUE, WED, THU, FRI, SAT, SUN);
				   for DAY'size use 8;
				   type SMALL is range 1 .. 12;
				   for SMALL'size use 8;
				   type YEAR is range 1900 .. 2100;
				   for YEAR'size use 16;
				   type VALUE is digits 5;
				   for VALUE'size use 32;
				   type FOURTH_RECORD (THE_DAY_OF_MONTH: DAY := MON) is record
				      THE_MONTH: SMALL;
				      THE_YEAR: YEAR;
				      case THE_DAY_OF_MONTH is
				         when MON => THE_MEASUREMENT: VALUE;
				         when TUE .. THU | SAT =>
				            THE_ALPHA_VALUE: SMALL;
				            THE_BETA_VALUE: SMALL;
				         when others => null;
				      end case;
				   end record;
				   for FOURTH_RECORD use record
				      THE_DAY_OF_MONTH at 0 range 0 .. 7;
				      THE_MONTH at 0 range 8 .. 15;
				      THE_MEASUREMENT at 0 range 16 .. 47;
				      THE_ALPHA_VALUE at 0 range 16 .. 23;
				      THE_BETA_VALUE at 0 * WORD_16_BITS range 24 .. 31;
				      THE_YEAR at 1 * WORD_32_BITS range 16 .. 31;
				   end record;
				   for FOURTH_RECORD'size use 64;
				   FOURTH : FOURTH_RECORD;
				end RECORDS;
				package P is
				end P;
				""";
		ByteBuffer b = ByteBuffer.allocate(24);
		b.put((byte) 0).put((byte) 7).putFloat(3.25f).putShort((short) 2000);
		b.put((byte) 2).put((byte) 8).put((byte) 3).put((byte) 4).putShort((short) 0).putShort((short) 1999);
		b.put((byte) 6).put((byte) 9).putInt(0).putShort((short) 1998);
		assertEquals(List.of("records[0].fourth.the_day_of_month = 0 (MON)", "records[0].fourth.the_month = 7",
				"records[0].fourth.the_year = 2000", "records[0].fourth.the_measurement = 3.25",
				"records[1].fourth.the_day_of_month = 2 (WED)", "records[1].fourth.the_month = 8",
				"records[1].fourth.the_year = 1999", "records[1].fourth.the_alpha_value = 3",
				"records[1].fourth.the_beta_value = 4",
				"records[2].fourth.the_day_of_month = 6 (SUN)", "records[2].fourth.the_month = 9",
				"records[2].fourth.the_year = 1998"), decode(east, b.array()));
	}

	@Test
	void repeatsUntilAMarker() {
		String east = """
				package ADDRESSES is
				   type COEFFICIENT is digits 6;
				   for COEFFICIENT'size use 32;
				   type CLIENT_ADDRESS is record
				      ONE_CHARACTER : CHARACTER;
				      END_OF_ADDRESS : constant CHARACTER := ASCII.CR;
				   end record;
				   type CLIENT is record
				      NAME : STRING (1 .. 3);
				      ADDRESS : CLIENT_ADDRESS;
				   end record;
				   VALUE : COEFFICIENT;
				   END_OF_COEFFICIENTS : constant STRING := "END";
				   CUSTOMER : CLIENT;
				   END_OF_CUSTOMERS : constant EOF;
				end ADDRESSES;
				package P is
				end P;
				""";
		ByteBuffer b = ByteBuffer.allocate(4 * 2 + 3 + 3 + 3 + 1 + 3 + 2 + 1);
		b.putFloat(0.5f).putFloat(0.25f).put("END".getBytes(StandardCharsets.US_ASCII));
		b.put("Ann".getBytes(StandardCharsets.US_ASCII)).put("Rue".getBytes(StandardCharsets.US_ASCII)).put((byte) 13);
		b.put("Bob".getBytes(StandardCharsets.US_ASCII)).put("Ox".getBytes(StandardCharsets.US_ASCII)).put((byte) 13);
		assertEquals(List.of("addresses.value[0] = 0.5", "addresses.value[1] = 0.25",
				"addresses.customer[0].name = Ann", "addresses.customer[0].address.one_character[0] = R",
				"addresses.customer[0].address.one_character[1] = u",
				"addresses.customer[0].address.one_character[2] = e", "addresses.customer[1].name = Bob",
				"addresses.customer[1].address.one_character[0] = O",
				"addresses.customer[1].address.one_character[1] = x"), decode(east, b.array()));
	}

	/** Example 3-32: fields of 2, 3, 16 and 3 bits, stored by a little-endian machine. */
	@Test
	void readsBitsStoredLeastSignificantFirst() {
		String east = """
				package LITTLE is
				   type A is range 0 .. 3;
				   for A'size use 2;
				   type B is range 0 .. 7;
				   for B'size use 3;
				   type C is range 0 .. 65535;
				   for C'size use 16;
				   type D is range -4 .. 3;
				   for D'size use 3;
				   type FIELDS is record
				      FIRST : A;
				      SECOND : B;
				      THIRD : C;
				      FOURTH : D;
				   end record;
				   X : FIELDS;
				end LITTLE;
				package P is
				   type BIT_ORDER is (HIGH_ORDER_FIRST, LOW_ORDER_FIRST);
				   OCTET_STORAGE : constant BIT_ORDER := LOW_ORDER_FIRST;
				end P;
				""";
		// Packed least significant bit first: A=1, B=5, C=0xABCD, D=-2 (110).
		long packed = 1L | (5L << 2) | (0xABCDL << 5) | (6L << 21);
		byte[] data = {(byte) packed, (byte) (packed >> 8), (byte) (packed >> 16)};
		assertEquals(List.of("little[0].x.first = 1", "little[0].x.second = 5", "little[0].x.third = 43981",
				"little[0].x.fourth = -2"), decode(east, data));
	}

	/** Example 3-34's 10-bit integer, its bits in three subfields; and the other sign conventions. */
	@Test
	void readsIntegersInSubfieldsAndEachSignConvention() {
		String east = """
				package INTEGERS is
				   type SCRAMBLED is range 0 .. 1023;
				   for SCRAMBLED'size use 10;
				   type PAD is range 0 .. 63;
				   for PAD'size use 6;
				   type ONES is range -32767 .. 32767;
				   for ONES'size use 16;
				   type MAGNITUDE is range -32767 .. 32767;
				   for MAGNITUDE'size use 16;
				   type SWAPPED is range 0 .. 65535;
				   for SWAPPED'size use 16;
				   type R is record
				      S : SCRAMBLED;
				      FILL : PAD;
				      O : ONES;
				      M : MAGNITUDE;
				      W : SWAPPED;
				   end record;
				   X : R;
				end INTEGERS;
				package P is
				   Scrambled_Rep : constant INTEGER_PHYSICAL_DESCRIPTION :=
				      (NUMBER_OF_SUBFIELDS => 3, COMPLEMENT => UNSIGNED, LOCATION => (1 => (5,7), 2 => (0,4), 3 => (8,9)));
				   Ones_Rep : constant INTEGER_PHYSICAL_DESCRIPTION :=
				      (NUMBER_OF_SUBFIELDS => 1, COMPLEMENT => ONES_COMPLEMENT, LOCATION => (1 => (0,15)));
				   Magnitude_Rep : constant INTEGER_PHYSICAL_DESCRIPTION :=
				      (1, SIGN_AND_MAGNITUDE, (1 => (0,15)));
				   Swapped_Rep : constant INTEGER_PHYSICAL_DESCRIPTION :=
				      (NUMBER_OF_SUBFIELDS => 2, COMPLEMENT => UNSIGNED, LOCATION => (1 => (8,15), 2 => (0,7)));
				   type BASIC_TYPE_NAMES is (USER_TYPE_SCRAMBLED, USER_TYPE_ONES, USER_TYPE_MAGNITUDE, USER_TYPE_SWAPPED);
				   type RELATION(choice: BASIC_TYPE_NAMES) is record
				      case choice is
				         when USER_TYPE_SCRAMBLED => P1 : INTEGER_PHYSICAL_DESCRIPTION := Scrambled_Rep;
				         when USER_TYPE_ONES => P2 : INTEGER_PHYSICAL_DESCRIPTION := Ones_Rep;
				         when USER_TYPE_MAGNITUDE => P3 : INTEGER_PHYSICAL_DESCRIPTION := Magnitude_Rep;
				         when USER_TYPE_SWAPPED => P4 : INTEGER_PHYSICAL_DESCRIPTION := Swapped_Rep;
				      end case;
				   end record;
				end P;
				""";
		// 0b1011001110 = 718: bits 9..0. Significance of bit numbers 0..9: 2^6 2^5 2^4 2^3 2^2 2^9 2^8 2^7 2^1 2^0.
		int value = 718;
		int[] significance = {6, 5, 4, 3, 2, 9, 8, 7, 1, 0};
		int stored = 0;
		for (int bit = 0; bit < 10; bit++) {
			if ((value >> significance[bit] & 1) == 1) {
				stored |= 1 << (15 - bit);
			}
		}
		ByteBuffer b = ByteBuffer.allocate(8);
		b.putShort((short) stored).putShort((short) ~5).putShort((short) (0x8000 | 300)).putShort((short) 0x3412);
		assertEquals(List.of("integers[0].x.s = 718", "integers[0].x.fill = 0", "integers[0].x.o = -5",
				"integers[0].x.m = -300", "integers[0].x.w = 4660"), decode(east, b.array()));
	}

	/** 1.0 (or -1.0) in each convention of CCSDS 646.0-G-1, encoded by hand from its algorithm. */
	@Test
	void readsRealsInEveryConvention() {
		String east = """
				package REALS is
				   type IEEE_BE is digits 6;
				   for IEEE_BE'size use 32;
				   type IEEE_LE is digits 6;
				   for IEEE_LE'size use 32;
				   type VAX_F is digits 6;
				   for VAX_F'size use 32;
				   type MIL_1750A is digits 6;
				   for MIL_1750A'size use 32;
				   type CDC_VE is digits 6;
				   for CDC_VE'size use 64;
				   type CDC_BE is digits 6;
				   for CDC_BE'size use 60;
				   type FILLER is range 0 .. 255;
				   for FILLER'size use 8;
				   type IBM is digits 6;
				   for IBM'size use 32;
				   type R is record
				      A : IEEE_BE;
				      B : IEEE_LE;
				      C : VAX_F;
				      D : MIL_1750A;
				      E : MIL_1750A;
				      F : CDC_VE;
				      G : CDC_BE;
				      H : CDC_BE;
				      FILL : FILLER;
				      I : IBM;
				   end record;
				   X : R;
				end REALS;
				package P is
				   Ieee_Be_Rep : constant REAL_PHYSICAL_DESCRIPTION := (1, 1, FCSTC000, 0, SIGN_AND_MAGNITUDE, 2, 127,
				      (1 => (1,8)), (1 => (9,31)));
				   Ieee_Le_Rep : constant REAL_PHYSICAL_DESCRIPTION := (2, 3, FCSTC000, 24, SIGN_AND_MAGNITUDE, 2, 127,
				      (1 => (25,31), 2 => (16,16)), (1 => (17,23), 2 => (8,15), 3 => (0,7)));
				   Vax_Rep : constant REAL_PHYSICAL_DESCRIPTION := (NUMBER_OF_SUBFIELDS_IN_EXPONENT => 2,
				      NUMBER_OF_SUBFIELDS_IN_MANTISSA => 3, CONVENTION_USED => FCSTC001, SIGN_BIT_NUMBER => 8,
				      COMPLEMENT => SIGN_AND_MAGNITUDE, EXPONENT_BASE => 2, BIAS => 128,
				      LOCATION_OF_EXPONENT => (1 => (9,15), 2 => (0,0)),
				      LOCATION_OF_MANTISSA => (1 => (1,7), 2 => (24, 31), 3 => (16,23)));
				   Mil_Rep : constant REAL_PHYSICAL_DESCRIPTION := (1, 1, FCSTC002, 0, TWOS_COMPLEMENT, 2, 0,
				      (1 => (24,31)), (1 => (0,23)));
				   Cdc_Ve_Rep : constant REAL_PHYSICAL_DESCRIPTION := (1, 1, FCSTC003, 0, SIGN_AND_MAGNITUDE, 2, 16384,
				      (1 => (1,15)), (1 => (16,63)));
				   Cdc_Be_Rep : constant REAL_PHYSICAL_DESCRIPTION := (1, 1, FCSTC004, 0, SIGN_AND_MAGNITUDE, 2, 1024,
				      (1 => (1,11)), (1 => (12,59)));
				   Ibm_Rep : constant REAL_PHYSICAL_DESCRIPTION := (1, 1, FCSTC005, 0, SIGN_AND_MAGNITUDE, 16, 64,
				      (1 => (1,7)), (1 => (8,31)));
				   type BASIC_TYPE_NAMES is (USER_TYPE_IEEE_BE, USER_TYPE_IEEE_LE, USER_TYPE_VAX_F, USER_TYPE_MIL_1750A,
				      USER_TYPE_CDC_VE, USER_TYPE_CDC_BE, USER_TYPE_IBM);
				   type RELATION(choice: BASIC_TYPE_NAMES) is record
				      case choice is
				         when USER_TYPE_IEEE_BE => P1 : REAL_PHYSICAL_DESCRIPTION := Ieee_Be_Rep;
				         when USER_TYPE_IEEE_LE => P2 : REAL_PHYSICAL_DESCRIPTION := Ieee_Le_Rep;
				         when USER_TYPE_VAX_F => P3 : REAL_PHYSICAL_DESCRIPTION := Vax_Rep;
				         when USER_TYPE_MIL_1750A => P4 : REAL_PHYSICAL_DESCRIPTION := Mil_Rep;
				         when USER_TYPE_CDC_VE => P5 : REAL_PHYSICAL_DESCRIPTION := Cdc_Ve_Rep;
				         when USER_TYPE_CDC_BE => P6 : REAL_PHYSICAL_DESCRIPTION := Cdc_Be_Rep;
				         when USER_TYPE_IBM => P7 : REAL_PHYSICAL_DESCRIPTION := Ibm_Rep;
				      end case;
				   end record;
				end P;
				""";
		ByteBuffer b = ByteBuffer.allocate(4 + 4 + 4 + 4 + 4 + 8 + 16 + 4);
		b.putFloat(21.5f);
		b.put(new byte[] {0, 0, (byte) 0xAC, 0x41}); // 21.5 little-endian
		b.put(new byte[] {(byte) 0x80, 0x40, 0, 0}); // VAX F 1.0: E=129, words swapped
		b.put(new byte[] {0x40, 0, 0, 1}); // 1750A 1.0 = 0.5 * 2^1
		b.put(new byte[] {(byte) 0x80, 0, 0, 0}); // 1750A -1.0 = -1 * 2^0
		b.put(new byte[] {0x40, 0x01, (byte) 0x80, 0, 0, 0, 0, 0}); // NOS-VE 1.0 = 0.5 * 2^(16385-16384)
		// NOS-BE: 1.0 (E=1024, M=1); -1.0 its ones' complement; then an 8-bit fill: 16 bytes.
		long one = (1024L << 48) | 1L;
		long minusOne = ~one & ((1L << 60) - 1);
		java.math.BigInteger both = java.math.BigInteger.valueOf(one).shiftLeft(68)
				.or(java.math.BigInteger.valueOf(minusOne).shiftLeft(8));
		byte[] cdc = both.toByteArray();
		byte[] sixteen = new byte[16];
		System.arraycopy(cdc, Math.max(0, cdc.length - 16), sixteen, Math.max(0, 16 - cdc.length), Math.min(16, cdc.length));
		b.put(sixteen);
		b.put(new byte[] {0x41, 0x10, 0, 0}); // IBM 1.0 = 1/16 * 16^(65-64)
		assertEquals(List.of("reals[0].x.a = 21.5", "reals[0].x.b = 21.5", "reals[0].x.c = 1.0", "reals[0].x.d = 1.0",
				"reals[0].x.e = -1.0", "reals[0].x.f = 1.0", "reals[0].x.g = 1.0", "reals[0].x.h = -1.0",
				"reals[0].x.fill = 0", "reals[0].x.i = 1.0"), decode(east, b.array()));
	}

	@Test
	void computesDiscriminantsWithEastOperatorsAndReadsAsciiNumbers() {
		String east = """
				package COMPUTED is
				   type N is range 0 .. 8;
				   for N'size use 8;
				   type OCTET is range 0 .. 255;
				   for OCTET'size use 8;
				   type OCTETS is array (OCTET range <>) of OCTET;
				   type LEVEL is range -9999 .. 99999;
				   for LEVEL'size use 40;
				   type KM is digits 5;
				   for KM'size use 48;
				   type STATE is (WORKING, IDLE);
				   for STATE'size use 56;
				   type BLOCK (VIRTUAL_SIZE : OCTET := 1; VIRTUAL_ODD : BOOLEAN := TRUE) is record
				      VALUES : OCTETS (1 .. VIRTUAL_SIZE);
				      case VIRTUAL_ODD is
				         when TRUE => EXTRA : OCTET;
				         when FALSE => null;
				      end case;
				   end record;
				   COUNT : N;
				   DATA : BLOCK;
				   HEIGHT : LEVEL;
				   DISTANCE : KM;
				   PROCESS : STATE;
				   DATA.VIRTUAL_SIZE : virtual OCTET := 2 ** COUNT - 1;
				   DATA.VIRTUAL_ODD : virtual BOOLEAN := is_odd(COUNT) and cos(0) = 1;
				end COMPUTED;
				package P is
				   Ascii_Number : constant ASCII_NUMERIC_PHYSICAL_DESCRIPTION := (NUMBER_OF_CHARACTERS => 5);
				   Ascii_Real : constant ASCII_NUMERIC_PHYSICAL_DESCRIPTION := (NUMBER_OF_CHARACTERS => 6);
				   Ascii_State : constant ASCII_ENUMERATION_PHYSICAL_DESCRIPTION :=
				      (NUMBER_OF_OCCURRENCES => 2, NUMBER_OF_CHARACTERS => 7, REPRESENTATION => ("WORKING", "IDLE"));
				   type BASIC_TYPE_NAMES is (USER_TYPE_LEVEL, USER_TYPE_KM, USER_TYPE_STATE);
				   type RELATION(choice: BASIC_TYPE_NAMES) is record
				      case choice is
				         when USER_TYPE_LEVEL => P1 : ASCII_NUMERIC_PHYSICAL_DESCRIPTION := Ascii_Number;
				         when USER_TYPE_KM => P2 : ASCII_NUMERIC_PHYSICAL_DESCRIPTION := Ascii_Real;
				         when USER_TYPE_STATE => P3 : ASCII_ENUMERATION_PHYSICAL_DESCRIPTION := Ascii_State;
				      end case;
				   end record;
				end P;
				""";
		ByteBuffer b = ByteBuffer.allocate(1 + 3 + 5 + 6 + 7);
		b.put((byte) 2).put(new byte[] {7, 8, 9}).put(" -123".getBytes(StandardCharsets.US_ASCII))
				.put("1.5D+2".getBytes(StandardCharsets.US_ASCII)).put("IDLE   ".getBytes(StandardCharsets.US_ASCII));
		assertEquals(List.of("computed[0].count = 2", "computed[0].data.values[0] = 7", "computed[0].data.values[1] = 8",
				"computed[0].data.values[2] = 9", "computed[0].height = -123", "computed[0].distance = 150.0",
				"computed[0].process = IDLE    (IDLE)"), decode(east, b.array()));
		ByteBuffer odd1 = ByteBuffer.allocate(1 + 1 + 1 + 5 + 6 + 7);
		odd1.put((byte) 1).put((byte) 7).put((byte) 5).put(" -123".getBytes(StandardCharsets.US_ASCII))
				.put("1.5D+2".getBytes(StandardCharsets.US_ASCII)).put("IDLE   ".getBytes(StandardCharsets.US_ASCII));
		List<String> odd = decode(east, odd1.array());
		assertTrue(odd.contains("computed[0].data.extra = 5"), odd.toString());
	}

	@Test
	void saysWhereTheDataEnds() {
		String east = "package L is\n type N is range 0 .. 65535;\n for N'size use 16;\n X : N;\n Y : N;\nend L;";
		StructureInterpretationException e = assertThrows(StructureInterpretationException.class,
				() -> decode(east, new byte[] {0, 1, 2}));
		assertTrue(e.getMessage().contains("in the middle of 'Y'"), e.getMessage());
	}
}
