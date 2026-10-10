# Notas sobre o oais-structure-dfdl

Este módulo liga o **DFDL** - a Data Format Description Language do Open Grid
Forum (GFD.207) - ao modelo comum `StructureNode`, usando o **Apache Daffodil
3.11.0** (`daffodil-japi_2.13`, Apache License 2.0, do Maven Central), a
implementação de referência do DFDL.

## Como um formato é descrito

Um esquema DFDL é um XML Schema comum cujos elementos levam anotações `dfdl:`
que dizem como cada um está disposto nos dados: `dfdl:length` e
`dfdl:lengthKind`, `dfdl:byteOrder`, `dfdl:representation` (`binary`/`text`),
`dfdl:encoding`, `dfdl:separator`/`dfdl:terminator` para texto delimitado,
`dfdl:occursCount` para repetição, e expressões como
`dfdl:length="{ xs:int(../tns:labelLen) }"` que tiram o comprimento de um campo
de outro. Registros binários, texto de largura fixa e texto delimitado (ex.:
CSV) podem todos ser descritos. Veja em `src/test/resources` um registro
binário "point" e um exemplo CSV, e mais em `oais-structure-demo`; as
Ferramentas de RepInfo do archive-manager também geram esquemas DFDL a partir
da sua descrição neutra em relação aos mecanismos (contagens como
`dfdl:occursCount`, condições e escolhas como
`dfdl:choiceDispatchKey`/`dfdl:choiceBranchKey` e expressões de ocorrência,
registros CSV com `dfdl:separator`/`dfdl:terminator`; e, quando o DFDL está
entre as linguagens escolhidas, campos de bits com `dfdl:lengthUnits="bits"`,
registros de comprimento explícito, valores nulos com
`nillable`/`dfdl:nilValue`, valores CSV entre aspas com um bloco de escape
`dfdl:defineEscapeScheme`, e formatos de número `dfdl:textNumberPattern`).

`new DfdlFormatSpecification(schemaUri)` aponta o adaptador para um esquema; o
elemento raiz padrão do esquema é o que é lido.

## Para que o DFDL é usado

Os exemplos deste projeto são pequenos de propósito (um registro binário
"point" e uma tabela CSV, mantidos idênticos nos módulos DFDL, Kaitai e DRB
para que seus resultados possam ser comparados). O DFDL em si é usado para
muito mais. Foi feito para os formatos de *registro* de texto e binários
anteriores ao XML e ao JSON, por exemplo:

- mensagens financeiras: SWIFT MT, ISO 20022, FIX;
- dados legados de mainframe: arquivos de largura fixa e EBCDIC descritos por
  copybooks COBOL;
- saúde: HL7 v2 (mensagens delimitadas por barras verticais);
- defesa e governo: formatos de mensagens militares (USMTF, VMF) e EDI (X12);
- dados científicos e telemetria: a NASA/JPL usou o DFDL para a telemetria de
  instrumentos de naves espaciais, um dos casos de uso que moldaram o padrão.

Esta lista é um ponto de partida, não completa. O projeto DFDL Schemas no
GitHub publica esquemas DFDL abertos para muitos desses formatos (veja abaixo).

## Galerias de exemplos

- [DFDL Schemas](https://github.com/DFDLSchemas) -- a coleção comunitária de
  esquemas DFDL abertos no GitHub, um repositório por formato (ex.: PCAP, PNG,
  NITF, EDIFACT, ISO 8583), cada um com dados de teste.
- [Exemplos do Apache Daffodil](https://daffodil.apache.org/examples/) --
  pequenos exemplos resolvidos do projeto Daffodil.

## Como o adaptador funciona

- **Compilado uma vez, depois reusado.** O Daffodil compila o esquema no
  primeiro `apply`, e o processador compilado fica guardado por aquela instância
  de `DfdlStructureRepInfo` - compilar é a parte cara, ler é barato em
  comparação. Crie uma instância por esquema e reuse-a.
- **A árvore lida é a do próprio Daffodil.** O resultado vem do infoset W3C DOM
  do Daffodil e é envolvido elemento por elemento (`DomStructureNode`), não
  copiado. Os bytes do Objeto Digital são lidos por inteiro para a memória,
  porque o adaptador os lê duas vezes (veja o próximo item).
- **Valores tipados.** Uma segunda leitura com
  `PositionTrackingInfosetOutputter` recupera o valor tipado de cada elemento
  simples, então um elemento `xs:int` volta como `Integer`, `xs:unsignedByte`
  como `Short`, e assim por diante, em vez de texto do DOM. Conferido com o
  Daffodil 3.11.
- **Sem posições em bytes com o Daffodil 3.11.** A mesma segunda leitura
  também tenta, por reflexão, os acessores de posição que versões mais antigas
  e mais novas do Daffodil expuseram; no 3.11 nenhum existe, então
  `getSourceRange()` fica sempre vazio. (Os adaptadores DRB e Kaitai informam
  posições.)
- **A repetição são irmãos com o mesmo nome.** Um elemento repetido (ex.: uma
  `row` de CSV) aparece como vários filhos com o mesmo nome do seu pai, a mesma
  convenção do adaptador DRB e do XML comum; use `childrenNamed("row")`. O
  adaptador do Kaitai usa, em vez disso, um só nó `ARRAY`.
- **Os bytes que sobram são informados, não ignorados.** O Daffodil para quando
  o elemento raiz do esquema está completo e não diz nada sobre os dados que vêm
  depois. O adaptador pega a posição final do Daffodil e define o atributo
  `trailingBytes` do nó raiz (`StructureNode.TRAILING_BYTES`) com o número de
  bytes que sobraram.
- **Os erros trazem os diagnósticos do próprio Daffodil.** Um esquema que não
  compila, ou dados que não correspondem a ele, lançam
  `StructureInterpretationException`, cuja mensagem lista os diagnósticos do
  Daffodil.
- **Log.** O módulo traz o `slf4j-simple` em tempo de execução para o log do
  Daffodil; um aplicativo com sua própria ligação SLF4J (o archive-manager usa
  o Logback) deve excluí-lo.

## Escrevendo esquemas que o Daffodil aceita

Tudo isto surgiu ao fazer os esquemas deste projeto compilarem com o Daffodil
real:

- **Inclua o `GeneralFormat` do Daffodil.** Várias propriedades de baixo nível
  (`leadingSkip`, `initiatedContent`, `textBidi`, `floating`, ...) não têm
  padrão, e o Daffodil se recusa a compilar um esquema que deixe alguma sem
  definir.
  `<xs:include schemaLocation="/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd"/>`
  (resolvido a partir do próprio jar do Daffodil) define todas; refira-se a ele
  no seu próprio `dfdl:defineFormat`/`dfdl:format` e mude só o que difere, ex.:
  `representation="binary"`.
- **A fonte do `xs:appinfo` é `http://www.ogf.org/dfdl/`**, não
  `.../dfdl-1.0/` (o Daffodil avisa sobre esta última).
- **Dê ao elemento raiz `dfdl:lengthKind="implicit"`** quando o padrão do
  formato é `explicit`: o comprimento de um elemento composto é a soma dos seus
  filhos, e "explicit" sem `dfdl:length` não compila.
- **Qualifique os nomes dos elementos nas expressões** (`../tns:labelLen`, não
  `../labelLen`) quando o esquema usa `elementFormDefault="qualified"`.
- **Termine cada linha de CSV com `dfdl:terminator="%NL; %ES;"`** (uma nova
  linha, ou o fim dos dados). Um terminador `%NL;` simples faz um arquivo cuja
  última linha não termina em nova linha perder essa linha -- em silêncio, pois
  o Daffodil ignora dados não lidos; já um separador infixo entre as linhas
  produz uma linha extra vazia a partir de uma nova linha final.
- **Proteja um registro repetido até o fim dos dados** com
  `<dfdl:assert testKind="pattern" testPattern="(?s)." .../>`. Um registro de
  texto que pode ser vazio (ex.: um só campo de texto) é lido com sucesso bem no
  fim dos dados, e o Daffodil para com "consumed no data and is stuck in an
  infinite loop". A asserção de padrão só deixa outro registro começar enquanto
  sobrar pelo menos um byte.
- **Converta inteiros pequenos antes da aritmética.** O Daffodil 3.11 falha com
  "Invariant broken ... ClassCastException: Integer cannot be cast to Short"
  quando uma expressão soma ou compara valores `xs:unsignedByte` (e
  semelhantes) diretamente, ex.: `{ . eq (../a + ../b) mod 256 }`; escreva
  `{ xs:int(.) eq (xs:int(../a) + xs:int(../b)) mod 256 }`. O gerador do
  archive-manager envolve toda referência a campo inteiro em `xs:integer(...)`
  por isso.
- **Sem `fn:sum`.** O Daffodil não o suporta, então um checksum sobre um número
  variável de valores não pode ser calculado numa expressão DFDL; verificações
  sobre campos nomeados podem (veja os exemplos de escrita à mão do
  archive-manager), e algo além disso precisa de uma camada própria escrita em
  Java.
- **Camadas** transformam parte dos dados antes de serem lidos: o Daffodil 3.11
  tem as camadas embutidas `gzip`, `base64_MIME`, `fourbyteswap`/`twobyteswap`,
  `lineFolded_IMF`/`lineFolded_iCalendar`, `boundaryMark` e `fixedLength`.
  Importe o esquema da camada de `/org/apache/daffodil/layers/xsd/` e ponha
  `dfdlx:layer="gz:gzip"` numa sequência, limitada por uma camada `fl:fixedLength`
  que a envolve, cujo comprimento é definido com `dfdl:newVariableInstance`.
- **Campos de bits** precisam de `dfdl:lengthUnits="bits"` com
  `dfdl:alignmentUnits="bits"`; um elemento do tamanho de um byte depois deles é
  alinhado ao próximo byte inteiro pelo alinhamento padrão de um byte.

## Regravando dados

`DfdlStructureRepInfo` é uma `WritableStructureRepInfo`: `write(dataObject,
changes)` lê para o infoset DOM do Daffodil, define o texto de cada elemento
alterado (`ElementPath` -> valor) e recodifica o infoset inteiro com o
unparser do Daffodil; `roundTrip(dataObject)` o regrava sem alterações e
compara. Tudo o que o esquema descreve é recodificado a partir do seu valor,
então comprimentos e contagens podem mudar se os elementos que os dão forem
alterados para corresponder (ou forem calculados com `dfdl:outputValueCalc`). O
que o infoset não guarda não é gravado do jeito que foi lido: bytes depois dos
dados descritos, bytes não usados dentro de um elemento de comprimento
explícito (gravados como o byte de preenchimento), e qual de vários
delimitadores ou terminadores os dados usavam (o primeiro é gravado - ex.: uma
nova linha depois de uma última linha que não tinha).

`encode(infoset)` grava um arquivo novo só a partir de valores, sem um original:
o infoset é um documento XML com os elementos do esquema, no seu namespace e na
sua ordem, com os valores como texto. `DfdlSchemaOutline.read(schemaText)` dá a
árvore de elementos que esse infoset segue - nomes, namespaces, quantas vezes
cada um ocorre, tipos de valor, quais são calculados (`dfdl:outputValueCalc`) e
quais são alternativas de uma escolha - lida do esquema como XML Schema,
seguindo tipos nomeados e referências a elementos dentro do único documento. A
Transformação do archive-manager monta infosets assim para regravar um Objeto de
Dados em outro formato.

## Limitações conhecidas

- Só o elemento raiz padrão do esquema pode ser usado.
- A segunda leitura, de valores tipados, é feita no melhor esforço: se falhar de
  um jeito que a primeira leitura não falha (o Daffodil pode lançar um erro
  interno "Abort"), os valores simplesmente voltam como texto do DOM.
- Sem posições em bytes com o Daffodil 3.11 (veja acima).
- A segunda leitura, de valores tipados, dobra o trabalho de leitura e mantém o
  Objeto Digital inteiro em memória.
