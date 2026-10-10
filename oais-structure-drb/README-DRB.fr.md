# Notes sur oais-structure-drb

Ce module relie le **DRB** Java de GAEL Consultant (« Data Request Broker »,
`fr.gael.drb`) au modèle commun `StructureNode`, comme `oais-structure-dfdl` le
fait pour Apache Daffodil. Il est écrit et testé avec **DRB 2.5.13**, sous
licence GNU LGPL v3 et servi depuis le `third-party/maven-repo` de ce dépôt (DRB
n'est pas sur Maven Central) - voir `third-party/README.md` pour la licence, le
jar des sources, et quelles dépendances de DRB sont utilisées ou non.

## À quoi sert DRB

DRB a été développé par GAEL Systems pour l'ESA comme « système de fichiers
virtuel » fédéré : un seul arbre navigable sur des produits de données
d'Observation de la Terre hétérogènes, utilisé dans l'outillage du segment sol
de Sentinel. On l'utilise typiquement pour :

- les conteneurs de produits satellitaires qui combinent de nombreux fichiers
  (le format SAFE) ;
- netCDF, HDF5, l'imagerie satellitaire JPEG2000, DIMAP, GeoTIFF ;
- les métadonnées XML, et les archives ZIP/TAR qui enveloppent l'un des
  précédents.

Ce module utilise les schémas SDF de DRB 2.5.13 et sa prise en charge intégrée
de XML ; les implémentations de formats pour la plupart des produits ci-dessus
étaient des paquets DRB séparés et ne sont pas incluses ici. Les exemples de ce
projet (un enregistrement binaire « point » et une table CSV, les mêmes que dans
les modules DFDL et Kaitai) sont dans `src/test/resources`.

## Galeries d'exemples

Il n'existe pas de galerie publique de schémas SDF pour le DRB Java utilisé
ici ; ceux de ce projet sont dans `src/test/resources`. Pour drb-python, le
successeur Python de GAEL, la prise en charge des formats prend la forme de
paquets de pilotes :

- [drb-python sur GitLab](https://gitlab.com/drb-python) -- les pilotes (p. ex.
  XML, ZIP, TAR, netCDF), les topics qui reconnaissent les produits (p. ex.
  Sentinel SAFE) et les modules complémentaires, en sources.
- [Pilotes drb-python sur PyPI](https://pypi.org/search/?q=drb-driver) -- les
  mêmes pilotes en paquets installables.

## Deux façons d'interpréter un Objet Numérique

`DrbFormatSpecification` en choisit une :

- **Un schéma SDF** - `new DrbFormatSpecification(schemaUri)`. Le langage de
  description déclaratif de DRB, son équivalent d'un schéma DFDL : un XML Schema
  ordinaire dont les éléments portent des annotations `sdf:block` dans l'espace
  de noms `http://www.gael.fr/2004/12/drb/sdf` - `sdf:length`, `sdf:byteOrder`
  (`MSB`/`LSB`), `sdf:encoding` (`BINARY`/`ASCII`/`EBCDIC`), `sdf:occurrence`,
  `sdf:delimiter`, `sdf:offset`, ... Enregistrements binaires, texte à largeur
  fixe et texte délimité (p. ex. CSV, où chaque champ a un `sdf:delimiter`)
  peuvent tous être décrits ainsi. Voir `src/test/resources` pour un
  enregistrement binaire little-endian et un exemple CSV ; les Outils RepInfo
  d'archive-manager génèrent aussi ces schémas à partir de leur description
  indépendante du moteur (comptes et conditions en requêtes `sdf:occurrence`,
  choix en requêtes `sdf:signature`, enregistrements CSV avec
  `sdf:delimiter`).
- **La reconnaissance de formats de DRB** -
  `DrbFormatSpecification.autoDetect("xml")`. DRB choisit l'une de ses
  implémentations intégrées (XML, ...) d'après l'**extension du fichier**, il
  faut donc donner l'extension habituelle de l'Objet Numérique.

## Comment fonctionne l'adaptateur

- **Réflexion, pas une dépendance de compilation.** `DrbApi` est la seule
  classe qui touche à DRB, par ses interfaces publiques (`DrbNode`,
  `DrbFactoryImpl`, `DrbAttribute`, ...). Le module se compile sans DRB, et
  `DrbStructureInterpreterProvider.isAvailable()` renvoie simplement `false`
  quand DRB n'est pas dans le classpath ; les applications ajoutent
  `fr.gael.drb:drb` (plus `org.slf4j:log4j-over-slf4j` pour ses appels de
  journalisation log4j 1.x) à l'exécution. Les tests de ce module utilisent le
  vrai DRB.
- **Entièrement en mémoire, comme les autres adaptateurs.** DRB ouvre les
  données par chemin, donc les octets sont écrits dans un fichier temporaire ;
  l'arbre de nœuds que produit DRB est copié dans de simples `StructureNode`
  (bornés par `DrbApi.MAX_NODES`), les nœuds de DRB sont fermés, et le fichier
  est supprimé avant que `apply` ne revienne.
- **Valeurs typées.** Les nombres reviennent en `Long` (`BigInteger` au-delà de
  son intervalle) ou `Double`, le texte en `String`, selon le type XML Schema
  déclaré de chaque nœud.
- **Positions d'octets et documentation.** DRB rapporte l'`offset` et la
  `length` absolus en octets de chaque nœud décodé, qui deviennent
  `getSourceRange()`, et la `xs:documentation` d'un élément en attribut
  `documentation`.
- **Des données trop courtes sont une erreur.** DRB lui-même n'échoue pas sur
  des données plus courtes que ce que décrit le schéma (il rapporte des
  positions au-delà de la fin) ; l'adaptateur vérifie la position de chaque
  nœud par rapport à la taille des données et lève à la place
  `StructureInterpretationException`.
- **Octets restants.** L'attribut `trailingBytes` du nœud racine
  (`StructureNode.TRAILING_BYTES`) dit combien d'octets suivent la fin de ce que
  décrit le schéma, de sorte qu'une description qui s'arrête trop tôt se voit.
- **Sérialisé.** DRB ne documente aucune garantie de sûreté entre threads, donc
  les appels partagent un seul verrou.

## Écrire des schémas SDF que DRB accepte

Ces points sont apparus en générant des schémas à partir des descriptions
d'archive-manager :

- **Marquez `constant="false"` une requête qui dépend des données**
  (`<sdf:occurrence constant="false">../count</sdf:occurrence>`). Sinon DRB
  l'évalue une fois et réutilise la première réponse pour chaque répétition.
- **Un choix est fait de plusieurs éléments facultatifs avec des requêtes
  `sdf:signature`** (p. ex. `../kind = 2`) ; DRB lit celui dont la signature
  est vraie. Les requêtes à l'intérieur d'une branche sont un niveau plus
  profond qu'elles n'en ont l'air, puisqu'elles sont évaluées depuis le nœud de
  la branche elle-même.
- **Un délimiteur est un seul caractère.** `sdf:delimiter` n'a pas
  d'alternative « ou fin des données ».
- **Les requêtes peuvent appeler Java.** Le XQuery de DRB appelle n'importe
  quelle méthode Java statique publique via un espace de noms `java:`
  (`declare namespace s = "java:java.lang.System"`), et `doc()` lit des
  fichiers et des URL. N'utilisez que des schémas SDF de confiance ;
  archive-manager refuse les schémas écrits à la main qui font l'un ou l'autre
  avant de les exécuter.

## Réécrire les données

`DrbStructureRepInfo` est un `WritableStructureRepInfo` quand il a un schéma SDF : les blocs SDF de DRB
prennent en charge `setValue`, qui réécrit une valeur à l'endroit où elle a été lue, dans une copie des
données (le fichier temporaire est accessible en écriture, donc DRB l'ouvre en lecture-écriture).
`write(dataObject, changes)` réécrit *chaque* valeur - sa nouvelle valeur, ou celle qui vient d'être
lue - donc réécrire sans modification (`roundTrip(dataObject)`) vérifie que DRB encode chaque valeur
comme elle est stockée. Les octets que le schéma ne décrit pas restent tels quels, donc un aller-retour
identique ne montre pas ici que le schéma couvre chaque octet (le décodage rapporte pour cela les octets
restants). Une valeur délimitée peut changer de longueur - DRB décale ce qui suit - mais pas une valeur de
longueur fixe : DRB complète une valeur texte trop courte et tronque une trop longue sans rien dire, donc
l'adaptateur relit les données écrites et refuse une modification qui n'est pas sortie comme demandé.

DRB 2.5.13 écrit un flottant de 4 octets en big-endian même quand le schéma dit `LSB` (il permute les
entiers et les doubles, mais pas les flottants) ; l'adaptateur note chaque flottant dont les octets
stockés se lisent en little-endian et l'écrit lui-même en little-endian une fois DRB terminé.

## Limites connues de DRB

- `xs:hexBinary`/`xs:base64Binary` se décodent en rien ; décrivez les octets
  bruts comme `xs:unsignedByte` répété (avec `maxOccurs` et `sdf:occurrence`).
- Les nombres à virgule flottante little-endian ne sont correctement décodés
  qu'à partir de DRB 2.5 (DRB 2.2 ignorait `LSB` pour `xs:float`/`xs:double`).
- DRB 2.5 n'a pas d'implémentation HDF5.
- Dans le texte délimité, le dernier champ du fichier a besoin de son
  délimiteur : un fichier CSV dont la dernière ligne n'a pas de saut de ligne
  final perd cette ligne. (DFDL, Kaitai et drb-python la lisent tous.)
