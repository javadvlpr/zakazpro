package com.mebelsex.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the command lines and module lists of the packaging driver. */
class InstallerBuilderCommandTest {

    @TempDir
    Path temp;

    private static final Path JPACKAGE = Path.of("C:/Program Files/Java/jdk-17/bin/jpackage.exe");
    private static final Path JLINK = Path.of("C:/Program Files/Java/jdk-17/bin/jlink.exe");

    @Test
    void jlinkCommandUsesTheJdk17CompressionFlag() {
        Path output = Path.of("C:/work/mebelsex/target/runtime");

        List<String> command = InstallerBuilder.jlinkCommand(JLINK, output, List.of("java.base", "java.sql"));

        assertEquals(JLINK.toString(), command.get(0));
        assertEquals("--add-modules", command.get(1));
        assertEquals("java.base,java.sql", command.get(2));
        assertTrue(command.contains("--strip-debug"));
        assertTrue(command.contains("--no-header-files"));
        assertTrue(command.contains("--no-man-pages"));
        assertTrue(command.contains("--compress=2"));
        assertEquals("--output", command.get(command.size() - 2));
        assertEquals(output.toString(), command.get(command.size() - 1));
    }

    @Test
    void appImageCommandHasOneJavaOptionsArgumentPerOption() {
        InstallerBuilder.AppImageSpec spec = new InstallerBuilder.AppImageSpec(
                Path.of("C:/work/target/dist"), Path.of("C:/work/target/installer"), "ZakazPro", "1.0.0",
                "ZakazPro", "Tavsif", Path.of("C:/work/installer/assets/zakazpro.ico"),
                Path.of("C:/work/target/runtime"), "mebelsex-1.0.0.jar", "com.mebelsex.Launcher",
                List.of("-Dfile.encoding=UTF-8", "-Xmx768m"));

        List<String> command = InstallerBuilder.appImageCommand(JPACKAGE, spec);

        assertEquals("app-image", argumentAfter(command, "--type"));
        assertEquals("ZakazPro", argumentAfter(command, "--name"));
        assertEquals("1.0.0", argumentAfter(command, "--app-version"));
        assertEquals("mebelsex-1.0.0.jar", argumentAfter(command, "--main-jar"));
        assertEquals("com.mebelsex.Launcher", argumentAfter(command, "--main-class"));
        assertEquals(spec.runtimeImage().toString(), argumentAfter(command, "--runtime-image"));
        assertEquals(spec.inputDir().toString(), argumentAfter(command, "--input"));
        assertEquals(spec.destDir().toString(), argumentAfter(command, "--dest"));
        assertEquals(List.of("-Dfile.encoding=UTF-8", "-Xmx768m"), argumentsAfter(command, "--java-options"));
        assertFalse(command.contains("--win-menu"), "the app-image step must not add Windows integration");
        assertTrue(command.stream().noneMatch(argument -> argument.contains("mebelsex.license")));
    }

    @Test
    void exeCommandIsPerUserWithMenuEntryAndShortcut() {
        InstallerBuilder.ExeSpec spec = new InstallerBuilder.ExeSpec(
                Path.of("C:/work/target/installer/ZakazPro"), Path.of("C:/work/target/installer"), "ZakazPro",
                "1.0.0", "ZakazPro", "Tavsif", "ZakazPro", "c3138c4d-c7aa-4b27-a524-eb2a1c59d38e");

        List<String> command = InstallerBuilder.exeCommand(JPACKAGE, spec);

        assertEquals("exe", argumentAfter(command, "--type"));
        assertEquals(spec.appImage().toString(), argumentAfter(command, "--app-image"));
        assertEquals("ZakazPro", argumentAfter(command, "--win-menu-group"));
        assertEquals(spec.upgradeUuid(), argumentAfter(command, "--win-upgrade-uuid"));
        assertTrue(command.contains("--win-per-user-install"));
        assertTrue(command.contains("--win-menu"));
        assertTrue(command.contains("--win-shortcut"));
        assertFalse(command.contains("--win-dir-chooser"));
    }

    @Test
    void pathsWithSpacesStayOneArgumentAndAreQuotedInTheLog() {
        Path directory = temp.resolve("One Drive").resolve("my project");
        List<String> command = InstallerBuilder.jlinkCommand(JLINK, directory, List.of("java.base"));

        assertEquals(directory.toString(), command.get(command.size() - 1));
        assertTrue(command.stream().anyMatch(argument -> argument.contains("my project")));

        String rendered = InstallerBuilder.renderCommand(command);
        assertTrue(rendered.contains("\"" + directory + "\""), rendered);
        assertTrue(rendered.startsWith("\"" + JLINK + "\""), rendered);
    }

    @Test
    void mergeModulesAddsTheBaseListSortedAndWithoutDuplicates() {
        List<String> merged = InstallerBuilder.mergeModules(
                List.of("java.sql", "java.base", "jdk.crypto.ec"), InstallerBuilder.BASE_MODULES);

        assertTrue(merged.containsAll(InstallerBuilder.BASE_MODULES));
        assertEquals(merged.size(), merged.stream().distinct().count());
        assertEquals(merged.stream().sorted().toList(), merged);
        // Every jdeps module above is already in the base list, so nothing extra is added.
        assertEquals(InstallerBuilder.BASE_MODULES.stream().sorted().toList(), merged);
        assertTrue(merged.contains("jdk.localedata"), "the Uzbek locale data must always be bundled");
    }

    @Test
    void mergeModulesKeepsJdepsOnlyModules() {
        List<String> merged = InstallerBuilder.mergeModules(List.of("jdk.jfr", "jdk.charsets"),
                InstallerBuilder.BASE_MODULES);

        assertTrue(merged.contains("jdk.charsets"));
        assertTrue(merged.contains("jdk.jfr"));
    }

    @Test
    void splitModulesParsesTheJdepsOutput() {
        assertEquals(List.of("java.base", "java.sql", "jdk.crypto.ec"),
                InstallerBuilder.splitModules("java.base,java.sql,jdk.crypto.ec\n"));
        assertEquals(List.of(), InstallerBuilder.splitModules("   "));
        assertEquals(List.of(), InstallerBuilder.splitModules(null));
        assertEquals(List.of("java.base"), InstallerBuilder.splitModules("java.base,java.base"));
    }

    @Test
    void readsTheVersionAndArtifactIdFromThePom() throws Exception {
        Path pom = temp.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>4.1.1</version>
                  </parent>
                  <groupId>com.mebelsex</groupId>
                  <artifactId>mebelsex</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        assertEquals("1.0.0", InstallerBuilder.readProjectVersion(pom));
        assertEquals("mebelsex", InstallerBuilder.readProjectArtifactId(pom));
    }

    @Test
    void theRealPomGivesTheVersionAndTheThinJarName() throws Exception {
        Path pom = Path.of("pom.xml");
        String version = InstallerBuilder.readProjectVersion(pom);
        String artifactId = InstallerBuilder.readProjectArtifactId(pom);

        assertEquals("1.0.0", version);
        assertEquals("mebelsex", artifactId);
        assertEquals("1.0.0", InstallerVersion.normalizeInstallerVersion(version));
    }

    @Test
    void optionsAreParsedAndUnknownOnesRejected() {
        assertEquals(new InstallerBuilder.Options(false, false, false, false),
                InstallerBuilder.Options.parse(new String[0]));
        assertTrue(InstallerBuilder.Options.parse(new String[] { "--skip-exe" }).skipExe());
        assertTrue(InstallerBuilder.Options.parse(new String[] { "--keep-temp" }).keepTemp());
        assertTrue(InstallerBuilder.Options.parse(new String[] { "--verbose" }).verbose());
        assertEquals(new InstallerBuilder.Options(true, true, false, false),
                InstallerBuilder.Options.parse(new String[] { "--skip-exe", "--keep-temp" }));
        assertThrows(IllegalArgumentException.class, () -> InstallerBuilder.Options.parse(new String[] { "--nope" }));
    }

    private static String argumentAfter(List<String> command, String option) {
        List<String> values = argumentsAfter(command, option);
        assertEquals(1, values.size(), "expected exactly one " + option);
        return values.get(0);
    }

    private static List<String> argumentsAfter(List<String> command, String option) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (int i = 0; i < command.size() - 1; i++) {
            if (option.equals(command.get(i))) {
                values.add(command.get(i + 1));
            }
        }
        return values;
    }
}
