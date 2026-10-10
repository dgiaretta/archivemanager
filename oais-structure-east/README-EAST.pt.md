# EAST (CCSDS 644.0-B-3)

O EAST (Enhanced Ada SubseT) é a linguagem de descrição de dados do CCSDS: um
*Registro de Descrição de Dados* (Data Description Record) em EAST diz, num
subconjunto das declarações do Ada, exatamente como um conjunto de dados está
disposto, até o bit. É padronizado como CCSDS 644.0-B-3 (junho de 2010), com
as convenções para números reais no CCSDS 646.0-G-1, e foi usado para dados
espaciais arquivados em Unidades de Dados de Formato Padrão (SFDUs). O módulo
`oais-structure-east` não tem dependências de terceiros e faz três coisas com
o EAST.

## Lendo EAST para a árvore de elementos (`EastReader`)

O pacote lógico de uma descrição (tipos, cláusulas de representação e depois
as variáveis na ordem em que os dados as guardam) e o pacote físico (ordem dos
bytes, armazenamento dos vetores, como os números são representados) se tornam
uma `FormatDescription` neutra em relação aos mecanismos, a partir da qual são
geradas as descrições em Kaitai Struct, DFDL e DRB:

- registros se tornam registros, vetores se tornam elementos repetidos (o
  primeiro índice variando mais rápido, a não ser que `ARRAY_STORAGE` diga o
  contrário);
- enumerações se tornam inteiros cujos códigos (de uma cláusula de
  representação de enumeração, ou 0, 1, 2...) significam os literais;
  intervalos de inteiros e de reais se tornam intervalos válidos;
- cláusulas de representação de registro fixam a ordem dos componentes, com o
  espaço não usado entre eles como campos `spare_n`;
- uma parte variante se torna uma escolha quando cada alternativa tem um só
  valor, e senão um registro opcional por alternativa (para `|`, intervalos,
  `others`, `null` e discriminantes verdadeiro/falso);
- discriminantes virtuais são substituídos pelas expressões dos seus valores
  reais, um caminho EAST para dentro de um registro lido antes
  (`LAST_DATE.DAY`) se tornando uma referência com pontos (`last_date.day`);
- `OCTET_STORAGE` dá a ordem dos bytes, e a representação física de um campo
  dá a sua própria ordem dos bytes quando seus subcampos são octetos inteiros
  em ordem inversa (little-endian) ou numa só sequência (big-endian).

Uma descrição descreve um conjunto de dados, aplicado repetidamente a todos os
dados: os conjuntos se repetem até o fim, num registro `set`, a não ser que um
marcador EOF termine a repetição da última variável.

O que a árvore de elementos não consegue expressar é recusado, com a linha e o
motivo: marcadores que não sejam EOF, `**` e as funções do EAST sobre valores
dos dados, campos de bits com sinal ou `LOW_ORDER_FIRST`, inteiros em partes
ou que não estejam em complemento de dois nem sem sinal, e reais em convenções
que não a IEEE 754. O interpretador lê tudo isso.

## Escrevendo EAST a partir da árvore de elementos (`EastWriter`)

Uma `FormatDescription` é escrita como uma descrição EAST: um tipo registro
por registro, um vetor por elemento repetido, uma enumeração com uma cláusula
de representação por campo com lista de códigos, um intervalo por campo com
intervalo válido, e um pacote físico que dá a ordem dos bytes e a
representação de cada real (IEEE 754, FCSTC000) e de cada inteiro cuja ordem
dos bytes não é a da própria descrição. Contagens, comprimentos, condições e
escolhas se tornam discriminantes virtuais cujos valores reais, com os
caminhos EAST dos campos que usam, encerram o pacote lógico. Uma parte
variante vem por último num registro EAST, então um elemento opcional ou uma
escolha se torna um registro próprio, com o nome do elemento. Significados,
unidades, escalas e valores de preenchimento se tornam comentários.

Os nomes são escritos em maiúsculas; nomes que são palavras-chave do EAST ou do
Ada (`RECORD`, `BODY`, `DELTA`...) ganham um `_1`. O que o EAST não consegue
descrever é recusado: texto delimitado, elementos numa posição absoluta,
compressão, registros de tamanho calculado, escolhas sobre texto, repetição até
o fim exceto do último elemento, e texto ou bytes repetidos de comprimento
calculado.

## Interpretando dados com o EAST (`EastStructureRepInfo`)

`EastStructureRepInfo` é uma `ExecutableStructureRepInfo`
(`SpecificationLanguage.EAST`, registrada por `EastStructureInterpreterProvider`)
que lê os dados diretamente como uma descrição EAST diz, dando uma árvore de
`StructureNode`: um nó composto por registro, um nó de vetor por vetor ou
repetição, e uma folha por valor, cada um com os bits de que foi lido. Ela
segue a própria descrição, então lê tudo o que o EAST consegue dizer:

- marcadores: um elemento repetido até que se encontre um valor (um texto, um
  caractere como `ASCII.CR`, ou um número), e marcadores EOF;
- componentes posicionados por cláusulas de representação de registro, em bits
  ou palavras (`WORD_16_BITS`, `WORD_32_BITS`), inclusive alternativas
  sobrepostas e discriminantes guardados depois do que escolhem;
- bits guardados do menos significativo para o mais (`LOW_ORDER_FIRST`);
- inteiros cujos bits estão em vários subcampos, em qualquer das quatro
  convenções de sinal (sem sinal, sinal e magnitude, complemento de um e de
  dois);
- reais em todas as convenções do CCSDS 646.0-G-1: IEEE 754 (FCSTC000), DEC VAX
  (FCSTC001), MIL-STD-1750A (FCSTC002), CDC NOS-VE (FCSTC003) e NOS-BE
  (FCSTC004), e hexadecimal da IBM (FCSTC005);
- números e enumerações em ASCII;
- discriminantes virtuais calculados com qualquer operador do EAST (`**`, `mod`,
  `rem`, `abs`, `cos`, `ln`, `is_odd`, `!`...) a partir de valores em qualquer
  parte dos dados lidos até ali, nomeados pelos seus caminhos EAST.

Os nomes são dados em minúsculas. A folha de uma enumeração binária contém seu
código, com seu literal como o atributo `meaning`. O interpretador lê dados;
não os regrava.

Limites conhecidos: as convenções da CDC seguem os algoritmos do CCSDS
646.0-G-1, mas não foram conferidas com dados gravados em máquinas CDC; os
reais são dados como doubles do Java, então reais de 128 bits perdem precisão.
