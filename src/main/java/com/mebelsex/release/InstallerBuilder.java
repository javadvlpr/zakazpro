package com.mebelsex.release;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the Windows package for ZakazPro with the JDK's own tools (no extra library):
 * a jlink runtime image, a jpackage app-image, a headless smoke test of that app-image,
 * the release gate and - when WiX is installed - the {@code .exe} installer. It also writes
 * a portable ZIP of the app-image.
 *
 * <p>
 * Started from {@code installer\build-installer.cmd} after {@code mvn clean verify}, on the
 * classpath only ({@code java -cp target/classes ...}), because logback is not needed and
 * {@code clean} would delete a running class path.
 *
 * <p>
 * The command lines are built by pure methods ({@link #jlinkCommand}, {@link #appImageCommand},
 * {@link #exeCommand}, {@link #mergeModules}) so they are unit-tested without running
 * anything; the file-based release checks live in {@link ReleaseGate}.
 */
public final class InstallerBuilder {

    /**
     * Modules that are added to the jdeps result on purpose. {@code jdk.localedata} is what
     * makes the Uzbek month and day names work, {@code java.desktop} carries JavaFX's AWT
     * dependencies and {@code java.sql} the SQLite dialect.
     */
    public static final List<String> BASE_MODULES = List.of(
            "java.base", "java.sql", "java.naming", "java.management", "java.instrument", "java.desktop",
            "java.xml", "java.logging", "java.prefs", "java.security.jgss", "java.security.sasl",
            "java.transaction.xa", "jdk.unsupported", "jdk.crypto.ec", "jdk.zipfs", "jdk.localedata",
            "jdk.jfr");

    /** Modules the smoke test insists on in the bundled runtime. */
    public static final List<String> REQUIRED_RUNTIME_MODULES = List.of("java.base", "java.sql", "java.desktop",
            "jdk.localedata");

    private static final String MAIN_CLASS = "com.mebelsex.Launcher";
    private static final long SMOKE_TIMEOUT_SECONDS = 120L;
    private static final int DELETE_ATTEMPTS = 20;
    private static final long DELETE_PAUSE_MILLIS = 3000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path projectRoot;
    private final Options options;

    private InstallerConfig config;
    private String version;
    private Path distDir;
    private Path mainJar;
    private Path runtimeDir;
    private Path appImageDir;
    private Path installerDir;
    private Path iconFile;
    private Path jpackage;
    private Path jlink;
    private Path jdeps;

    private InstallerBuilder(Path projectRoot, Options options) {
        this.projectRoot = projectRoot;
        this.options = options;
    }

    public static void main(String[] args) {
        Options options = Options.parse(args);
        if (options.help()) {
            for (String line : usage()) {
                ConsoleOutput.line(line);
            }
            return;
        }
        int exitCode;
        try {
            exitCode = new InstallerBuilder(Paths.get("").toAbsolutePath(), options).run();
        } catch (Exception e) {
            ConsoleOutput.error("PACKAGING FAILED: " + e.getMessage());
            if (options.verbose()) {
                for (StackTraceElement element : e.getStackTrace()) {
                    ConsoleOutput.error("    at " + element);
                }
            }
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    /** Runs every step in order and returns the process exit code. */
    public int run() throws IOException, InterruptedException {
        ConsoleOutput.line("ZakazPro installer builder - " + stamp());
        config = loadConfig();
        version = InstallerVersion.normalizeInstallerVersion(readProjectVersion(projectRoot.resolve("pom.xml")));
        distDir = projectRoot.resolve("target").resolve("dist");
        installerDir = projectRoot.resolve("target").resolve("installer");
        appImageDir = installerDir.resolve(config.appName());
        runtimeDir = projectRoot.resolve("target").resolve("runtime");

        preconditions();
        prepareDist();
        List<String> modules = resolveModules();
        buildRuntime(modules);
        buildAppImage();
        smokeTest();
        ReleaseGate.Result gate = runGate();
        ConsoleOutput.line(gate.summary());
        if (!gate.ok()) {
            throw new IOException("the release gate failed - see the lines above");
        }
        buildExe();
        writePortableZip();
        ConsoleOutput.line("DONE. Version " + version + ", artifacts under " + installerDir);
        return 0;
    }

    // ------------------------------------------------------------------
    // steps
    // ------------------------------------------------------------------

    private InstallerConfig loadConfig() throws IOException {
        InstallerConfig loaded = InstallerConfig.load(projectRoot.resolve(InstallerConfig.DEFAULT_FILE));
        List<String> problems = loaded.problems();
        if (!problems.isEmpty()) {
            throw new IOException(InstallerConfig.DEFAULT_FILE + " is invalid: " + String.join("; ", problems));
        }
        return loaded;
    }

    private void preconditions() throws IOException {
        jpackage = findTool("jpackage");
        jlink = findTool("jlink");
        jdeps = findTool("jdeps");
        int feature = Runtime.version().feature();
        if (feature != 17) {
            throw new IOException("Java 17 is required to package ZakazPro (this JVM is " + feature + ")");
        }
        iconFile = projectRoot.resolve(config.iconPath());
        if (!Files.isRegularFile(iconFile)) {
            Path source = projectRoot.resolve(config.iconSource());
            ConsoleOutput.line("Icon missing, building it from " + source + " ...");
            IconBuilder.Result result = IconBuilder.build(source, iconFile, projectRoot.resolve("src/main/resources/icons"));
            for (String warning : result.warnings()) {
                ConsoleOutput.line("WARNING: " + warning);
            }
        }
        ConsoleOutput.line("Preconditions OK: Java " + feature + ", " + jpackage + ", icon " + iconFile);
    }

    /** Puts the thin application jar next to the runtime jars that copy-dependencies wrote. */
    private void prepareDist() throws IOException {
        String artifactId = readProjectArtifactId(projectRoot.resolve("pom.xml"));
        Path built = projectRoot.resolve("target").resolve(artifactId + "-" + version + ".jar");
        if (!Files.isRegularFile(built)) {
            throw new IOException("The application jar is missing: " + built
                    + " - run `mvn clean verify` first (installer\\build-installer.cmd does that)");
        }
        Files.createDirectories(distDir);
        mainJar = distDir.resolve(built.getFileName().toString());
        Files.copy(built, mainJar, StandardCopyOption.REPLACE_EXISTING);
        long libraries = countJars(distDir.resolve("lib"));
        if (libraries == 0) {
            throw new IOException("No runtime jars in " + distDir.resolve("lib")
                    + " - the copy-dependencies step did not run");
        }
        ConsoleOutput.line("Prepared " + mainJar.getFileName() + " with " + libraries + " runtime jars");
    }

    private List<String> resolveModules() throws IOException, InterruptedException {
        List<Path> jars = listJars(distDir.resolve("lib"));
        List<String> command = new ArrayList<>();
        command.add(jdeps.toString());
        command.add("--multi-release");
        command.add("17");
        command.add("--ignore-missing-deps");
        command.add("--print-module-deps");
        command.add("--class-path");
        List<String> classPath = new ArrayList<>();
        classPath.add(mainJar.toString());
        classPath.addAll(jars.stream().map(Path::toString).toList());
        command.add(String.join(java.io.File.pathSeparator, classPath));
        command.add(mainJar.toString());
        ConsoleOutput.line("Finding the JDK modules with jdeps ...");
        String output = capture(command);
        List<String> modules = mergeModules(splitModules(output), BASE_MODULES);
        ConsoleOutput.line("Runtime modules (" + modules.size() + "): " + String.join(",", modules));
        return modules;
    }

    private void buildRuntime(List<String> modules) throws IOException, InterruptedException {
        deleteRecursively(runtimeDir);
        List<String> command = jlinkCommand(jlink, runtimeDir, modules);
        ConsoleOutput.line("jlink: " + renderCommand(command));
        run(command, "jlink");
    }

    private void buildAppImage() throws IOException, InterruptedException {
        deleteRecursively(appImageDir);
        AppImageSpec spec = new AppImageSpec(distDir, installerDir, config.appName(), version, config.vendor(),
                config.description(), iconFile, runtimeDir, mainJar.getFileName().toString(), MAIN_CLASS,
                config.javaOptions());
        List<String> command = appImageCommand(jpackage, spec);
        ConsoleOutput.line("jpackage app-image: " + renderCommand(command));
        run(command, "jpackage app-image");
        if (!Files.isDirectory(appImageDir)) {
            throw new IOException("jpackage reported success but " + appImageDir + " does not exist");
        }
        ConsoleOutput.line("App-image ready: " + appImageDir);
    }

    private void smokeTest() throws IOException, InterruptedException {
        Path exe = appImageDir.resolve(config.appName() + ".exe");
        if (!Files.isRegularFile(exe)) {
            throw new IOException("No launcher in the app-image: " + exe);
        }
        Path smokeDir = projectRoot.resolve("target").resolve("smoke").resolve(stamp());
        Files.createDirectories(smokeDir);
        Path database = smokeDir.resolve("smoke.db");
        Path resultFile = SmokeTestGuard.resultFileFor(database);
        Path logFile = smokeDir.resolve("smoke-output.log");

        ProcessBuilder builder = new ProcessBuilder(exe.toString(), SmokeTestGuard.ARG);
        builder.environment().put(SmokeTestGuard.ENV_DB_PATH, database.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFile.toFile());
        ConsoleOutput.line("Smoke test: " + exe + " " + SmokeTestGuard.ARG);
        Process process = builder.start();
        if (!process.waitFor(SMOKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("the packaged application did not finish the smoke test within "
                    + SMOKE_TIMEOUT_SECONDS + " s");
        }
        int exit = process.exitValue();
        if (!Files.isRegularFile(resultFile)) {
            throw new IOException("the packaged application wrote no smoke-result.txt (exit " + exit
                    + "); launcher output: " + (Files.isRegularFile(logFile)
                            ? Files.readString(logFile, StandardCharsets.UTF_8) : "(none)"));
        }
        String result = Files.readString(resultFile, StandardCharsets.UTF_8);
        ConsoleOutput.line(result.trim());
        List<String> problems = new ArrayList<>();
        if (exit != 0) {
            problems.add("exit code " + exit);
        }
        for (String required : List.of("RESULT=OK", "file.encoding=UTF-8", "machine.id=MX-", "check.uzbek-text=OK|")) {
            if (!result.contains(required)) {
                problems.add("missing '" + required + "' in smoke-result.txt");
            }
        }
        if (!result.contains("uz.month=") || result.contains("uz.month=October")) {
            problems.add("the Uzbek month name is not available in the packaged runtime");
        }
        if (!problems.isEmpty()) {
            throw new IOException("smoke test failed: " + String.join("; ", problems));
        }
        ConsoleOutput.line("Smoke test PASSED (result file " + resultFile + ")");
        if (!options.keepTemp()) {
            deleteRecursively(smokeDir);
        }
    }

    private ReleaseGate.Result runGate() throws IOException, InterruptedException {
        ReleaseGate.Result gate = ReleaseGate.inspect(mainJar, appImageDir, iconFile, version, config);
        return gate.plus(runtimeModuleChecks());
    }

    private List<ReleaseGate.Finding> runtimeModuleChecks() throws IOException, InterruptedException {
        Path java = appImageDir.resolve("runtime").resolve("bin").resolve("java.exe");
        if (!Files.isRegularFile(java)) {
            return List.of(new ReleaseGate.Finding("runtime.modules", false,
                    "no java.exe in the bundled runtime: " + java));
        }
        String modules = capture(List.of(java.toString(), "--list-modules"));
        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_RUNTIME_MODULES) {
            if (!modules.contains(required + "@")) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            return List.of(new ReleaseGate.Finding("runtime.modules", false,
                    "missing from the bundled runtime: " + missing));
        }
        return List.of(new ReleaseGate.Finding("runtime.modules", true,
                "java.sql, java.desktop, jdk.localedata and java.base present"));
    }

    private void buildExe() throws IOException, InterruptedException {
        if (options.skipExe()) {
            ConsoleOutput.line("--skip-exe: no .exe installer was built.");
            return;
        }
        if (!wixAvailable()) {
            ConsoleOutput.line("WiX Toolset 3.x was not found, so only the app-image was built.");
            ConsoleOutput.line("Install WiX 3.14 (https://github.com/wixtoolset/wix3/releases) so that candle.exe");
            ConsoleOutput.line("and light.exe are on PATH, then run installer\\build-installer.cmd again.");
            ConsoleOutput.line("The app-image already passed the smoke test and the release gate.");
            return;
        }
        ExeSpec spec = new ExeSpec(appImageDir, installerDir, config.appName(), version, config.vendor(),
                config.description(), config.menuGroup(), config.upgradeUuid().trim());
        List<String> command = exeCommand(jpackage, spec);
        ConsoleOutput.line("jpackage exe: " + renderCommand(command));
        run(command, "jpackage exe");
        Path exe = installerDir.resolve(config.appName() + "-" + version + ".exe");
        if (!Files.isRegularFile(exe)) {
            List<Path> found = listFiles(installerDir, ".exe");
            exe = found.isEmpty() ? null : found.get(0);
        }
        if (exe == null) {
            throw new IOException("jpackage reported success but no installer .exe was found in " + installerDir);
        }
        ConsoleOutput.line("Installer: " + exe + " (" + (Files.size(exe) / (1024 * 1024)) + " MB)");
    }

    private void writePortableZip() throws IOException {
        Path zip = installerDir.resolve(config.appName() + "-" + version + "-portable.zip");
        Files.createDirectories(installerDir);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
            out.setLevel(Deflater.BEST_SPEED);
            try (Stream<Path> walk = Files.walk(appImageDir)) {
                for (Path path : walk.toList()) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }
                    String entryName = appImageDir.getParent().relativize(path).toString().replace('\\', '/');
                    out.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, out);
                    out.closeEntry();
                }
            }
        }
        ConsoleOutput.line("Portable ZIP: " + zip + " (" + (Files.size(zip) / (1024 * 1024)) + " MB)");
    }

    // ------------------------------------------------------------------
    // pure command construction (unit-tested)
    // ------------------------------------------------------------------

    /** {@code jlink} command for the runtime image. */
    public static List<String> jlinkCommand(Path jlink, Path outputDir, List<String> modules) {
        List<String> command = new ArrayList<>();
        command.add(jlink.toString());
        command.add("--add-modules");
        command.add(String.join(",", modules));
        command.add("--strip-debug");
        command.add("--no-header-files");
        command.add("--no-man-pages");
        command.add("--compress=2");
        command.add("--output");
        command.add(outputDir.toString());
        return List.copyOf(command);
    }

    /** Everything jpackage needs for the app-image. */
    public record AppImageSpec(Path inputDir, Path destDir, String name, String version, String vendor,
            String description, Path icon, Path runtimeImage, String mainJar, String mainClass,
            List<String> javaOptions) {
    }

    /** {@code jpackage --type app-image} command. */
    public static List<String> appImageCommand(Path jpackage, AppImageSpec spec) {
        List<String> command = new ArrayList<>();
        command.add(jpackage.toString());
        command.add("--type");
        command.add("app-image");
        command.add("--name");
        command.add(spec.name());
        command.add("--app-version");
        command.add(spec.version());
        command.add("--vendor");
        command.add(spec.vendor());
        command.add("--description");
        command.add(spec.description());
        command.add("--icon");
        command.add(spec.icon().toString());
        command.add("--input");
        command.add(spec.inputDir().toString());
        command.add("--main-jar");
        command.add(spec.mainJar());
        command.add("--main-class");
        command.add(spec.mainClass());
        command.add("--runtime-image");
        command.add(spec.runtimeImage().toString());
        for (String option : spec.javaOptions()) {
            command.add("--java-options");
            command.add(option);
        }
        command.add("--dest");
        command.add(spec.destDir().toString());
        return List.copyOf(command);
    }

    /** Everything jpackage needs for the per-user {@code .exe} installer. */
    public record ExeSpec(Path appImage, Path destDir, String name, String version, String vendor,
            String description, String menuGroup, String upgradeUuid) {
    }

    /** {@code jpackage --type exe} command. */
    public static List<String> exeCommand(Path jpackage, ExeSpec spec) {
        List<String> command = new ArrayList<>();
        command.add(jpackage.toString());
        command.add("--type");
        command.add("exe");
        command.add("--app-image");
        command.add(spec.appImage().toString());
        command.add("--name");
        command.add(spec.name());
        command.add("--app-version");
        command.add(spec.version());
        command.add("--win-per-user-install");
        command.add("--win-menu");
        command.add("--win-menu-group");
        command.add(spec.menuGroup());
        command.add("--win-shortcut");
        command.add("--win-upgrade-uuid");
        command.add(spec.upgradeUuid());
        command.add("--vendor");
        command.add(spec.vendor());
        command.add("--description");
        command.add(spec.description());
        command.add("--dest");
        command.add(spec.destDir().toString());
        return List.copyOf(command);
    }

    /** Adds the fixed base modules to the jdeps result, sorted and without duplicates. */
    public static List<String> mergeModules(List<String> jdepsModules, List<String> baseModules) {
        List<String> merged = new ArrayList<>();
        for (String module : jdepsModules) {
            addModule(merged, module);
        }
        for (String module : baseModules) {
            addModule(merged, module);
        }
        merged.sort(String::compareTo);
        return List.copyOf(merged);
    }

    private static void addModule(List<String> modules, String module) {
        if (module == null || module.isBlank()) {
            return;
        }
        String trimmed = module.trim();
        if (!modules.contains(trimmed)) {
            modules.add(trimmed);
        }
    }

    /** Parses the jdeps output ("java.base,java.sql,..." possibly with newlines). */
    public static List<String> splitModules(String jdepsOutput) {
        if (jdepsOutput == null || jdepsOutput.isBlank()) {
            return List.of();
        }
        List<String> modules = new ArrayList<>();
        for (String part : jdepsOutput.trim().split("[,\\s]+")) {
            addModule(modules, part);
        }
        return List.copyOf(modules);
    }

    /** Renders a command for the log, quoting arguments that contain spaces. */
    public static String renderCommand(List<String> command) {
        StringBuilder text = new StringBuilder();
        for (String argument : command) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(quote(argument));
        }
        return text.toString();
    }

    private static String quote(String argument) {
        if (argument.indexOf(' ') < 0 && argument.indexOf('"') < 0) {
            return argument;
        }
        return '"' + argument.replace("\"", "\\\"") + '"';
    }

    /** The project {@code <version>} (a direct child of {@code <project>}). */
    public static String readProjectVersion(Path pomFile) throws IOException {
        return readProjectValue(pomFile, "version");
    }

    /** The project {@code <artifactId>}. */
    public static String readProjectArtifactId(Path pomFile) throws IOException {
        return readProjectValue(pomFile, "artifactId");
    }

    private static String readProjectValue(Path pomFile, String tag) throws IOException {
        try (InputStream in = Files.newInputStream(pomFile)) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element element && tag.equals(element.getNodeName())) {
                    return element.getTextContent().trim();
                }
            }
        } catch (Exception e) {
            throw new IOException("Cannot read <" + tag + "> from " + pomFile + ": " + e.getMessage(), e);
        }
        throw new IOException("No <" + tag + "> in " + pomFile);
    }

    /** Command-line options of the driver. */
    public record Options(boolean skipExe, boolean keepTemp, boolean verbose, boolean help) {

        public static Options parse(String[] args) {
            boolean skipExe = false;
            boolean keepTemp = false;
            boolean verbose = false;
            boolean help = false;
            if (args != null) {
                for (String arg : args) {
                    switch (arg.toLowerCase(Locale.ROOT)) {
                        case "--skip-exe" -> skipExe = true;
                        case "--keep-temp" -> keepTemp = true;
                        case "--verbose" -> verbose = true;
                        case "--help", "-h", "/?" -> help = true;
                        default -> throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                }
            }
            return new Options(skipExe, keepTemp, verbose, help);
        }
    }

    public static List<String> usage() {
        return List.of(
                "Usage: InstallerBuilder [--skip-exe] [--keep-temp] [--verbose] [--help]",
                "  --skip-exe   build only the app-image (no WiX needed)",
                "  --keep-temp  keep the smoke-test folder under target/smoke",
                "  --verbose    print stack traces");
    }

    // ------------------------------------------------------------------
    // process helpers
    // ------------------------------------------------------------------

    private void run(List<String> command, String what) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.inheritIO();
        Process process = builder.start();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException(what + " failed with exit code " + exit + ": " + renderCommand(command));
        }
    }

    private String capture(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException(renderCommand(command) + " failed with exit code " + exit + ": " + output.trim());
        }
        return output;
    }

    private Path findTool(String name) throws IOException {
        String fileName = name + (isWindows() ? ".exe" : "");
        String home = System.getProperty("java.home");
        if (home != null) {
            Path candidate = Paths.get(home, "bin", fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return Paths.get(fileName);
    }

    private boolean wixAvailable() {
        return onPath("candle.exe") && onPath("light.exe");
    }

    private static boolean onPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String element : path.split(java.io.File.pathSeparator)) {
            if (!element.isBlank() && Files.isRegularFile(Paths.get(element.trim(), executable))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static long countJars(Path directory) throws IOException {
        return listJars(directory).size();
    }

    private static List<Path> listJars(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> listFiles(Path directory, String suffix) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(suffix)).sorted().toList();
        }
    }

    /**
     * Deletes a directory tree, retrying a few times: the files a previous run just wrote are
     * often locked for some seconds by Windows Defender or by a OneDrive sync, and a single
     * attempt would fail the whole build with a confusing file name.
     */
    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        IOException last = null;
        for (int attempt = 1; attempt <= DELETE_ATTEMPTS; attempt++) {
            try {
                deleteOnce(directory);
                return;
            } catch (IOException e) {
                last = e;
                pause(DELETE_PAUSE_MILLIS);
            }
        }
        throw new IOException("Cannot delete " + directory
                + " - another program holds a lock on it (OneDrive sync, antivirus scan or a"
                + " still running instance). Close it and try again. Last error: "
                + (last == null ? "unknown" : last.getMessage()));
    }

    private static void deleteOnce(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Timestamp used for the smoke-test folder name. */
    private static String stamp() {
        return LocalDateTime.now().format(STAMP);
    }
}
