# Note su oais-structure-drb

Questo modulo collega il **DRB** Java di GAEL Consultant ("Data Request Broker",
`fr.gael.drb`) al modello comune `StructureNode`, come fa `oais-structure-dfdl`
per Apache Daffodil. È scritto e provato con **DRB 2.5.13**, distribuito con
licenza GNU LGPL v3 e servito dal `third-party/maven-repo` di questo repository
(DRB non è su Maven Central) - vedi `third-party/README.md` per la licenza, il
jar dei sorgenti, e quali dipendenze di DRB sono usate e quali no.

## A che cosa serve DRB

DRB è stato sviluppato da GAEL Systems per l'ESA come "file system virtuale"
federato: un unico albero navigabile su prodotti di dati di Osservazione della
Terra eterogenei, usato negli strumenti del segmento di terra di Sentinel. Si
usa tipicamente per:

- contenitori di prodotti satellitari che combinano molti file (il formato
  SAFE);
- netCDF, HDF5, immagini satellitari JPEG2000, DIMAP, GeoTIFF;
- metadati XML, e archivi ZIP/TAR che racchiudono uno qualsiasi dei
  precedenti.

Questo modulo usa gli schemi SDF di DRB 2.5.13 e il suo supporto XML
integrato; le implementazioni dei formati per la maggior parte dei prodotti
sopra erano pacchetti DRB separati e non sono incluse qui. Gli esempi di questo
progetto (un record binario "point" e una tabella CSV, gli stessi dei moduli
DFDL e Kaitai) sono in `src/test/resources`.

## Raccolte di esempi

Non esiste una raccolta pubblica di schemi SDF per il DRB Java usato qui; quelli
di questo progetto sono in `src/test/resources`. Per drb-python, il successore
Python di GAEL, il supporto ai formati arriva come pacchetti driver:

- [drb-python su GitLab](https://gitlab.com/drb-python) -- i driver (per es.
  XML, ZIP, TAR, netCDF), i topic che riconoscono i prodotti (per es. Sentinel
  SAFE) e gli add-on, come sorgenti.
- [Driver drb-python su PyPI](https://pypi.org/search/?q=drb-driver) -- gli
  stessi driver come pacchetti installabili.

## Due modi di interpretare un Oggetto Digitale

`DrbFormatSpecification` ne sceglie uno:

- **Uno schema SDF** - `new DrbFormatSpecification(schemaUri)`. Il linguaggio
  di descrizione dichiarativo di DRB, il suo equivalente di uno schema DFDL: un
  normale XML Schema i cui elementi portano annotazioni `sdf:block` nel
  namespace `http://www.gael.fr/2004/12/drb/sdf` - `sdf:length`,
  `sdf:byteOrder` (`MSB`/`LSB`), `sdf:encoding` (`BINARY`/`ASCII`/`EBCDIC`),
  `sdf:occurrence`, `sdf:delimiter`, `sdf:offset`, ... Record binari, testo a
  larghezza fissa e testo delimitato (per es. CSV, dove ogni campo ha un
  `sdf:delimiter`) si possono descrivere tutti in questo modo. Vedete
  `src/test/resources` per un record binario little-endian e un esempio CSV;
  anche gli Strumenti RepInfo di archive-manager generano questi schemi dalla
  loro descrizione indipendente dal motore (conteggi e condizioni come query
  `sdf:occurrence`, scelte come query `sdf:signature`, record CSV con
  `sdf:delimiter`).
- **Il riconoscimento dei formati di DRB** -
  `DrbFormatSpecification.autoDetect("xml")`. DRB sceglie una delle sue
  implementazioni integrate (XML, ...) in base all'**estensione del file**,
  quindi va indicata l'estensione abituale dell'Oggetto Digitale.

## Come funziona l'adattatore

- **Riflessione, non una dipendenza di compilazione.** `DrbApi` è l'unica
  classe che tocca DRB, tramite le sue interfacce pubbliche (`DrbNode`,
  `DrbFactoryImpl`, `DrbAttribute`, ...). Il modulo si compila senza DRB, e
  `DrbStructureInterpreterProvider.isAvailable()` riporta semplicemente `false`
  quando DRB non è nel classpath; le applicazioni aggiungono `fr.gael.drb:drb`
  (più `org.slf4j:log4j-over-slf4j` per le sue chiamate di logging log4j 1.x) a
  runtime. I test di questo modulo usano il vero DRB.
- **Tutto in memoria, come gli altri adattatori.** DRB apre i dati per
  percorso, quindi i byte sono scritti in un file temporaneo; l'albero di nodi
  prodotto da DRB è copiato in semplici `StructureNode` (limitati da
  `DrbApi.MAX_NODES`), i nodi di DRB sono chiusi, e il file è cancellato prima
  che `apply` ritorni.
- **Valori tipizzati.** I numeri tornano come `Long` (`BigInteger` oltre il suo
  intervallo) o `Double`, il testo come `String`, secondo il tipo XML Schema
  dichiarato di ogni nodo.
- **Posizioni dei byte e documentazione.** DRB riporta l'`offset` e la `length`
  assoluti in byte di ogni nodo decodificato, che diventano `getSourceRange()`,
  e la `xs:documentation` di un elemento come attributo `documentation`.
- **Dati troppo corti sono un errore.** DRB di per sé non fallisce su dati più
  corti di quanto descrive lo schema (riporta posizioni oltre la fine);
  l'adattatore controlla la posizione di ogni nodo rispetto alla dimensione dei
  dati e lancia invece `StructureInterpretationException`.
- **Byte avanzati.** L'attributo `trailingBytes` del nodo radice
  (`StructureNode.TRAILING_BYTES`) dice quanti byte seguono la fine di ciò che lo
  schema descrive, così una descrizione che si ferma prima è visibile.
- **Serializzato.** DRB non documenta alcuna garanzia di thread-safety, quindi
  le chiamate condividono un solo lock.

## Scrivere schemi SDF che DRB accetta

Sono emersi generando schemi dalle descrizioni di archive-manager:

- **Marcate `constant="false"` una query che dipende dai dati**
  (`<sdf:occurrence constant="false">../count</sdf:occurrence>`). Altrimenti
  DRB la valuta una volta e riusa la prima risposta per ogni ripetizione.
- **Una scelta è fatta di più elementi facoltativi con query
  `sdf:signature`** (per es. `../kind = 2`); DRB legge quello la cui firma è
  vera. Le query dentro un ramo sono un livello più in profondità di quanto
  sembrino, poiché sono valutate dal nodo del ramo stesso.
- **Un delimitatore è un singolo carattere.** `sdf:delimiter` non ha
  l'alternativa "o fine dei dati".
- **Le query possono chiamare Java.** L'XQuery di DRB chiama qualsiasi metodo
  Java statico pubblico tramite un namespace `java:` (`declare namespace s =
  "java:java.lang.System"`), e `doc()` legge file e URL. Usate solo schemi SDF
  di cui vi fidate; archive-manager rifiuta gli schemi scritti a mano che fanno
  l'una o l'altra cosa prima di eseguirli.

## Riscrivere i dati

`DrbStructureRepInfo` è un `WritableStructureRepInfo` quando ha uno schema SDF: i blocchi SDF di DRB
supportano `setValue`, che riscrive un valore là dove è stato letto, in una copia dei dati (il file
temporaneo è scrivibile, quindi DRB lo apre in lettura e scrittura). `write(dataObject, changes)`
riscrive *ogni* valore - il suo nuovo valore, o quello appena letto - quindi riscrivere senza modifiche
(`roundTrip(dataObject)`) verifica che DRB codifichi ogni valore come è memorizzato. I byte che lo schema
non descrive restano come erano, quindi qui un viaggio di andata e ritorno identico non mostra che lo
schema copre ogni byte (la decodifica riporta per questo i byte avanzati). Un valore delimitato può
cambiare lunghezza - DRB sposta ciò che segue - ma uno di lunghezza fissa no: DRB riempie un valore di
testo corto e taglia uno lungo senza lamentarsi, quindi l'adattatore rilegge i dati scritti e rifiuta una
modifica che non è uscita come richiesto.

DRB 2.5.13 scrive un float di 4 byte in big-endian anche quando lo schema dice `LSB` (scambia interi e
double, ma non i float); l'adattatore annota ogni float i cui byte memorizzati si leggono come
little-endian e lo scrive lui stesso in little-endian quando DRB ha finito.

## Limiti noti di DRB

- `xs:hexBinary`/`xs:base64Binary` si decodificano come niente; descrivete i
  byte grezzi come `xs:unsignedByte` ripetuto (con `maxOccurs` e
  `sdf:occurrence`).
- I numeri in virgola mobile little-endian si decodificano correttamente solo
  da DRB 2.5 in poi (DRB 2.2 ignorava `LSB` per `xs:float`/`xs:double`).
- DRB 2.5 non ha un'implementazione HDF5.
- Nel testo delimitato, l'ultimo campo del file ha bisogno del suo
  delimitatore: un file CSV la cui ultima riga non ha a capo finale perde quella
  riga. (DFDL, Kaitai e drb-python la leggono tutti.)
