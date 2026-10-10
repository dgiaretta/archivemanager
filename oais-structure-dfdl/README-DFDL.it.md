# Note su oais-structure-dfdl

Questo modulo collega **DFDL** - il Data Format Description Language dell'Open
Grid Forum (GFD.207) - al modello comune `StructureNode`, usando **Apache
Daffodil 3.11.0** (`daffodil-japi_2.13`, Apache License 2.0, da Maven
Central), l'implementazione DFDL di riferimento.

## Come si descrive un formato

Uno schema DFDL è un normale XML Schema i cui elementi portano annotazioni
`dfdl:` che dicono come ciascuno è disposto nei dati: `dfdl:length` e
`dfdl:lengthKind`, `dfdl:byteOrder`, `dfdl:representation` (`binary`/`text`),
`dfdl:encoding`, `dfdl:separator`/`dfdl:terminator` per il testo delimitato,
`dfdl:occursCount` per la ripetizione, ed espressioni come
`dfdl:length="{ xs:int(../tns:labelLen) }"` che prendono la lunghezza di un
campo da un altro. Si possono descrivere record binari, testo a larghezza fissa
e testo delimitato (per es. CSV). Vedete `src/test/resources` per un record
binario "point" e un esempio CSV, e `oais-structure-demo` per altri; anche gli
Strumenti RepInfo di archive-manager generano schemi DFDL dalla loro
descrizione indipendente dal motore (conteggi come `dfdl:occursCount`,
condizioni e scelte come `dfdl:choiceDispatchKey`/`dfdl:choiceBranchKey` ed
espressioni di occorrenza, record CSV con `dfdl:separator`/`dfdl:terminator`;
e, quando DFDL è tra i linguaggi scelti, campi di bit con
`dfdl:lengthUnits="bits"`, record di lunghezza esplicita, valori nulli con
`nillable`/`dfdl:nilValue`, valori CSV tra virgolette con un blocco di escape
`dfdl:defineEscapeScheme`, e formati numerici `dfdl:textNumberPattern`).

`new DfdlFormatSpecification(schemaUri)` indica lo schema all'adattatore; viene
analizzato l'elemento radice predefinito dello schema.

## A che cosa serve DFDL

Gli esempi di questo progetto sono volutamente piccoli (un record binario
"point" e una tabella CSV, mantenuti identici nei moduli DFDL, Kaitai e DRB
così che i risultati si possano confrontare). DFDL in sé si usa per molto di
più. È nato per i formati di *record* testuali e binari precedenti a XML e
JSON, per esempio:

- messaggistica finanziaria: SWIFT MT, ISO 20022, FIX;
- dati legacy dei mainframe: file a larghezza fissa ed EBCDIC descritti da
  copybook COBOL;
- sanità: HL7 v2 (messaggi delimitati da barre verticali);
- difesa e pubblica amministrazione: formati di messaggi militari (USMTF, VMF)
  ed EDI (X12);
- dati scientifici e telemetria: NASA/JPL ha usato DFDL per la telemetria
  degli strumenti di bordo, uno dei casi d'uso che hanno dato forma allo
  standard.

Questo elenco è un punto di partenza, non è esaustivo. Il progetto DFDL
Schemas su GitHub pubblica schemi DFDL aperti per molti di questi formati
(vedi sotto).

## Raccolte di esempi

- [DFDL Schemas](https://github.com/DFDLSchemas) -- la raccolta comunitaria di
  schemi DFDL aperti su GitHub, un repository per formato (per es. PCAP, PNG,
  NITF, EDIFACT, ISO 8583), ciascuno con dati di prova.
- [Esempi di Apache Daffodil](https://daffodil.apache.org/examples/) --
  piccoli esempi svolti del progetto Daffodil.

## Come funziona l'adattatore

- **Compilato una volta, poi riutilizzato.** Daffodil compila lo schema al
  primo `apply` e il processore compilato è tenuto in cache da quell'istanza di
  `DfdlStructureRepInfo` - la compilazione è la parte costosa, l'analisi è
  relativamente economica. Create un'istanza per schema e riutilizzatela.
- **L'albero analizzato è quello di Daffodil.** Il risultato viene dall'infoset
  W3C DOM di Daffodil ed è avvolto elemento per elemento (`DomStructureNode`),
  non copiato. I byte dell'Oggetto Digitale sono letti interamente in memoria,
  perché l'adattatore li analizza due volte (vedi il punto seguente).
- **Valori tipizzati.** Una seconda analisi con
  `PositionTrackingInfosetOutputter` recupera il valore tipizzato di ogni
  elemento semplice, così un elemento `xs:int` torna come `Integer`,
  `xs:unsignedByte` come `Short`, e così via, invece che come testo DOM.
  Verificato con Daffodil 3.11.
- **Nessuna posizione dei byte con Daffodil 3.11.** La stessa seconda analisi
  prova anche, per riflessione, gli accessori di posizione che versioni più
  vecchie e più nuove di Daffodil hanno esposto; con la 3.11 nessuno esiste,
  quindi `getSourceRange()` è sempre vuoto. (Gli adattatori DRB e Kaitai
  riportano le posizioni.)
- **La ripetizione è fatta di fratelli con lo stesso nome.** Un elemento
  ripetuto (per es. una `row` CSV) appare come più figli con lo stesso nome del
  suo genitore, la stessa convenzione dell'adattatore DRB e dell'XML semplice;
  usate `childrenNamed("row")`. L'adattatore Kaitai usa invece un unico nodo
  `ARRAY`.
- **I byte avanzati sono segnalati, non ignorati.** Daffodil si ferma quando
  l'elemento radice dello schema è completo e non dice nulla dei dati che
  seguono. L'adattatore prende la posizione finale di Daffodil e imposta
  l'attributo `trailingBytes` del nodo radice (`StructureNode.TRAILING_BYTES`)
  al numero di byte avanzati.
- **Gli errori portano la diagnostica di Daffodil.** Uno schema che non si
  compila, o dati che non vi corrispondono, sollevano
  `StructureInterpretationException` il cui messaggio elenca la diagnostica di
  Daffodil.
- **Logging.** Il modulo porta `slf4j-simple` a runtime per il logging di
  Daffodil; un'applicazione con un proprio binding SLF4J (archive-manager usa
  Logback) dovrebbe escluderlo.

## Scrivere schemi che Daffodil accetta

Sono tutti emersi mentre si facevano compilare gli schemi di questo progetto
con il vero Daffodil:

- **Includete il `GeneralFormat` di Daffodil.** Diverse proprietà di basso
  livello (`leadingSkip`, `initiatedContent`, `textBidi`, `floating`, ...) non
  hanno un valore predefinito, e Daffodil rifiuta di compilare uno schema che ne
  lascia qualcuna non impostata.
  `<xs:include schemaLocation="/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd"/>`
  (risolto dal jar di Daffodil stesso) le imposta tutte; richiamatelo dal
  vostro `dfdl:defineFormat`/`dfdl:format` e sovrascrivete solo ciò che
  differisce, per es. `representation="binary"`.
- **La sorgente di `xs:appinfo` è `http://www.ogf.org/dfdl/`**, non
  `.../dfdl-1.0/` (Daffodil avverte per quest'ultima).
- **Date all'elemento radice `dfdl:lengthKind="implicit"`** quando il valore
  predefinito del formato è `explicit`: la lunghezza di un elemento composto è
  la somma dei suoi figli, e "explicit" senza `dfdl:length` non si compila.
- **Qualificate i nomi degli elementi nelle espressioni** (`../tns:labelLen`,
  non `../labelLen`) quando lo schema usa `elementFormDefault="qualified"`.
- **Terminate ogni riga CSV con `dfdl:terminator="%NL; %ES;"`** (un a capo, o
  la fine dei dati). Un semplice terminatore `%NL;` fa perdere a un file la cui
  ultima riga non ha a capo quella riga -- in silenzio, perché Daffodil ignora i
  dati non analizzati; un separatore infisso tra le righe produce invece una
  riga vuota in più da un a capo finale.
- **Proteggete un record ripetuto fino alla fine dei dati** con
  `<dfdl:assert testKind="pattern" testPattern="(?s)." .../>`. Un record
  testuale che può essere vuoto (per es. un solo campo di testo) altrimenti si
  analizza con successo proprio alla fine dei dati, e Daffodil si ferma con
  "consumed no data and is stuck in an infinite loop". L'asserzione di pattern
  lascia iniziare un altro record solo finché resta almeno un byte.
- **Convertite i piccoli interi prima dell'aritmetica.** Daffodil 3.11 fallisce
  con "Invariant broken ... ClassCastException: Integer cannot be cast to
  Short" quando un'espressione somma o confronta direttamente valori
  `xs:unsignedByte` (e simili), per es. `{ . eq (../a + ../b) mod 256 }`;
  scrivete `{ xs:int(.) eq (xs:int(../a) + xs:int(../b)) mod 256 }`. Il
  generatore di archive-manager avvolge per questo ogni riferimento a un campo
  intero in `xs:integer(...)`.
- **Niente `fn:sum`.** Daffodil non la supporta, quindi un checksum su un
  numero variabile di valori non si può calcolare in un'espressione DFDL; i
  controlli su campi nominati sì (vedi gli esempi di scrittura a mano di
  archive-manager), e per altro serve un layer personalizzato scritto in Java.
- **I layer** trasformano parte dei dati prima dell'analisi: Daffodil 3.11 ha
  i layer integrati `gzip`, `base64_MIME`, `fourbyteswap`/`twobyteswap`,
  `lineFolded_IMF`/`lineFolded_iCalendar`, `boundaryMark` e `fixedLength`.
  Importate lo schema del layer da `/org/apache/daffodil/layers/xsd/` e mettete
  `dfdlx:layer="gz:gzip"` su una sequenza, delimitata da un layer
  `fl:fixedLength` che la racchiude, la cui lunghezza si imposta con
  `dfdl:newVariableInstance`.
- **I campi di bit** richiedono `dfdl:lengthUnits="bits"` con
  `dfdl:alignmentUnits="bits"`; un elemento della dimensione di un byte che li
  segue è allineato al byte intero successivo dall'allineamento predefinito di
  un byte.

## Riscrivere i dati

`DfdlStructureRepInfo` è un `WritableStructureRepInfo`: `write(dataObject, changes)` analizza
nell'infoset DOM di Daffodil, imposta il testo di ogni elemento cambiato (`ElementPath` -> valore), e
ricodifica l'intero infoset con l'unparser di Daffodil; `roundTrip(dataObject)` lo riscrive senza
modifiche e confronta. Tutto ciò che lo schema descrive viene ricodificato dal suo valore, quindi
lunghezze e conteggi possono cambiare se gli elementi che li danno sono cambiati di conseguenza (o sono
calcolati con `dfdl:outputValueCalc`). Ciò che l'infoset non contiene non viene scritto come è stato
letto: i byte dopo i dati descritti, i byte inutilizzati dentro un elemento di lunghezza esplicita
(scritti come byte di riempimento), e quale di più delimitatori o terminatori usavano i dati (si scrive
il primo - per es. un a capo dopo un'ultima riga che non ne aveva).

`encode(infoset)` scrive un nuovo file a partire dai soli valori, senza un originale: l'infoset è un
documento XML con gli elementi dello schema, nel suo namespace e nel suo ordine, che contiene i valori
come testo. `DfdlSchemaOutline.read(schemaText)` dà l'albero degli elementi che un tale infoset segue -
nomi, namespace, quante volte compare ciascuno, tipi dei valori, quali sono calcolati
(`dfdl:outputValueCalc`) e quali sono alternative di una scelta - letto dallo schema come XML Schema,
seguendo i tipi nominati e i riferimenti agli elementi all'interno dello stesso documento. La
Trasformazione di archive-manager costruisce così gli infoset per riscrivere un Oggetto Dati in un altro
formato.

## Limiti noti

- Si può usare solo l'elemento radice predefinito dello schema.
- La seconda analisi per i valori tipizzati è fatta al meglio: se fallisce in
  un modo in cui la prima non fallisce (Daffodil può lanciare un errore interno
  "Abort"), i valori tornano semplicemente come testo DOM.
- Nessuna posizione dei byte con Daffodil 3.11 (vedi sopra).
- La seconda analisi per i valori tipizzati raddoppia il lavoro di analisi e
  tiene l'intero Oggetto Digitale in memoria.
