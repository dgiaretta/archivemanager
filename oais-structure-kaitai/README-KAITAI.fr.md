# Notes sur oais-structure-kaitai

Ce module relie **Kaitai Struct** au modèle commun `StructureNode`. Il utilise
le runtime Java de Kaitai Struct `io.kaitai:kaitai-struct-runtime:0.11` (licence
MIT, depuis Maven Central), et a été vérifié avec des classes générées par le
compilateur Kaitai Struct 0.11.

## Comment un format est décrit

Une description Kaitai Struct est un fichier `.ksy` (YAML) : une `seq` de
champs, chacun avec un `id` et un `type` (`s4`, `u1`, `f8`, `str`, un type
défini par l'utilisateur, ...), plus `size`, `encoding`, `terminator`, `repeat`
(`eos`, `expr`, `until`), `if`, `switch-on` pour les enregistrements variants,
des `instances` calculées, et `endian` au niveau supérieur. Il est conçu pour la
rétro-ingénierie de formats binaires arbitraires ; la galerie publique de
formats (https://formats.kaitai.io) compte des centaines de spécifications
réelles. Voir `src/main/ksy` pour les deux exemples de ce module : un
enregistrement binaire « point » et un fichier CSV.

**Contrairement à DFDL et DRB, un `.ksy` n'est pas lu à l'exécution.** Le
compilateur Kaitai Struct (`ksc`) le traduit à l'avance en code source - ici,
une classe Java - et c'est cette classe qui analyse les octets. Ainsi :

- `KaitaiFormatSpecification` désigne une **classe générée**
  (`new KaitaiFormatSpecification(Point2d.class)`), pas un fichier `.ksy`.
- Les classes générées doivent être compilées dans l'application. Celles de ce
  module sont versionnées sous `src/main/java/.../generated/`, donc sa
  construction n'a pas besoin du compilateur.
- Les Outils RepInfo d'archive-manager génèrent un `.ksy` à partir de leur
  description indépendante du moteur (un type par enregistrement et par branche
  de choix, `repeat: expr`/`eos`, `if`, `switch-on`, champs texte avec
  `terminator` ; et, quand Kaitai fait partie des langages choisis, champs de
  bits `bN`, enregistrements `size:`, `process: zlib` et `instances` avec
  `pos:` pour les éléments à une position). Ils **peuvent** le tester sur un
  fichier d'exemple : ils embarquent le compilateur Kaitai Struct, l'exécutent
  comme processus Java séparé, compilent le Java généré dans le même processus
  et le chargent (`KaitaiSampleRunner` ; voir le README d'archive-manager).

## À quoi sert Kaitai Struct

Au-delà des deux petits exemples de ce projet, Kaitai Struct sert surtout à
décrire et à rétro-concevoir des formats binaires existants. Les
spécifications de la galerie comprennent :

- conteneurs multimédias : MP4, AVI, WAV, MIDI ;
- exécutables et autres formats binaires : ELF, PE/EXE, Mach-O, `.class` Java ;
- systèmes de fichiers et criminalistique : NTFS, ext2, ruches du registre
  Windows, fichiers prefetch, journaux d'événements (populaires en analyse de
  logiciels malveillants et dans les défis CTF) ;
- formats d'archive et de compression, formats de ressources de jeux, et
  formats de protocoles réseau et de capture de paquets (PCAP).

N'importe quel `.ksy` de la galerie peut être compilé et utilisé avec cet
adaptateur de la même manière que les exemples d'ici.

## Galeries d'exemples

- [Galerie de formats Kaitai Struct](https://formats.kaitai.io) -- des
  centaines de descriptions `.ksy` de formats réels, par catégorie, chacune
  avec ses analyseurs générés et sa documentation.
- [kaitai_struct_formats](https://github.com/kaitai-io/kaitai_struct_formats)
  -- les mêmes descriptions en sources, sur GitHub.
- [Kaitai Struct Web IDE](https://ide.kaitai.io) -- essayez une description sur
  un fichier dans le navigateur.

## Générer les classes

Après avoir modifié un `.ksy`, régénérez avec le compilateur Kaitai Struct
(0.11, https://kaitai.io), depuis `src/main/ksy` :

    kaitai-struct-compiler -t java --java-package info.oais.infomodel.structure.kaitai.generated --debug --outdir ../java point2d.ksy csv_points.ksy

`--debug` fait que les classes générées enregistrent d'où chaque champ a été lu,
ce que cet adaptateur transforme en positions d'octets (voir ci-dessous). Sans
cette option tout le reste fonctionne encore, simplement sans positions.

## Comment fonctionne l'adaptateur

- **Générique, par réflexion.** `KaitaiReflectiveStructureNode` parcourt
  n'importe quelle classe générée sans code propre au format, avec les
  conventions de la cible Java de Kaitai : chaque champ est un accesseur public
  sans argument nommé d'après le champ en camelCase sans préfixe `get`
  (`label_len` devient `labelLen()`), un type imbriqué est un autre
  `KaitaiStruct`, et un champ `repeat` est une `List`. Les accesseurs internes
  de Kaitai (`_io`, `_parent`, `_root`, `_read`, ...) sont ignorés. Les enfants
  viennent dans l'**ordre du fichier** : depuis la liste `_seqFields` d'une
  classe `--debug`, sinon depuis l'ordre des champs déclarés de la classe (la
  réflexion Java ne garantit pas l'ordre des méthodes).
- **Octets restants.** L'attribut `trailingBytes` du nœud racine
  (`StructureNode.TRAILING_BYTES`) dit combien d'octets l'analyse n'a pas
  atteints (la position finale du flux par rapport à sa taille).
- **Valeurs typées.** Les valeurs reviennent avec les types Java des
  accesseurs générés (`int`, `long`, `double`, `String`, `byte[]`, ...).
- **La répétition est un nœud `ARRAY`.** Un champ `repeat` devient un nœud de
  type `ARRAY` dont les enfants s'appellent `0`, `1`, ... (les adaptateurs DFDL
  et DRB utilisent plutôt des frères de même nom). Les vues en table
  sélectionnent ces lignes avec `<rows select="array">`.
- **Positions d'octets depuis les compilations `--debug`.** Une classe compilée
  avec `--debug` a les tables publiques `_attrStart`/`_attrEnd` (le décalage de
  début et de fin de chaque champ, par nom d'accesseur) et `_arrStart`/`_arrEnd`
  (une entrée par élément d'un champ répété). L'adaptateur les lit, donc chaque
  champ - et chaque élément d'un champ répété - rapporte son intervalle
  d'octets. Une classe `--debug` n'analyse pas non plus dans son constructeur ;
  `KaitaiStructureRepInfo` le détecte et appelle `_read()` lui-même
  (appelez-le vous-même si vous construisez directement une telle classe).
- **Types switch.** Pour un champ `switch-on`, le nom de type du nœud est le
  type concret réellement analysé, pas le type commun déclaré.
- **En mémoire.** L'Objet Numérique est lu entièrement dans un tampon d'octets
  avant l'analyse.

## Réécrire les données

`KaitaiStructureRepInfo` est un `WritableStructureRepInfo`, pour les classes compilées avec le mode
lecture-écriture de Kaitai Struct (`kaitai-struct-compiler -w`, Java et Python seulement, à partir de
0.11) ; une classe en lecture seule est refusée avec cette explication. `write(dataObject, changes)` lit
les données, récupère les instances lues paresseusement, règle les valeurs modifiées via les mutateurs
générés (les étapes du chemin nomment les champs comme dans le `.ksy`, `sample_count`, ou comme
l'accesseur, `sampleCount` ; `[n]` choisit un élément d'un champ répété), fait se vérifier chaque objet
modifié (`_check()`, qui refuse p. ex. un compte de répétitions qui ne correspond plus à sa liste, ou une
chaîne qui ne tient plus dans sa taille), et écrit le tout avec `_write`. Kaitai écrit dans un flux de
taille fixe, et un élément répété ou dimensionné jusqu'à la fin des données doit se terminer exactement
là où se termine le flux, donc l'écrivain part de la taille des données elles-mêmes et l'ajuste d'après ce
que rapporte Kaitai. Les octets inutilisés dans un type de `size` déclarée sont écrits comme des zéros.

## Limites connues

- Les descriptions ne peuvent pas être chargées à l'exécution : chaque format
  demande que sa classe soit d'abord générée et compilée (archive-manager fait
  les deux à la demande pour son test sur un exemple, ce qui prend quelques
  secondes).
- Le compilateur Kaitai Struct ne renomme pas les champs dont les noms sont des
  mots réservés de Java (`class`, `int`, `null`, ...), donc leur Java ne se
  compile pas ; l'éditeur d'archive-manager refuse de tels noms quand Kaitai est
  une cible. Les noms que YAML lit comme booléens ou null (`on`, `no`, `null`)
  doivent être mis entre guillemets dans un `.ksy`, comme le fait le générateur
  d'archive-manager.
- L'analyse est immédiate et en mémoire ; pour de très gros fichiers, une
  variante `RandomAccessFileKaitaiStream` de `KaitaiStructureRepInfo` serait
  l'extension naturelle.
