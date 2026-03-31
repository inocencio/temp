package org.tecwizard.qw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;

public class Main {
    private String[] args;

    //~-------------------- MAIN --------------------//

    public Main(String[] args) {
        this.args = args;

        // pack
        if (contains(cmdPack)) {
            boolean isRawFiles = hasArg("--raw-files");
            removeAttribute("--raw-files");

            pack(findArg(1), findArg(2), isRawFiles);
            return;
        }

        // unpack
        if (contains(cmdUnpack)) {
            unpack(findArg(1), findArg(2));
        }
    }

    public static void main(String[] args) {
        new Main(args);
    }

    //~-------------------- COMMANDS --------------------//

    private void pack(final String sourceFolder, final String targetFile, boolean isRawFiles) {
        String source = "";
        String target ="";
        StringBuilder sb = new StringBuilder();

        if (".".equals(sourceFolder)) {
            source = getCurrentDir();
        } else if (!isAbsolutePath(sourceFolder)) {
            source = getJoinedPath(getCurrentDir(), sourceFolder);
        } else {
            source = sourceFolder;
        }

        if (targetFile == null) {
            target = getLastPath(source) + ".txt";
            target = getJoinedPath(getFolder(source, 1), target);
        } else if (!isAbsolutePath(targetFile)) {
            target = getJoinedPath(getFolder(source, 1), targetFile);
        }

        System.out.printf("Packing:  %s%n", source);
        System.out.printf("To:       %s%n", target);
        System.out.printf("Mode:     %s%n%n", isRawFiles ? "raw" : "base64" );

        try {
            final Path originPath = Path.of(source);
            final String name = originPath.getFileName().toString();
            final boolean hasRawFiles = isRawFiles;
            final String hash = generateId(10);
            final String delim = hash + "=".repeat(60);
            final String bl = System.lineSeparator();
            final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            List<Structure> filesList = Files.walk(originPath)
               .filter(Files::isRegularFile)
               .map(path -> toStructure(path, originPath, hasRawFiles))
               .toList();

            sb.append("@@name=").append(name).append(bl);
            sb.append("@@format=").append(isRawFiles ? "raw" : "base64").append(bl);
            sb.append("@@hash=").append(hash).append(bl);
            sb.append("@@date=").append(sdf.format(System.currentTimeMillis())).append(bl);
            sb.append(bl);

            for (Structure structure : filesList) {
                String str = "[%s:%s]%n%s%n%s%n%s%n%n";
                sb.append(String.format(str, structure.name, structure.getRelativePath(), delim, structure.content, delim));
            }

            Files.writeString(Path.of(target), sb.toString());
            System.out.println("It was packed successfully.");
        } catch (IOException e) {
            System.out.println("It was unable to pack. Error: " + e.getMessage());
        }
    }

    private void unpack(final String sourceFile, final String targetFolder) {
        String source = "";
        String target ="";
        StringBuilder sb = new StringBuilder();

        if (isAbsolutePath(sourceFile)) {
            source = sourceFile;
        } else {
            source = getJoinedPath(getCurrentDir(), sourceFile);
        }

        if (!isPathFile(source)) {
            System.out.printf("'%s' is not a file!%n", sourceFile);
            return;
        }

        if (targetFolder == null) {
            target = getJoinedPath(getCurrentDir(), getFileNameWithoutExt(source));
        } else {
            if (!isAbsolutePath(targetFolder)) {
                target = getJoinedPath(getCurrentDir(), targetFolder);
            } else {
                target = targetFolder;
            }
        }

        if (isPathExists(target)) {
            System.out.printf("'%s' already exists!%n", target);
            return;
        }

        System.out.printf("Unpacking:  %s%n", source);
        System.out.printf("To:         %s%n%n", target);

        try {
            boolean isRawFiles = false;
            boolean isReading = false;

            String delim = "";
            Path filePath = null;
            StringBuilder content = new StringBuilder();

            String file = Files.readString(Path.of(source), StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(new StringReader(file));
            String line;

            while ((line = reader.readLine()) != null) {

                // entries
                if (!isReading && line.startsWith("@@")) {
                    String[] parts = line.split("=");
                    String key = parts[0].substring(2);
                    String value = parts[1];

                    if ("format".equals(key)) {
                        isRawFiles = "raw".equals(value.trim());
                        System.out.printf("format:     %s%n%n", value);
                        continue;
                    }
                    if ("hash".equals(key)) {
                        delim = value + "=".repeat(60);
                        continue;
                    }
                }

                // getting file path
                if (!isReading && (line.startsWith("[") && line.endsWith("]"))) {
                    String[] parts = line.substring(1, line.length() - 1).split(":");
                    filePath = Path.of(getJoinedPath(target, parts[1]));
                    continue;
                }

                // start reading...
                if (!isReading && line.equals(delim)) {
                    isReading = true;
                    continue;
                }

                // reading...
                if (isReading && !line.equals(delim)) {
                    content.append(line);
                    if (isRawFiles) {
                        content.append(System.lineSeparator());
                    }
                    continue;
                }

                // finishing and save file
                if (isReading && line.equals(delim)) {
                    System.out.printf("Creating %s...%n", filePath);
                    Files.createDirectories(filePath.getParent());

                    if (isRawFiles) {
                        Files.writeString(filePath, content.toString());
                    } else {
                        convertBase64ToFile(content.toString(), filePath.toString());
                    }
                    content = new StringBuilder();
                    isReading = false;
                    filePath = null;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //~-------------------- UTILS --------------------//

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

    public static boolean isAbsolutePath(String path) {
        return Paths.get(path).isAbsolute();
    }

    public static String getLastPath(String path) {
        return Paths.get(path).getFileName().toString();
    }

    public static String getCurrentDir() {
        return System.getProperty("user.dir");
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

            // apenda o separador apenas quando o caminho atual não for o último
            if (i < paths.length - 1) {
                sb.append(sep);
            }
        }

        return Paths.get(sb.toString()).normalize().toString();
    }

    public static String getFolder(String path, int levelsUp) {
        // cria um objeto Path a partir da string do caminho
        Path p = Paths.get(path);

        // sobe o número de níveis necessários no caminho
        for (int i = 0; i < levelsUp; i++) {
            // obtém o diretório pai
            p = p.getParent();
            if (p == null) {
                // caso não haja mais níveis para subir, retorna uma string vazia ou null
                return "";
            }
        }

        return p.toString();
    }

    /**
     * Obtêm o nome do arquivo sem a extensão. Se o arquivo não tem extensão, retorna o nome.
     *
     * @param filePath nome do arquivo
     * @return o nome do arquivo sem extensão
     */
    public static String getFileNameWithoutExt(String filePath) {
        String name = getLastPath(filePath);
        int index = name.lastIndexOf(".");
        return index == -1 ? name : name.substring(0, index);
    }

    public static void convertBase64ToFile(String content, String filePath) throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(content);

        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            fos.write(decodedBytes);
            fos.close();

        } catch(IOException e) {
            throw new IOException("Não foi possível converter o arquivo " + filePath + ", Erro: " + e.getMessage());
        }
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

    //~-------------------- ARGUMENTS --------------------//

    private boolean hasArgs() {
        return args != null && args.length > 0;
    }

    /**
     * Verifica se há um argumento específico na linha de argumentos.
     *
     * @param argument - argumento desejado.
     * @return verdadeiro se o argumento foi encontrado, o case é ignorado.
     */
    private boolean hasArg(String argument) {
        if (!hasArgs()) return false;
        return Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase(argument));
    }

    /**
     * Obtêm um argumento a partir de sua posição (1, 2, 3...). A posição tem que ser diferente de zero, já
     * que a posição zero é sempre o comando de execução. Se não encontrar, ou a posição informada é superior a quantidade
     * de argumentos, retorna, portanto, null.
     *
     * @param pos  - posição esperada do argumento acima de zero.
     * @return o valor do argumento desejado.
     */
    private String findArg(int pos) {
        if (!hasArgs()) return null;
        return pos < args.length ? args[pos] : null;
    }

    private void removeAttribute(final String attribute) {
        if (!hasArgs()) return;

        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        List<Integer> idxs = new ArrayList<>();

        for (int i = 0; i < argsList.size(); i++) {
            String arg = argsList.get(i);
            if (arg.equalsIgnoreCase(attribute)) {
                idxs.add(i);
            }
        }

        if (idxs.isEmpty()) return;

        for (int i = idxs.size() - 1; i >= 0; i--) {
            argsList.remove((int) idxs.get(i));
        }
        args = argsList.toArray(new String[0]);
    }

    /**
     * Verifica se o argumento é válido e se encaixa com a definição criada.
     *
     * @param argument instância de Argument
     * @return verdadeiro se as condições atendem.
     */
    private boolean contains(Argument argument) {
        boolean hasMin = args.length - 1 >= argument.getMinArg();
        boolean hasMax = args.length - 1 <= argument.getMaxArg();
        boolean hasCommand = argument.getCommands() != null && argument.getCommands().length > 0;

        if (!(hasCommand && hasMin && hasMax)) {
            return false;
        }

        String cmd = args[0];
        for (String command : argument.getCommands()) {
            if (cmd.equalsIgnoreCase(command)) {
                return true;
            }
        }

        return false;
    }

    private interface Argument {
        String[] getCommands();
        int getMinArg();
        int getMaxArg();
    }

    /**
     * Pack
     */
    private Argument cmdPack = new Argument() {

        @Override
        public String[] getCommands() {
            return new String[]{"pack", "p"};
        }

        @Override
        public int getMinArg() {
            return 1;
        }

        @Override
        public int getMaxArg() {
            return 3;
        }
    };

    /**
     * Pack
     */
    private Argument cmdUnpack = new Argument() {

        @Override
        public String[] getCommands() {
            return new String[]{"unpack", "up"};
        }

        @Override
        public int getMinArg() {
            return 1;
        }

        @Override
        public int getMaxArg() {
            return 2;
        }
    };

    //~-------------------- STRUCTURE --------------------//

    public static class Structure {
        private String name;
        private Path path;
        private Path relativePath;
        private String content;

        public Structure() {}

        public Structure(String name, Path path) {}

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Path getPath() {
            return path;
        }

        public void setPath(Path path) {
            this.path = path;
        }

        public Path getRelativePath() {
            return relativePath;
        }

        public void setRelativePath(Path relativePath) {
            this.relativePath = relativePath;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    private static Structure toStructure(Path path, Path originPath, boolean hasRawFiles) {
        Structure structure = new Structure();
        structure.setName(path.getFileName().toString());
        structure.setPath(path);
        structure.setRelativePath(originPath.relativize(path));

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);

            if (hasRawFiles) {
                structure.setContent(content);
            } else {
                structure.setContent(Base64.getEncoder().encodeToString(content.getBytes()));
            }
        } catch (IOException e) {
            System.out.println("It was unable to read file: " + path);
            e.printStackTrace();
        }
        return structure;
    }
}
