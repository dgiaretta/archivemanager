# Note su oais-structure-kaitai

Questo modulo collega **Kaitai Struct** al modello comune `StructureNode`. Usa il
runtime Java di Kaitai Struct `io.kaitai:kaitai-struct-runtime:0.11` (licenza
MIT, da Maven Central), ed è stato verificato con classi generate dal
compilatore Kaitai Struct 0.11.

## Come si descrive un formato

Una descrizione Kaitai Struct è un file `.ksy` (YAML): una `seq` di campi,
ciascuno con un `id` e un `type` (`s4`, `u1`, `f8`, `str`, un tipo definito
dall'utente, ...), più `size`, `encoding`, `terminator`, `repeat` (`eos`,
`expr`, `until`), `if`, `switch-on` per i record varianti, `instances`
calcolate, ed `endian` al livello superiore. È pensato per il reverse
engineering di formati binari arbitrari; la raccolta pubblica di formati
(https://formats.kaitai.io) ha centinaia di specifiche reali. Vedete
`src/main/ksy` per i due esempi di questo modulo: un record binario "point" e
un file CSV.

**A differenza di DFDL e DRB, un `.ksy` non viene letto a runtime.** Il
compilatore Kaitai Struct (`ksc`) lo traduce in anticipo in codice sorgente -
qui, una classe Java - ed è quella classe ad analizzare i byte. Quindi:

- `KaitaiFormatSpecification` nomina una **classe generata**
  (`new KaitaiFormatSpecification(Point2d.class)`), non un file `.ksy`.
- Le classi generate devono essere compilate nell'applicazione. Quelle di
  questo modulo sono incluse nel repository sotto
  `src/main/java/.../generated/`, quindi la sua compilazione non richiede il
  compilatore.
- Gli Strumenti RepInfo di archive-manager generano un `.ksy` dalla loro
  descrizione indipendente dal motore (un tipo per record e per ramo di scelta,
  `repeat: expr`/`eos`, `if`, `switch-on`, campi di testo con `terminator`; e,
  quando Kaitai è tra i linguaggi scelti, campi di bit `bN`, record con `size:`,
  `process: zlib` e `instances` con `pos:` per elementi a una posizione).
  **Possono** provarlo su un file di esempio: includono il compilatore Kaitai
  Struct, lo eseguono come processo Java separato, compilano il Java generato
  nello stesso processo e lo caricano (`KaitaiSampleRunner`; vedi il README di
  archive-manager).

## A che cosa serve Kaitai Struct

Al di là dei due piccoli esempi di questo progetto, Kaitai Struct si usa
soprattutto per descrivere e fare reverse engineering di formati binari
esistenti. Le specifiche della raccolta includono:

- contenitori multimediali: MP4, AVI, WAV, MIDI;
- eseguibili e altri formati binari: ELF, PE/EXE, Mach-O, `.class` di Java;
- file system e informatica forense: NTFS, ext2, hive del registro di Windows,
  file prefetch, registri degli eventi (popolari nell'analisi di malware e
  nelle sfide CTF);
- formati di archivio e di compressione, formati di risorse dei giochi, e
  formati di protocolli di rete e di cattura di pacchetti (PCAP).

Qualsiasi `.ksy` della raccolta si può compilare e usare con questo
adattatore allo stesso modo degli esempi qui.

## Raccolte di esempi

- [Raccolta di formati Kaitai Struct](https://formats.kaitai.io) -- centinaia
  di descrizioni `.ksy` di formati reali, per categoria, ciascuna con i suoi
  parser generati e la sua documentazione.
- [kaitai_struct_formats](https://github.com/kaitai-io/kaitai_struct_formats)
  -- le stesse descrizioni come sorgenti, su GitHub.
- [Kaitai Struct Web IDE](https://ide.kaitai.io) -- provate una descrizione su
  un file nel browser.

## Generare le classi

Dopo aver modificato un `.ksy`, rigenerate con il compilatore Kaitai Struct
(0.11, https://kaitai.io), da `src/main/ksy`:

    kaitai-struct-compiler -t java --java-package info.oais.infomodel.structure.kaitai.generated --debug --outdir ../java point2d.ksy csv_points.ksy

`--debug` fa sì che le classi generate registrino dove è stato letto ogni campo,
cosa che questo adattatore trasforma in posizioni dei byte (vedi sotto).
Senza, tutto il resto funziona lo stesso, solo senza posizioni.

## Come funziona l'adattatore

- **Generico, per riflessione.** `KaitaiReflectiveStructureNode` percorre
  qualsiasi classe generata senza codice specifico per il formato, usando le
  convenzioni del target Java di Kaitai: ogni campo è un accessore pubblico
  senza argomenti con il nome del campo in camelCase senza prefisso `get`
  (`label_len` diventa `labelLen()`), un tipo annidato è un altro
  `KaitaiStruct`, e un campo `repeat` è una `List`. Gli accessori di servizio
  di Kaitai (`_io`, `_parent`, `_root`, `_read`, ...) sono saltati. I figli
  vengono in **ordine di file**: dall'elenco `_seqFields` di una classe
  `--debug`, altrimenti dall'ordine dei campi dichiarati della classe (la
  riflessione Java non garantisce l'ordine dei metodi).
- **Byte avanzati.** L'attributo `trailingBytes` del nodo radice
  (`StructureNode.TRAILING_BYTES`) dice quanti byte l'analisi non ha raggiunto
  (la posizione finale del flusso rispetto alla sua dimensione).
- **Valori tipizzati.** I valori tornano con i tipi Java degli accessori
  generati (`int`, `long`, `double`, `String`, `byte[]`, ...).
- **La ripetizione è un nodo `ARRAY`.** Un campo `repeat` diventa un nodo di
  tipo `ARRAY` i cui figli si chiamano `0`, `1`, ... (gli adattatori DFDL e DRB
  usano invece fratelli con lo stesso nome). Le viste tabellari selezionano
  queste righe con `<rows select="array">`.
- **Posizioni dei byte dalle compilazioni `--debug`.** Una classe compilata con
  `--debug` ha le mappe pubbliche `_attrStart`/`_attrEnd` (l'offset di inizio e
  di fine di ogni campo, per nome dell'accessore) e `_arrStart`/`_arrEnd` (una
  voce per elemento di un campo ripetuto). L'adattatore le legge, così ogni
  campo - e ogni elemento di un campo ripetuto - riporta il suo intervallo di
  byte. Una classe `--debug` inoltre non analizza nel suo costruttore;
  `KaitaiStructureRepInfo` lo rileva e chiama `_read()` da sé (chiamatelo voi
  se costruite direttamente una tale classe).
- **Tipi switch.** Per un campo `switch-on`, il nome del tipo del nodo è il tipo
  concreto effettivamente analizzato, non il tipo comune dichiarato.
- **In memoria.** L'Oggetto Digitale è letto interamente in un buffer di byte
  prima dell'analisi.

## Riscrivere i dati

`KaitaiStructureRepInfo` è un `WritableStructureRepInfo`, per le classi compilate con la modalità di
lettura e scrittura di Kaitai Struct (`kaitai-struct-compiler -w`, solo Java e Python, dalla 0.11); una
classe di sola lettura è rifiutata con questa spiegazione. `write(dataObject, changes)` legge i dati,
recupera le istanze lette in modo pigro, imposta i valori cambiati tramite i setter generati (i passi del
percorso nominano i campi come nel `.ksy`, `sample_count`, o come fa l'accessore, `sampleCount`; `[n]`
sceglie un elemento di un campo ripetuto), fa verificare a ogni oggetto cambiato se stesso (`_check()`,
che rifiuta per es. un conteggio di ripetizioni che non corrisponde più alla sua lista, o una stringa che
non entra più nella sua dimensione), e scrive tutto con `_write`. Kaitai scrive in un flusso di
dimensione fissa, e un elemento ripetuto o dimensionato fino alla fine dei dati deve finire esattamente
dove finisce il flusso, quindi lo scrittore parte dalla dimensione dei dati stessi e la corregge in base a
ciò che Kaitai riporta. I byte inutilizzati dentro un tipo di `size` dichiarata sono scritti come zeri.

## Limiti noti

- Le descrizioni non si possono caricare a runtime: ogni formato richiede che
  la sua classe sia prima generata e compilata (archive-manager fa entrambe le
  cose su richiesta per la sua prova con un esempio, il che richiede qualche
  secondo).
- Il compilatore Kaitai Struct non rinomina i campi i cui nomi sono parole
  riservate di Java (`class`, `int`, `null`, ...), quindi il loro Java non si
  compila; l'editor di archive-manager rifiuta tali nomi quando Kaitai è un
  obiettivo. I nomi che YAML legge come booleani o null (`on`, `no`, `null`)
  vanno messi tra virgolette in un `.ksy`, come fa il generatore di
  archive-manager.
- L'analisi è immediata e in memoria; per file molto grandi una variante
  `RandomAccessFileKaitaiStream` di `KaitaiStructureRepInfo` sarebbe
  l'estensione naturale.
