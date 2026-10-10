# EAST (CCSDS 644.0-B-3)

EAST (Enhanced Ada SubseT) est le langage de description de données du CCSDS :
un *Data Description Record* EAST dit, dans un sous-ensemble des déclarations
d'Ada, exactement comment un ensemble de données est disposé, jusqu'au bit. Il
est normalisé sous la référence CCSDS 644.0-B-3 (juin 2010), avec les
conventions pour les nombres réels dans CCSDS 646.0-G-1, et a servi pour des
données spatiales archivées en Standard Formatted Data Units (SFDU). Le module
`oais-structure-east` n'a aucune dépendance tierce, et fait trois choses avec
EAST.

## Lire EAST dans l'arbre des éléments (`EastReader`)

Le paquetage logique d'une description (types, clauses de représentation, puis
les variables dans l'ordre où les données les contiennent) et son paquetage
physique (ordre des octets, stockage des tableaux, représentation des nombres)
deviennent une `FormatDescription` indépendante du moteur, à partir de laquelle
sont générées des descriptions Kaitai Struct, DFDL et DRB :

- les enregistrements deviennent des enregistrements, les tableaux des éléments
  répétés (le premier indice variant le plus vite, sauf si `ARRAY_STORAGE` dit
  autrement) ;
- les énumérations deviennent des entiers dont les codes (d'une clause de
  représentation d'énumération, ou 0, 1, 2...) signifient les littéraux ; les
  intervalles entiers et réels deviennent des intervalles valides ;
- les clauses de représentation d'enregistrement fixent l'ordre des composants,
  avec l'espace inutilisé entre eux en champs `spare_n` ;
- une partie variante devient un choix quand chaque alternative a une seule
  valeur, et sinon un enregistrement facultatif par alternative (pour `|`, les
  intervalles, `others`, `null` et les discriminants vrai/faux) ;
- les discriminants virtuels sont remplacés par les expressions de leurs valeurs
  effectives, un chemin EAST dans un enregistrement lu plus tôt
  (`LAST_DATE.DAY`) devenant une référence pointée (`last_date.day`) ;
- `OCTET_STORAGE` donne l'ordre des octets, et la représentation physique d'un
  champ son propre ordre des octets quand ses sous-champs sont des octets
  entiers dans l'ordre inverse (little-endian) ou une seule suite (big-endian).

Une description décrit un ensemble de données, appliqué de façon répétée à la
totalité des données : les ensembles se répètent jusqu'à la fin, dans un
enregistrement `set`, sauf si un marqueur EOF termine la répétition de la
dernière variable.

Ce que l'arbre des éléments ne peut pas exprimer est refusé avec la ligne et la
raison : les marqueurs autres que EOF, `**` et les fonctions EAST sur des valeurs
issues des données, les champs de bits signés ou `LOW_ORDER_FIRST`, les entiers
en plusieurs parties ou ni en complément à deux ni non signés, et les réels dans
d'autres conventions que IEEE 754. L'interpréteur les lit tous.

## Écrire EAST à partir de l'arbre des éléments (`EastWriter`)

Une `FormatDescription` est écrite comme une description EAST : un type
enregistrement par enregistrement, un tableau par élément répété, une
énumération avec une clause de représentation par champ ayant une liste de
codes, un intervalle par champ ayant un intervalle valide, et un paquetage
physique donnant l'ordre des octets et la représentation de chaque réel
(IEEE 754, FCSTC000) et de chaque entier dont l'ordre des octets n'est pas celui
de la description. Comptes, longueurs, conditions et choix deviennent des
discriminants virtuels dont les valeurs effectives, avec les chemins EAST des
champs qu'ils utilisent, terminent le paquetage logique. Une partie variante
vient en dernier dans un enregistrement EAST, donc un élément facultatif ou un
choix devient un enregistrement à part entière, du nom de l'élément. Sens,
unités, mise à l'échelle et valeurs de remplissage deviennent des
commentaires.

Les noms sont écrits en majuscules ; les noms qui sont des mots-clés d'EAST ou
d'Ada (`RECORD`, `BODY`, `DELTA`...) reçoivent `_1`. Ce qu'EAST ne peut pas
décrire est refusé : texte délimité, éléments à une position absolue,
compression, enregistrements de taille calculée, choix sur du texte, répétition
jusqu'à la fin sauf pour le dernier élément, et texte ou octets répétés de
longueur calculée.

## Interpréter des données avec EAST (`EastStructureRepInfo`)

`EastStructureRepInfo` est un `ExecutableStructureRepInfo`
(`SpecificationLanguage.EAST`, enregistré par
`EastStructureInterpreterProvider`) qui lit directement les données comme le dit
une description EAST, en donnant un arbre de `StructureNode` : un nœud composé
par enregistrement, un nœud tableau par tableau ou répétition, et une feuille
par valeur, chacun avec les bits d'où il a été lu. Il suit la description
elle-même, donc il lit tout ce qu'EAST sait dire :

- marqueurs : un élément répété jusqu'à ce qu'une valeur (une chaîne, un
  caractère comme `ASCII.CR`, ou un nombre) soit trouvée, et marqueurs EOF ;
- composants placés par des clauses de représentation d'enregistrement, en bits
  ou en mots (`WORD_16_BITS`, `WORD_32_BITS`), y compris des alternatives qui
  se chevauchent et des discriminants stockés après ce qu'ils choisissent ;
- bits stockés du moins significatif au plus significatif (`LOW_ORDER_FIRST`) ;
- entiers dont les bits sont répartis en plusieurs sous-champs, dans l'une des
  quatre conventions de signe (non signé, signe et grandeur, complément à un et
  à deux) ;
- réels dans chaque convention de CCSDS 646.0-G-1 : IEEE 754 (FCSTC000), DEC
  VAX (FCSTC001), MIL-STD-1750A (FCSTC002), CDC NOS-VE (FCSTC003) et NOS-BE
  (FCSTC004), et hexadécimal IBM (FCSTC005) ;
- nombres et énumérations en ASCII ;
- discriminants virtuels calculés avec n'importe quel opérateur EAST (`**`,
  `mod`, `rem`, `abs`, `cos`, `ln`, `is_odd`, `!`...) à partir de valeurs prises
  n'importe où dans les données lues jusque-là, désignées par leurs chemins
  EAST.

Les noms sont donnés en minuscules. La feuille d'une énumération binaire
contient son code, avec son littéral comme attribut `meaning`. L'interpréteur lit
les données ; il ne les réécrit pas.

Limites connues : les conventions CDC suivent les algorithmes de CCSDS
646.0-G-1 mais n'ont pas été vérifiées sur des données écrites sur des machines
CDC ; les réels sont rendus en doubles Java, donc les réels sur 128 bits perdent
de la précision.
