# Notes sur oais-structure-dfdl

Ce module relie **DFDL** - le Data Format Description Language de l'Open Grid
Forum (GFD.207) - au modèle commun `StructureNode`, avec **Apache Daffodil
3.11.0** (`daffodil-japi_2.13`, Apache License 2.0, depuis Maven Central),
l'implémentation DFDL de référence.

## Comment un format est décrit

Un schéma DFDL est un XML Schema ordinaire dont les éléments portent des
annotations `dfdl:` qui disent comment chacun est disposé dans les données :
`dfdl:length` et `dfdl:lengthKind`, `dfdl:byteOrder`, `dfdl:representation`
(`binary`/`text`), `dfdl:encoding`, `dfdl:separator`/`dfdl:terminator` pour le
texte délimité, `dfdl:occursCount` pour la répétition, et des expressions comme
`dfdl:length="{ xs:int(../tns:labelLen) }"` qui prennent la longueur d'un champ
dans un autre. Enregistrements binaires, texte à largeur fixe et texte délimité
(p. ex. CSV) peuvent tous être décrits. Voir `src/test/resources` pour un
enregistrement binaire « point » et un exemple CSV, et `oais-structure-demo`
pour d'autres ; les Outils RepInfo d'archive-manager génèrent aussi des schémas
DFDL à partir de leur description indépendante du moteur (comptes en
`dfdl:occursCount`, conditions et choix en `dfdl:choiceDispatchKey`/
`dfdl:choiceBranchKey` et expressions d'occurrence, enregistrements CSV avec
`dfdl:separator`/`dfdl:terminator` ; et, quand DFDL fait partie des langages
choisis, champs de bits avec `dfdl:lengthUnits="bits"`, enregistrements de
longueur explicite, valeurs nulles avec `nillable`/`dfdl:nilValue`, valeurs CSV
entre guillemets avec un bloc d'échappement `dfdl:defineEscapeScheme`, et
formats de nombres `dfdl:textNumberPattern`).

`new DfdlFormatSpecification(schemaUri)` indique le schéma à l'adaptateur ;
c'est l'élément racine par défaut du schéma qui est analysé.

## À quoi sert DFDL

Les exemples de ce projet sont volontairement petits (un enregistrement binaire
« point » et une table CSV, gardés identiques dans les modules DFDL, Kaitai et
DRB pour pouvoir comparer leurs résultats). DFDL lui-même sert à bien plus. Il a
été conçu pour les formats d'*enregistrements* textuels et binaires antérieurs à
XML et JSON, par exemple :

- messagerie financière : SWIFT MT, ISO 20022, FIX ;
- données patrimoniales des mainframes : fichiers à largeur fixe et EBCDIC
  décrits par des copybooks COBOL ;
- santé : HL7 v2 (messages délimités par des barres verticales) ;
- défense et administration : formats de messages militaires (USMTF, VMF) et
  EDI (X12) ;
- données scientifiques et télémesure : la NASA/JPL a utilisé DFDL pour la
  télémesure d'instruments d'engins spatiaux, l'un des cas d'usage qui ont
  façonné la norme.

Cette liste est un point de départ, pas une liste exhaustive. Le projet DFDL
Schemas sur GitHub publie des schémas DFDL ouverts pour beaucoup de ces formats
(voir ci-dessous).

## Galeries d'exemples

- [DFDL Schemas](https://github.com/DFDLSchemas) -- la collection
  communautaire de schémas DFDL ouverts sur GitHub, un dépôt par format (p. ex.
  PCAP, PNG, NITF, EDIFACT, ISO 8583), chacun avec des données de test.
- [Exemples d'Apache Daffodil](https://daffodil.apache.org/examples/) --
  petits exemples commentés du projet Daffodil.

## Comment fonctionne l'adaptateur

- **Compilé une fois, puis réutilisé.** Daffodil compile le schéma au premier
  `apply` et le processeur compilé est mis en cache par cette instance de
  `DfdlStructureRepInfo` - la compilation est la partie coûteuse, l'analyse est
  comparativement bon marché. Créez une instance par schéma et réutilisez-la.
- **L'arbre analysé est celui de Daffodil.** Le résultat vient de l'infoset
  W3C DOM de Daffodil et est enveloppé élément par élément
  (`DomStructureNode`), pas copié. Les octets de l'Objet Numérique sont lus
  entièrement en mémoire, car l'adaptateur les analyse deux fois (voir le point
  suivant).
- **Valeurs typées.** Une seconde analyse avec
  `PositionTrackingInfosetOutputter` récupère la valeur typée de chaque élément
  simple, si bien qu'un élément `xs:int` revient en `Integer`, `xs:unsignedByte`
  en `Short`, etc., plutôt qu'en texte DOM. Vérifié avec Daffodil 3.11.
- **Pas de positions d'octets avec Daffodil 3.11.** La même seconde analyse
  essaie aussi, par réflexion, les accesseurs de position que des versions plus
  anciennes et plus récentes de Daffodil ont exposés ; avec la 3.11 aucun
  n'existe, donc `getSourceRange()` est toujours vide. (Les adaptateurs DRB et
  Kaitai rapportent les positions.)
- **La répétition est faite de frères de même nom.** Un élément répété (p. ex.
  une `row` CSV) apparaît comme plusieurs enfants de même nom de son parent, la
  même convention que l'adaptateur DRB et le XML simple ; utilisez
  `childrenNamed("row")`. L'adaptateur Kaitai utilise plutôt un seul nœud
  `ARRAY`.
- **Les octets restants sont signalés, pas ignorés.** Daffodil s'arrête dès que
  l'élément racine du schéma est complet et ne dit rien des données qui
  suivent. L'adaptateur prend la position finale de Daffodil et règle l'attribut
  `trailingBytes` du nœud racine (`StructureNode.TRAILING_BYTES`) sur le nombre
  d'octets restants.
- **Les erreurs portent les diagnostics de Daffodil.** Un schéma qui ne se
  compile pas, ou des données qui ne lui correspondent pas, lèvent
  `StructureInterpretationException` dont le message liste les diagnostics de
  Daffodil.
- **Journalisation.** Le module apporte `slf4j-simple` à l'exécution pour la
  journalisation de Daffodil ; une application ayant sa propre liaison SLF4J
  (archive-manager utilise Logback) doit l'exclure.

## Écrire des schémas que Daffodil accepte

Tous ces points sont apparus en faisant compiler les schémas de ce projet par
le vrai Daffodil :

- **Incluez le `GeneralFormat` de Daffodil.** Plusieurs propriétés de bas
  niveau (`leadingSkip`, `initiatedContent`, `textBidi`, `floating`, ...) n'ont
  pas de valeur par défaut, et Daffodil refuse de compiler un schéma qui en
  laisse une non définie.
  `<xs:include schemaLocation="/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd"/>`
  (résolu depuis le jar de Daffodil lui-même) les définit toutes ; référencez-le
  depuis votre propre `dfdl:defineFormat`/`dfdl:format` et ne surchargez que ce
  qui diffère, p. ex. `representation="binary"`.
- **La source de `xs:appinfo` est `http://www.ogf.org/dfdl/`**, et non
  `.../dfdl-1.0/` (Daffodil émet un avertissement pour cette dernière).
- **Donnez à l'élément racine `dfdl:lengthKind="implicit"`** quand la valeur
  par défaut du format est `explicit` : la longueur d'un élément composé est la
  somme de ses enfants, et « explicit » sans `dfdl:length` ne compile pas.
- **Qualifiez les noms d'éléments dans les expressions** (`../tns:labelLen`, et
  non `../labelLen`) quand le schéma utilise `elementFormDefault="qualified"`.
- **Terminez chaque ligne CSV par `dfdl:terminator="%NL; %ES;"`** (un saut de
  ligne, ou la fin des données). Un simple terminateur `%NL;` fait perdre à un
  fichier dont la dernière ligne n'a pas de saut de ligne cette ligne -- en
  silence, puisque Daffodil ignore les données non analysées ; un séparateur
  infixe entre les lignes produit au contraire une ligne vide de plus à partir
  d'un saut de ligne final.
- **Protégez un enregistrement répété jusqu'à la fin des données** avec
  `<dfdl:assert testKind="pattern" testPattern="(?s)." .../>`. Un
  enregistrement texte qui peut être vide (p. ex. un seul champ texte) s'analyse
  sinon avec succès tout à la fin des données, et Daffodil s'arrête avec
  « consumed no data and is stuck in an infinite loop ». L'assertion de motif
  ne laisse commencer un autre enregistrement que tant qu'il reste au moins un
  octet.
- **Convertissez les petits entiers avant l'arithmétique.** Daffodil 3.11
  échoue avec « Invariant broken ... ClassCastException: Integer cannot be cast
  to Short » quand une expression additionne ou compare directement des valeurs
  `xs:unsignedByte` (et similaires), p. ex. `{ . eq (../a + ../b) mod 256 }` ;
  écrivez `{ xs:int(.) eq (xs:int(../a) + xs:int(../b)) mod 256 }`. Le
  générateur d'archive-manager enveloppe pour cette raison toute référence à un
  champ entier dans `xs:integer(...)`.
- **Pas de `fn:sum`.** Daffodil ne la prend pas en charge, donc une somme de
  contrôle sur un nombre variable de valeurs ne peut pas être calculée dans une
  expression DFDL ; des vérifications sur des champs nommés, si (voir les
  exemples d'écriture à la main d'archive-manager), et au-delà il faut une
  couche personnalisée écrite en Java.
- **Les couches** transforment une partie des données avant l'analyse :
  Daffodil 3.11 a les couches intégrées `gzip`, `base64_MIME`,
  `fourbyteswap`/`twobyteswap`, `lineFolded_IMF`/`lineFolded_iCalendar`,
  `boundaryMark` et `fixedLength`. Importez le schéma de la couche depuis
  `/org/apache/daffodil/layers/xsd/` et mettez `dfdlx:layer="gz:gzip"` sur une
  séquence, bornée par une couche `fl:fixedLength` englobante dont la longueur
  est fixée avec `dfdl:newVariableInstance`.
- **Les champs de bits** demandent `dfdl:lengthUnits="bits"` avec
  `dfdl:alignmentUnits="bits"` ; un élément de la taille d'un octet qui les suit
  est aligné sur l'octet entier suivant par l'alignement par défaut d'un octet.

## Réécrire les données

`DfdlStructureRepInfo` est un `WritableStructureRepInfo` : `write(dataObject, changes)` analyse dans
l'infoset DOM de Daffodil, règle le texte de chaque élément modifié (`ElementPath` -> valeur), et
réencode tout l'infoset avec l'unparser de Daffodil ; `roundTrip(dataObject)` le réécrit sans
modification et compare. Tout ce que le schéma décrit est réencodé à partir de sa valeur, donc
longueurs et comptes peuvent changer si les éléments qui les donnent sont modifiés en conséquence (ou
calculés avec `dfdl:outputValueCalc`). Ce que l'infoset ne contient pas n'est pas écrit tel qu'il a été
lu : les octets après les données décrites, les octets inutilisés dans un élément de longueur explicite
(écrits comme octet de remplissage), et lequel de plusieurs délimiteurs ou terminateurs les données
utilisaient (c'est le premier qui est écrit - p. ex. un saut de ligne après une dernière ligne qui n'en
avait pas).

`encode(infoset)` écrit un nouveau fichier à partir des seules valeurs, sans original : l'infoset est un
document XML des éléments du schéma, dans son espace de noms et son ordre, contenant les valeurs sous
forme de texte. `DfdlSchemaOutline.read(schemaText)` donne l'arbre d'éléments que suit un tel infoset -
noms, espaces de noms, nombre d'occurrences de chacun, types des valeurs, lesquels sont calculés
(`dfdl:outputValueCalc`) et lesquels sont des alternatives d'un choix - lu dans le schéma en tant que XML
Schema, en suivant les types nommés et les références d'éléments au sein du même document. La
Transformation d'archive-manager construit ainsi des infosets pour réécrire un Objet-Donnée dans un
autre format.

## Limites connues

- Seul l'élément racine par défaut du schéma peut être utilisé.
- La seconde analyse pour les valeurs typées est faite au mieux : si elle échoue
  d'une manière où la première n'échoue pas (Daffodil peut lever une erreur
  interne « Abort »), les valeurs reviennent simplement en texte DOM.
- Pas de positions d'octets avec Daffodil 3.11 (voir ci-dessus).
- La seconde analyse pour les valeurs typées double le travail d'analyse et
  garde tout l'Objet Numérique en mémoire.
