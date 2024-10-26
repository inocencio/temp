package org.caixa.crelease.processors;

import org.apache.logging.log4j.util.Strings;
import org.caixa.crelease.utils.AppUtils;
import org.caixa.crelease.utils.CRelease;

import java.nio.file.Path;

public class ConverterPrc extends CRelease {

    public void convert(String source, String target) {
        title("Converter");

        boolean convertToBinary = false;
        String sourcePath = "";
        String targetPath = "";

        if (Strings.isEmpty(source)) {
            msg("Não foi informado o arquivo de origem a ser convertido.");
            return;
        }
        sourcePath = AppUtils.getJoinedPath(AppUtils.getUserDir(), source);

        if (Strings.isEmpty(target)) {
            if (AppUtils.checkExt(Path.of(sourcePath), ".txt")) {
                convertToBinary = true;
            }

            String fileName = AppUtils.getFilenameWithoutExt(source);
            target = fileName + (convertToBinary ? ".zip" : ".txt");
        }
        targetPath = AppUtils.getJoinedPath(AppUtils.getUserDir(), target);

        // verifica se o source é binário ou texto
        msg(ln(), "Convertendo...", ln());
        info("ORIGEM");
        msg(sourcePath);
        info("DESTINO");
        msg(targetPath);

        try {
            if (convertToBinary) {
                AppUtils.convertBase64ToBinary(AppUtils.readFile(sourcePath), targetPath);
            } else {
                String base64 = AppUtils.convertBinaryToBase64(sourcePath);
                AppUtils.saveFile(base64, targetPath);
            }
            msg(ln(), "Arquivo convertido com sucesso!");
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }
}

// -------------------------------------------------------------------------------

package org.caixa.crelease.utils;

import org.caixa.crelease.models.HashStates;
import org.caixa.crelease.models.Module;
import org.caixa.crelease.models.Setup;
import org.caixa.crelease.html.Structure;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppUtils {

  private static Setup setup;
  private static Map<String, String> filesContent = new HashMap<>();
  private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
  private static LocalTime startTime;
  private static LocalTime endTime;

  /**
   * Obtêm o caminho de um arquivo ou pasta com
   *
   * @param args - caminhos
   * @return o caminho absoluto a partir do user.home + os outros caminhos passados pelos argumentos.
   */
  public static String getInternalPath(String... args) {
    String sep = FileSystems.getDefault().getSeparator();
    String baseDir = System.getProperty("user.home");
    baseDir = String.join(sep, baseDir, ".crelease");

    Path path = Paths.get(baseDir, args);

    return path.toString();
  }

  public static String removeLastDir(String path) {
    Path p = Paths.get(path);
    return p.getParent().toString();
  }

  /**
   * Retorna o último elemento de um caminho que pode ser um arquivo ou um diretório.
   *
   * @param path - caminho a ser utilizado.
   * @return o último elemento de um caminho.
   */
  public static String getLastPathElement(String path) {
    Path p = Paths.get(path);
    return p.getFileName().toString();
  }

  public static String getUserDir() {
    return System.getProperty("user.dir");
  }

  /**
   * Obtêm uma instância de configuração de Setup.
   *
   * @return uma instância de setup.
   */
  public static Setup getSetup() {
    if (setup == null) {
      String xmlPath = getInternalPath("config", "setup.xml");
      setup = readXML(xmlPath, Setup.class);
    }

    return setup;
  }

  /**
   * Obtêm uma instância de Hash States.
   *
   * @return uma instância de hash setates.
   */
  public static HashStates getHashStates() {
    String xmlPath = AppUtils.getInternalPath("states", "states.xml");
    return AppUtils.readXML(xmlPath, HashStates.class);
  }

  public static boolean isEqualsValues(String value1, String value2) {
    if ((value1 == null && value2 != null) || (value1 != null && value2 == null)) {
      return false;
    }

    assert value1 != null;
    String v1 = value1.replace(System.lineSeparator(), "").trim();
    String v2 = value2.replace(System.lineSeparator(), "").trim();

    return v1.equals(v2);
  }

  /**
   * Verifica se o caminho informado representa um arquivo. Retorna falso se não existir ou se for um diretório.
   *
   * @param path - caminho do arquivo informado.
   * @return verdadeiro se o caminho informado é um arquivo.
   */
  public static boolean isPathFile(String path) {
    return Files.isRegularFile(Paths.get(path));
  }

  /**
   * Verifica se o caminho informado representa um diretório/pasta. Retorna falso se não existir ou se for um arquivo.
   *
   * @param path - caminho do diretório informado.
   * @return verdadeiro se o caminho informado é um diretório.
   */
  public static boolean isPathDir(String path) {
    return Files.isDirectory(Paths.get(path));
  }

  /**
   * Verifica se o caminho informado existe. Caso exista, ele pode ser um arquivo, um diretório ou um link simbólico.
   *
   * @param path
   * @return
   */
  public static boolean isPathExists(String path) {
    return Files.exists(Paths.get(path));
  }

  /**
   * Retorna verdadeiro se o local informado é um diretório do tipo git. Caso exista o caminho informado, mas não é do
   * tipo git, então retorna falso.
   *
   * @param path - caminho informado que se espera ter o diretório .git
   * @return verdadeiro se o caminho é um diretório git.
   */
  public static boolean isGitPath(String path) {
    File dir = new File(path, ".git");
    return dir.exists() && dir.isDirectory();
  }

  public static String getJoinedPath(Object... paths) {
    StringBuilder sb = new StringBuilder();
    String sep = FileSystems.getDefault().getSeparator();

    for (int i = 0; i < paths.length; i++) {
      Object path = paths[i];
      if (path instanceof String) {
        sb.append(path);
      } else if (path instanceof File) {
        sb.append(((File) path).getPath());
      }

      // apenda o separador apenas quando o caminho atual não é o último
      if (i < paths.length - 1) {
        sb.append(sep);
      }
    }

    return Paths.get(sb.toString()).normalize().toString();
  }

  /**
   * Obtêm o caminho do repositório a partir do caminho da release (os módulos de dentro do repositório são separados por
   * release) mais o caminho do módulo definido pelo setup.
   * Ex: /git-output/release/3.17.0/SICDT_JavaWeb_Core
   *
   * @param module  - módulo do caminho final.
   * @param release - release aonde o módulo desejado se encontra.
   * @return o caminho absoluto a partir do repositório.
   */
  public static String getFullRepositoryPath(String release, Module module) {
    return AppUtils.getJoinedPath(getSetup().getRepository(), release, getSetup().getModuleByName(module.getName()).getFolder());
  }

  /**
   * Obtêm o caminho do projeto do usuário a partir do módulo desejado.
   *
   * @param module - módulo do caminho final.
   * @return o caminho absoluto a partir do caminho do projeto do usuário.
   */
  public static String getFullUserPath(Module module) {
    return AppUtils.getJoinedPath(getSetup().getPath(), getSetup().getModuleByName(module.getName()).getFolder());
  }

  /**
   * Obtêm apenas os dígitos de uma string. "-", "." e "," serão também removidos.
   *
   * @param value uma string alfanumérica
   * @return valor formatado apenas como dígitos.
   */
  public static String getDigits(String value) {
    return value.replaceAll("\\D+", "");
  }

  public static void saveXML(String xmlPath, Class xmlClass, Object xmlObject) {
    File file = new File(xmlPath);

    try {
      JAXBContext ctx = JAXBContext.newInstance(xmlClass);
      Marshaller save = ctx.createMarshaller();
      save.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
      save.marshal(xmlObject, file);

    } catch (JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  public static void saveFile(String content, String filePath) throws IOException {
    try {
      Files.writeString(Path.of(filePath), content);
    } catch (IOException e) {
      throw new IOException(String.format("Não foi possível salvar o arquivo %s, Erro: %s", filePath, e.getMessage()));
    }
  }

  public static <T> T readXML(String xmlPath, Class<T> xmlClass) {
    try {
      File file = new File(xmlPath);
      JAXBContext ctx = JAXBContext.newInstance(xmlClass);

      Unmarshaller reader = ctx.createUnmarshaller();
      return xmlClass.cast(reader.unmarshal(file));
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  public static String readFile(String filePath) {
    List<String> charsets = Arrays.asList("UTF-8", "windows-1252", "ISO-8859-1");
    AtomicReference<String> content = new AtomicReference<>("");

    charsets.stream()
       .map(Charset::forName)
       .filter(cs -> {
         try {
           content.set(Files.readString(Path.of(filePath), cs));
           return true;
         } catch (IOException e) {
           return false;
         }
       })
       .findFirst()
       .orElseThrow(() -> new RuntimeException("Erro ao ler o arquivo: " + filePath));
    return content.toString();
  }

  public static String readBranchSQLFiles(String path, String branch, boolean refresh) {
    String branchToken = getDigits(branch);
    StringBuilder sb = new StringBuilder();
    List<Path> files = new ArrayList<>();
    Path dir = Paths.get(path);

    try {
      if (refresh || filesContent.isEmpty()) {
        filesContent.clear();
        Files.list(dir)
           .filter(file -> file.toString().toLowerCase().endsWith(".sql"))
           .forEach(file -> files.add(file.getFileName()));

        if (!files.isEmpty()) {
          for (Path file : files) {
            String content = readFile(getJoinedPath(path, file.getFileName().toString()));
            filesContent.put(file.getFileName().toString(), content);
          }
        }
      }

      if (!filesContent.isEmpty()) {
        for (String fileName : filesContent.keySet()) {
          String content = filesContent.get(fileName);
          if (content.contains(branchToken)) {
            sb.append(fileName).append("\n");
          }
        }
      }

      String output = sb.toString();
      if (output.endsWith("\n")) {
        output = output.substring(0, output.length() - 1);
      }

      return output;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String getShortHash(String hash) {
    if (hash == null || hash.trim().isEmpty()) {
      return "";
    }
    if (hash.length() >= 8) {
      return hash.substring(0, 8);
    }
    return hash;
  }

  public static String markTime() {
    // marca o primeiro tempo
    if (startTime == null && endTime == null) {
      startTime = LocalTime.now();
      return null;
    }

    // marca o segundo tempo e apresenta o resultado
    if (startTime != null && endTime == null) {
      endTime = LocalTime.now();
    }

    // calcula a duração
    Duration d = Duration.between(startTime, endTime);
    long hours = d.toHours();
    long minutes = d.toMinutesPart();
    long seconds = d.toSecondsPart();

    String duration = String.format("%02d:%02d:%02d", hours, minutes, seconds);

    startTime = null;
    endTime = null;

    return duration;
  }

  /**
   * Formata uma DateTime ou LocalTime para o padrão dd/MM/yyyy HH:mm:ss.
   *
   * @param time a ser convertido em string formatada.
   * @return uma string de data + hora formatada.
   */
  public static String formatDatetime(LocalDateTime time) {
    return time.format(dtf);
  }

  public static String shortHash(String hash) {
    if (hash != null && hash.length() >= 10) {
      return hash.substring(0, 10);
    }

    return hash;
  }

  /**
   * A partir de uma lista, retorna o tamanho máximo de posições para um valor informado.
   *
   * @param values é uma lista de strings.
   * @return o tamanho máximo.
   */
  public static <T> int findMax(List<T> values) {
    int max = 0;

    for (T value : values) {
      String stringValue = String.valueOf(value);
      if (stringValue.length() > max) {
        max = stringValue.length();
      }
    }

    return max;
  }

  /**
   * A partir de uma lista, retorna o tamanho máximo de posições para um valor informado.
   *
   * @param values é uma lista genérica
   * @param mapper é o campo em formato lambda a ser obtido que espera estar dentro da lista.
   * @return o tamanho máximo.
   * @param <T> uma lista de um T tipo fixo.
   * @param <R> um campo R que espera existir dentro da lista.
   */
  public static <T, R> int findMax(List<T> values, Function<? super T, ? extends R> mapper) {
    int max = 0;
    List<? extends R> mappedList = values.stream().map(mapper).toList();

    for (R r : mappedList) {
      String stringValue = String.valueOf(r);
      if (stringValue.length() > max) {
        max = stringValue.length();
      }
    }

    return max;
  }

  /**
   * Retorna o tamanho máximo em posições de um valor númerico.
   * Ex:
   * 1, retorna 1;
   * 33. retorna 2;
   * 3122, retorna 4;
   * e por aí vai.
   *
   * @param number que será pego o seu tamanho em posições.
   * @return quantidade de posições do número.
   */
  public static int findMax(int number) {
    return String.valueOf(number).length();
  }

  /**
   * Verifica se o arquivo termina com alguma das extensões permitidas.
   *
   * @param filePath nome de arquivo
   * @param allowedExts extensões permitidas
   * @return verdadeiro se o arquivo terminar com alguma das extensões permitidas.
   */
  public static boolean checkExt(Path filePath, String... allowedExts) {
    if (filePath == null) return false;

    return Arrays
       .stream(allowedExts)
       .anyMatch(ext -> filePath.getFileName().toString().toLowerCase().endsWith(ext));
  }

  /**
   * Obtêm o nome do arquivo sem a sua extensão.
   *
   * @param filename nome do arquivo com a extensão
   * @return nome do arquivo sem a extensão.
   */
  public static String getFilenameWithoutExt(String filename) {
    if (filename == null || !filename.contains(".")) return null;
    return filename.substring(0, filename.lastIndexOf("."));
  }

  /**
   * Verifica qual é a pasta de estrutura que o arquivo encontra-se.
   *
   * @param filePath é o arquivo utilizado.
   * @return a estrutura utilizada pelo aquivo que representa a pasta que ele se encontra.
   */
  public static Structure findStructure(Path filePath) {
    if (filePath == null) return null;

    for (Structure structure : Structure.values()) {
      String path = filePath.toFile().getAbsolutePath();

      if (path.toLowerCase().contains(structure.getFolder()))
        return structure;
    }

    return null;
  }

  /**
   * Procura todos os tokens que podem estar numa página que seguem um padrão determinado.
   *
   * @param pattern é o padrão usado para detectar toekns
   * @param input é o texto de entrada que contêm os tokens
   * @return uma lista de tokens
   */
  public static List<String> findTokens(String pattern, String input) {
    String regex = pattern + "[\\w\\-]+";

    Matcher matcher = Pattern.compile(regex).matcher(input);
    List<String> tokenList = new ArrayList<>();

    while (matcher.find()) {
      tokenList.add(matcher.group());
    }

    return tokenList;
  }

  /**
   * Gera um hash alfanumérico com o tamanho desejado.
   *
   * @param length - tamanho de caractéres do hash
   * @return o hash
   */
  public static String generateId(int length) {
    String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
       + "abcdefghijklmnopqrstuvwxyz"
       + "0123456789";

    Random random = new Random(System.nanoTime());
    StringBuilder id = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      int index = random.nextInt(CHARACTERS.length());
      id.append(CHARACTERS.charAt(index));
    }

    return id.toString();
  }

  /**
   * Converte um arquivo binário em formato texto base64.
   *
   * @param filePath caminho do arquivo binário.
   * @return conteúdo do arquivo binário convertido em base64.
   * @throws IOException se não for possível converter o arquivo.
   */
  public static String convertBinaryToBase64(String filePath) throws IOException {
    File file = new File(filePath);
    byte[] fileContent = new byte[(int) file.length()];

    try {
      FileInputStream fis = new FileInputStream(file);
      fis.read(fileContent);
      return Base64.getEncoder().encodeToString(fileContent);
    } catch(IOException e) {
      throw new IOException("Não foi possível ler o arquivo " + filePath + ", Erro: " + e.getMessage());
    }
  }

  /**
   * Converte texto em formato base64 para um arquivo binário.
   *
   * @param base64 é uma string em formato base64.
   * @param filePath o caminho do arquivo de saida.
   * @throws IOException se não for possível converter para o arquivo destino.
   */
  public static void convertBase64ToBinary(String base64, String filePath) throws IOException {
    byte[] decodedBytes = Base64.getDecoder().decode(base64);

    try {
      FileOutputStream fos = new FileOutputStream(filePath);
      fos.write(decodedBytes);
      fos.close();

    } catch(IOException e) {
      throw new IOException("Não foi possível converter o arquivo " + base64 + ", Erro: " + e.getMessage());
    }
  }

}

