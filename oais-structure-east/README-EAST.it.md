# EAST (CCSDS 644.0-B-3)

EAST (Enhanced Ada SubseT) è il linguaggio di descrizione dei dati del CCSDS: un
*Data Description Record* EAST dice, in un sottoinsieme delle dichiarazioni di
Ada, esattamente come è disposto un insieme di dati, fino al bit. È
standardizzato come CCSDS 644.0-B-3 (giugno 2010), con le convenzioni per i
numeri reali in CCSDS 646.0-G-1, ed è stato usato per dati spaziali archiviati
in Standard Formatted Data Units (SFDU). Il modulo `oais-structure-east` non ha
dipendenze di terze parti, e fa tre cose con EAST.

## Leggere EAST nell'albero degli elementi (`EastReader`)

Il pacchetto logico di una descrizione (tipi, clausole di rappresentazione, poi
le variabili nell'ordine in cui i dati le contengono) e il pacchetto fisico
(ordine dei byte, memorizzazione degli array, come sono rappresentati i numeri)
diventano una `FormatDescription` indipendente dal motore, da cui si generano
descrizioni Kaitai Struct, DFDL e DRB:

- i record diventano record, gli array elementi ripetuti (con il primo indice
  che varia più velocemente, salvo che `ARRAY_STORAGE` dica altrimenti);
- le enumerazioni diventano interi i cui codici (da una clausola di
  rappresentazione dell'enumerazione, oppure 0, 1, 2...) significano i
  letterali; gli intervalli interi e reali diventano intervalli validi;
- le clausole di rappresentazione dei record fissano l'ordine dei componenti,
  con lo spazio inutilizzato tra di essi come campi `spare_n`;
- una parte variante diventa una scelta quando ogni alternativa ha un solo
  valore, e altrimenti un record facoltativo per alternativa (per `|`,
  intervalli, `others`, `null` e discriminanti vero/falso);
- i discriminanti virtuali sono sostituiti dalle espressioni dei loro valori
  effettivi, e un percorso EAST dentro un record letto prima (`LAST_DATE.DAY`)
  diventa un riferimento puntato (`last_date.day`);
- `OCTET_STORAGE` dà l'ordine dei byte, e la rappresentazione fisica di un
  campo il suo proprio ordine dei byte quando i suoi sottocampi sono ottetti
  interi in ordine inverso (little-endian) o una sola sequenza (big-endian).

Una descrizione descrive un insieme di dati, applicato ripetutamente all'intero
dei dati: gli insiemi si ripetono fino alla fine, in un record `set`, a meno che
un marcatore EOF non termini la ripetizione dell'ultima variabile.

Ciò che l'albero degli elementi non sa esprimere è rifiutato con la riga e il
motivo: marcatori diversi da EOF, `**` e le funzioni EAST su valori presi dai
dati, campi di bit con segno o `LOW_ORDER_FIRST`, interi in più parti o non in
complemento a due né senza segno, e reali in convenzioni diverse da IEEE 754.
L'interprete li legge tutti.

## Scrivere EAST dall'albero degli elementi (`EastWriter`)

Una `FormatDescription` è scritta come descrizione EAST: un tipo record per
ogni record, un array per ogni elemento ripetuto, un'enumerazione con una
clausola di rappresentazione per ogni campo con un elenco di codici, un
intervallo per ogni campo con un intervallo valido, e un pacchetto fisico che dà
l'ordine dei byte e la rappresentazione di ogni reale (IEEE 754, FCSTC000) e di
ogni intero il cui ordine dei byte non è quello della descrizione. Conteggi,
lunghezze, condizioni e scelte diventano discriminanti virtuali i cui valori
effettivi, con i percorsi EAST dei campi che usano, chiudono il pacchetto
logico. In un record EAST la parte variante viene per ultima, quindi un
elemento facoltativo o una scelta diventa un record a sé, con il nome
dell'elemento. Significati, unità, scala e valori di riempimento diventano
commenti.

I nomi sono scritti in maiuscolo; ai nomi che sono parole chiave di EAST o Ada
(`RECORD`, `BODY`, `DELTA`...) si aggiunge `_1`. Ciò che EAST non sa descrivere
è rifiutato: testo delimitato, elementi a una posizione assoluta, compressione,
record di dimensione calcolata, scelte su testo, ripetizione fino alla fine se
non dell'ultimo elemento, e testo o byte ripetuti di lunghezza calcolata.

## Interpretare i dati con EAST (`EastStructureRepInfo`)

`EastStructureRepInfo` è un `ExecutableStructureRepInfo`
(`SpecificationLanguage.EAST`, registrato da `EastStructureInterpreterProvider`)
che legge i dati direttamente come dice una descrizione EAST, dando un albero di
`StructureNode`: un nodo composto per ogni record, un nodo array per ogni array o
ripetizione, e una foglia per ogni valore, ciascuno con i bit da cui è stato
letto. Segue la descrizione stessa, quindi legge tutto ciò che EAST sa dire:

- marcatori: un elemento ripetuto finché non si trova un valore (una stringa,
  un carattere come `ASCII.CR`, o un numero), e marcatori EOF;
- componenti posizionati da clausole di rappresentazione dei record, in bit o
  parole (`WORD_16_BITS`, `WORD_32_BITS`), comprese alternative sovrapposte e
  discriminanti memorizzati dopo ciò che scelgono;
- bit memorizzati dal meno significativo (`LOW_ORDER_FIRST`);
- interi i cui bit sono in più sottocampi, in una qualsiasi delle quattro
  convenzioni di segno (senza segno, segno e grandezza, complemento a uno e a
  due);
- reali in ogni convenzione di CCSDS 646.0-G-1: IEEE 754 (FCSTC000), DEC VAX
  (FCSTC001), MIL-STD-1750A (FCSTC002), CDC NOS-VE (FCSTC003) e NOS-BE
  (FCSTC004), ed esadecimale IBM (FCSTC005);
- numeri ed enumerazioni in ASCII;
- discriminanti virtuali calcolati con qualsiasi operatore EAST (`**`, `mod`,
  `rem`, `abs`, `cos`, `ln`, `is_odd`, `!`...) da valori ovunque nei dati letti
  fin lì, nominati dai loro percorsi EAST.

I nomi sono dati in minuscolo. La foglia di un'enumerazione binaria contiene il
suo codice, con il suo letterale come attributo `meaning`. L'interprete legge i
dati; non li riscrive.

Limiti noti: le convenzioni CDC seguono gli algoritmi di CCSDS 646.0-G-1 ma non
sono state verificate su dati scritti su macchine CDC; i reali sono dati come
double Java, quindi i reali a 128 bit perdono precisione.
