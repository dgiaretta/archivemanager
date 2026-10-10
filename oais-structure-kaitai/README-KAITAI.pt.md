# Notas sobre o oais-structure-kaitai

Este módulo liga o **Kaitai Struct** ao modelo comum `StructureNode`. Usa o
runtime Java do Kaitai Struct `io.kaitai:kaitai-struct-runtime:0.11` (licença
MIT, do Maven Central) e foi conferido com classes geradas pelo compilador do
Kaitai Struct 0.11.

## Como um formato é descrito

Uma descrição em Kaitai Struct é um arquivo `.ksy` (YAML): uma `seq` de campos,
cada um com um `id` e um `type` (`s4`, `u1`, `f8`, `str`, um tipo definido pelo
usuário, ...), mais `size`, `encoding`, `terminator`, `repeat` (`eos`, `expr`,
`until`), `if`, `switch-on` para registros variantes, `instances` calculadas, e
`endian` no nível de cima. É feito para a engenharia reversa de formatos
binários quaisquer; a galeria pública de formatos (https://formats.kaitai.io)
tem centenas de especificações reais. Veja em `src/main/ksy` os dois exemplos
deste módulo: um registro binário "point" e um arquivo CSV.

**Ao contrário do DFDL e do DRB, um `.ksy` não é lido em tempo de execução.** O
compilador do Kaitai Struct (`ksc`) o traduz antecipadamente em código-fonte -
aqui, uma classe Java - e é essa classe que lê os bytes. Então:

- `KaitaiFormatSpecification` nomeia uma **classe gerada**
  (`new KaitaiFormatSpecification(Point2d.class)`), não um arquivo `.ksy`.
- As classes geradas precisam ser compiladas no aplicativo. As deste módulo
  estão versionadas em `src/main/java/.../generated/`, então sua compilação não
  precisa do compilador.
- As Ferramentas de RepInfo do archive-manager geram um `.ksy` a partir da sua
  descrição neutra em relação aos mecanismos (um tipo por registro e por ramo
  de escolha, `repeat: expr`/`eos`, `if`, `switch-on`, campos de texto com
  `terminator`; e, quando o Kaitai está entre as linguagens escolhidas, campos
  de bits `bN`, registros com `size:`, `process: zlib` e `instances` com `pos:`
  para elementos numa posição). Elas **conseguem** testá-lo com um arquivo de
  amostra: incluem o compilador do Kaitai Struct, executam-no como um processo
  Java à parte, compilam o Java gerado no mesmo processo e o carregam
  (`KaitaiSampleRunner`; veja o README do archive-manager).

## Para que o Kaitai Struct é usado

Além dos dois pequenos exemplos deste projeto, o Kaitai Struct é usado
sobretudo para descrever e fazer a engenharia reversa de formatos binários
existentes. As especificações da galeria incluem:

- contêineres de mídia: MP4, AVI, WAV, MIDI;
- executáveis e outros formatos binários: ELF, PE/EXE, Mach-O, `.class` do Java;
- sistemas de arquivos e perícia: NTFS, ext2, hives do registro do Windows,
  arquivos de prefetch, logs de eventos (comuns na análise de malware e em
  desafios CTF);
- formatos de arquivamento e compressão, formatos de recursos de jogos, e
  formatos de protocolos de rede e de captura de pacotes (PCAP).

Qualquer `.ksy` da galeria pode ser compilado e usado com este adaptador do
mesmo jeito que os exemplos daqui.

## Galerias de exemplos

- [Galeria de formatos do Kaitai Struct](https://formats.kaitai.io) --
  centenas de descrições `.ksy` de formatos reais, por categoria, cada uma com
  seus leitores gerados e documentação.
- [kaitai_struct_formats](https://github.com/kaitai-io/kaitai_struct_formats)
  -- as mesmas descrições em código-fonte, no GitHub.
- [Web IDE do Kaitai Struct](https://ide.kaitai.io) -- experimente uma
  descrição com um arquivo, no navegador.

## Gerando as classes

Depois de editar um `.ksy`, gere de novo com o compilador do Kaitai Struct
(0.11, https://kaitai.io), a partir de `src/main/ksy`:

    kaitai-struct-compiler -t java --java-package info.oais.infomodel.structure.kaitai.generated --debug --outdir ../java point2d.ksy csv_points.ksy

O `--debug` é o que faz as classes geradas registrarem de onde cada campo foi
lido, o que este adaptador transforma em posições em bytes (veja abaixo). Sem
ele, todo o resto continua funcionando, só que sem posições.

## Como o adaptador funciona

- **Genérico, por reflexão.** `KaitaiReflectiveStructureNode` percorre qualquer
  classe gerada sem código específico do formato, usando as convenções do alvo
  Java do Kaitai: cada campo é um acessor público sem argumentos com o nome do
  campo em camelCase, sem o prefixo `get` (`label_len` vira `labelLen()`), um
  tipo aninhado é outro `KaitaiStruct`, e um campo `repeat` é uma `List`. Os
  acessores de controle do próprio Kaitai (`_io`, `_parent`, `_root`, `_read`,
  ...) são pulados. Os filhos vêm na **ordem do arquivo**: da lista
  `_seqFields` de uma classe `--debug`, senão da ordem dos campos declarados da
  classe (a reflexão do Java não garante a ordem dos métodos).
- **Bytes que sobram.** O atributo `trailingBytes` do nó raiz
  (`StructureNode.TRAILING_BYTES`) diz quantos bytes a leitura não alcançou (a
  posição final do fluxo comparada ao seu tamanho).
- **Valores tipados.** Os valores voltam nos tipos Java dos acessores gerados
  (`int`, `long`, `double`, `String`, `byte[]`, ...).
- **A repetição é um nó `ARRAY`.** Um campo `repeat` se torna um nó do tipo
  `ARRAY` cujos filhos se chamam `0`, `1`, ... (os adaptadores DFDL e DRB usam,
  em vez disso, irmãos com o mesmo nome). As visões de tabela escolhem essas
  linhas com `<rows select="array">`.
- **Posições em bytes das compilações `--debug`.** Uma classe compilada com
  `--debug` tem mapas públicos `_attrStart`/`_attrEnd` (a posição de início e
  de fim de cada campo, pelo nome do acessor) e `_arrStart`/`_arrEnd` (uma
  entrada por elemento de um campo repetido). O adaptador os lê, então cada
  campo - e cada elemento de um campo repetido - informa seu intervalo de
  bytes. Uma classe `--debug` também não lê no construtor;
  `KaitaiStructureRepInfo` percebe isso e chama `_read()` ele mesmo (chame-o
  você mesmo se construir uma classe dessas diretamente).
- **Tipos de switch.** Num campo `switch-on`, o nome do tipo do nó é o tipo
  concreto de fato lido, não o tipo comum declarado.
- **Em memória.** O Objeto Digital é lido por inteiro para um buffer de bytes
  antes da leitura.

## Regravando dados

`KaitaiStructureRepInfo` é uma `WritableStructureRepInfo`, para classes
compiladas com o modo de leitura e escrita do Kaitai Struct
(`kaitai-struct-compiler -w`, só Java e Python, 0.11 em diante); uma classe só
de leitura é recusada com essa explicação. `write(dataObject, changes)` lê os
dados, busca as instâncias lidas sob demanda, define os valores alterados pelos
setters gerados (os passos do caminho nomeiam os campos como no `.ksy`,
`sample_count`, ou como o acessor, `sampleCount`; `[n]` escolhe um elemento de
um campo repetido), faz cada objeto alterado se verificar (`_check()`, que
recusa, por exemplo, uma contagem de repetição que não corresponde mais à sua
lista, ou um texto que não cabe mais no seu tamanho) e grava tudo com `_write`.
O Kaitai grava num fluxo de tamanho fixo, e um elemento repetido ou dimensionado
até o fim dos dados precisa terminar exatamente onde o fluxo termina, então o
gravador começa do tamanho dos próprios dados e o ajusta pelo que o Kaitai
informa. Os bytes não usados dentro de um tipo de `size` declarado são gravados
como zeros.

## Limitações conhecidas

- As descrições não podem ser carregadas em tempo de execução: todo formato
  precisa ter sua classe gerada e compilada antes (o archive-manager faz as duas
  coisas sob demanda no seu teste com amostra, o que leva alguns segundos).
- O compilador do Kaitai Struct não renomeia campos cujos nomes são palavras
  reservadas do Java (`class`, `int`, `null`, ...), então o Java deles não
  compila; o editor do archive-manager recusa esses nomes quando o Kaitai é um
  alvo. Nomes que o YAML lê como booleanos ou nulos (`on`, `no`, `null`)
  precisam estar entre aspas num `.ksy`, como faz o gerador do archive-manager.
- A leitura é imediata e em memória; para arquivos muito grandes, uma variante
  de `KaitaiStructureRepInfo` com `RandomAccessFileKaitaiStream` seria a
  extensão natural.
