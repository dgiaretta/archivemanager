# Notas sobre o oais-structure-drb

Este módulo liga o **DRB** ("Data Request Broker", `fr.gael.drb`) em Java da
GAEL Consultant ao modelo comum `StructureNode`, como o `oais-structure-dfdl`
faz com o Apache Daffodil. Foi escrito e testado com o **DRB 2.5.13**, que tem
licença GNU LGPL v3 e é servido pelo próprio `third-party/maven-repo` deste
repositório (o DRB não está no Maven Central) - veja `third-party/README.md`
para a licença, o jar de fontes e quais das dependências do próprio DRB são
usadas e quais não.

## Para que o DRB é usado

O DRB foi desenvolvido pela GAEL Systems para a ESA como um "sistema de
arquivos virtual" federado: uma só árvore navegável sobre produtos de dados de
Observação da Terra heterogêneos, usada nas ferramentas do segmento de solo
dos Sentinel. Costuma ser usado para:

- contêineres de produtos de satélite que reúnem muitos arquivos (o formato
  SAFE);
- netCDF, HDF5, imagens de satélite JPEG2000, DIMAP, GeoTIFF;
- metadados XML, e arquivos ZIP/TAR que envolvem qualquer um dos anteriores.

Este módulo usa os esquemas SDF do DRB 2.5.13 e seu suporte embutido a XML; as
implementações de formato para a maioria dos produtos acima eram pacotes
separados do DRB e não estão incluídas aqui. Os exemplos deste projeto (um
registro binário "point" e uma tabela CSV, os mesmos dos módulos DFDL e Kaitai)
estão em `src/test/resources`.

## Galerias de exemplos

Não há uma galeria pública de esquemas SDF do DRB para o DRB em Java usado
aqui; os deste projeto estão em `src/test/resources`. No drb-python, o sucessor
em Python da GAEL, o suporte a formatos vem em pacotes de drivers:

- [drb-python no GitLab](https://gitlab.com/drb-python) -- os drivers
  (ex.: XML, ZIP, TAR, netCDF), os tópicos que reconhecem produtos (ex.:
  Sentinel SAFE) e os complementos, em código-fonte.
- [drivers do drb-python no PyPI](https://pypi.org/search/?q=drb-driver) --
  os mesmos drivers como pacotes instaláveis.

## Duas formas de interpretar um Objeto Digital

`DrbFormatSpecification` escolhe uma:

- **Um esquema SDF** - `new DrbFormatSpecification(schemaUri)`. A linguagem
  declarativa de descrição do DRB, sua contrapartida de um esquema DFDL: um
  XML Schema comum cujos elementos levam anotações `sdf:block` no namespace
  `http://www.gael.fr/2004/12/drb/sdf` - `sdf:length`, `sdf:byteOrder`
  (`MSB`/`LSB`), `sdf:encoding` (`BINARY`/`ASCII`/`EBCDIC`), `sdf:occurrence`,
  `sdf:delimiter`, `sdf:offset`, ... Registros binários, texto de largura fixa
  e texto delimitado (ex.: CSV, em que cada campo tem um `sdf:delimiter`)
  podem todos ser descritos assim. Veja em `src/test/resources` um registro
  binário little-endian e um exemplo CSV; as Ferramentas de RepInfo do
  archive-manager também geram esses esquemas a partir da sua descrição neutra
  em relação aos mecanismos (contagens e condições como consultas
  `sdf:occurrence`, escolhas como consultas `sdf:signature`, registros CSV com
  `sdf:delimiter`).
- **O reconhecimento de formatos do próprio DRB** -
  `DrbFormatSpecification.autoDetect("xml")`. O DRB escolhe uma das suas
  implementações embutidas (XML, ...) pela **extensão do arquivo**, então é
  preciso informar a extensão usual do Objeto Digital.

## Como o adaptador funciona

- **Reflexão, não uma dependência de compilação.** `DrbApi` é a única classe
  que toca o DRB, por meio das suas interfaces públicas (`DrbNode`,
  `DrbFactoryImpl`, `DrbAttribute`, ...). O módulo compila sem o DRB, e
  `DrbStructureInterpreterProvider.isAvailable()` simplesmente informa `false`
  quando o DRB não está no classpath; os aplicativos acrescentam
  `fr.gael.drb:drb` (mais `org.slf4j:log4j-over-slf4j` para as chamadas de log
  do log4j 1.x) em tempo de execução. Os testes deste módulo usam o DRB real.
- **Tudo em memória, como os outros adaptadores.** O DRB abre os dados por
  caminho, então os bytes são gravados num arquivo temporário; a árvore de nós
  que o DRB produz é copiada para `StructureNode`s simples (limitada por
  `DrbApi.MAX_NODES`), os nós do DRB são fechados e o arquivo é apagado antes
  de `apply` retornar.
- **Valores tipados.** Os números voltam como `Long` (`BigInteger` além do seu
  alcance) ou `Double`, o texto como `String`, pelo tipo de XML Schema
  declarado de cada nó.
- **Posições em bytes e documentação.** O DRB informa o `offset` absoluto em
  bytes e o `length` de cada nó decodificado, que se tornam `getSourceRange()`,
  e o `xs:documentation` de um elemento como um atributo `documentation`.
- **Dados curtos são um erro.** O próprio DRB não falha com dados mais curtos do
  que o esquema descreve (informa posições além do fim); o adaptador confere a
  posição de cada nó com o tamanho dos dados e lança
  `StructureInterpretationException` no lugar.
- **Bytes que sobram.** O atributo `trailingBytes` do nó raiz
  (`StructureNode.TRAILING_BYTES`) diz quantos bytes vêm depois do fim do que o
  esquema descreve, para que uma descrição que para cedo fique visível.
- **Serializado.** O DRB não documenta nenhuma garantia de segurança entre
  threads, então as chamadas a ele compartilham uma trava.

## Escrevendo esquemas SDF que o DRB aceita

Isto surgiu ao gerar esquemas a partir das descrições do archive-manager:

- **Marque uma consulta que depende dos dados com `constant="false"`**
  (`<sdf:occurrence constant="false">../count</sdf:occurrence>`). Senão o DRB
  a avalia uma vez e reusa a primeira resposta em todas as repetições.
- **Uma escolha são vários elementos opcionais com consultas
  `sdf:signature`** (ex.: `../kind = 2`); o DRB lê aquele cuja assinatura é
  verdadeira. As consultas dentro de um ramo ficam um nível mais fundo do que
  parecem, pois são avaliadas a partir do próprio nó do ramo.
- **Um delimitador é um só caractere.** O `sdf:delimiter` não tem a
  alternativa "ou fim dos dados".
- **As consultas podem chamar Java.** O XQuery do DRB chama qualquer método
  Java público estático por meio de um namespace `java:` (`declare namespace s
  = "java:java.lang.System"`), e `doc()` lê arquivos e URLs. Só use esquemas
  SDF em que confia; o archive-manager recusa esquemas escritos à mão que façam
  uma dessas coisas antes de executá-los.

## Regravando dados

`DrbStructureRepInfo` é uma `WritableStructureRepInfo` quando tem um esquema
SDF: os blocos SDF do DRB suportam `setValue`, que grava um valor de volta onde
ele foi lido, numa cópia dos dados (o arquivo temporário pode ser gravado,
então o DRB o abre para leitura e escrita). `write(dataObject, changes)` grava
*todos* os valores de novo - seu novo valor, ou o que acabou de ser lido -
então regravar sem alterações (`roundTrip(dataObject)`) testa se o DRB codifica
cada valor do jeito que está armazenado. Os bytes que o esquema não descreve
ficam como estavam, então uma ida e volta idêntica aqui não mostra que o
esquema cobre todos os bytes (para isso, a decodificação informa os bytes que
sobram). Um valor delimitado pode mudar de comprimento - o DRB desloca o que
vem depois - mas um de comprimento fixo não: o DRB completa um texto curto e
corta um longo sem reclamar, então o adaptador lê de volta os dados gravados e
recusa uma alteração que não saiu como pedida.

O DRB 2.5.13 grava um float de 4 bytes em big-endian mesmo quando o esquema diz
`LSB` (inverte inteiros e doubles, mas não floats); o adaptador anota cada float
cujos bytes armazenados se leem como little-endian e o grava ele mesmo em
little-endian depois que o DRB termina.

## Limitações conhecidas do DRB

- `xs:hexBinary`/`xs:base64Binary` são decodificados como nada; descreva bytes
  brutos como `xs:unsignedByte` repetido (com `maxOccurs` e `sdf:occurrence`).
- Ponto flutuante little-endian só é decodificado corretamente a partir do
  DRB 2.5 (o DRB 2.2 ignorava `LSB` para `xs:float`/`xs:double`).
- O DRB 2.5 não tem implementação de HDF5.
- Em texto delimitado, o último campo do arquivo precisa do seu delimitador: um
  arquivo CSV cuja última linha não termina em nova linha perde essa linha. (O
  DFDL, o Kaitai e o drb-python a leem.)
