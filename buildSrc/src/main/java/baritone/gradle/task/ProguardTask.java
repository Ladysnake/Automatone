/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.gradle.task;

import baritone.gradle.util.Determinizer;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Brady
 * @since 10/11/2018
 */
public class ProguardTask extends Jar {

    protected static final String PROGUARD_ZIP = "proguard.zip";
    protected static final String PROGUARD_JAR = "proguard.jar";
    protected static final String PROGUARD_CONFIG_TEMPLATE = "scripts/proguard.pro";
    protected static final String PROGUARD_CONFIG_DEST = "template.pro";
    protected static final String PROGUARD_CONFIG = "api.pro";
    protected static final String PROGUARD_EXPORT_PATH = "proguard_out.jar";

    private final Path proguardOut;

    @Input
    private String url;

    @Input
    private String extract;

    private final RegularFileProperty input;
    private FileCollection classpath_;

    public ProguardTask() {
        super();
        input = getProject().getObjects().fileProperty();
        proguardOut = this.getTemporaryFile(ProguardTask.PROGUARD_EXPORT_PATH);
    }

    @TaskAction
    protected void exec() throws Exception {
        Path input = this.getInput().getAsFile().get().toPath();
        Path output = this.getArchiveFile().get().getAsFile().toPath();
        downloadProguard();
        extractProguard();
        generateConfigs(input);
        runProguard(getTemporaryFile(PROGUARD_CONFIG));
        Determinizer.determinize(this.proguardOut, output);
        cleanup();
    }

    protected Path getTemporaryFile(String file) {
        return Paths.get(new File(getTemporaryDir(), file).getAbsolutePath());
    }

    private void downloadProguard() throws Exception {
        Path proguardZip = getTemporaryFile(PROGUARD_ZIP);
        if (!Files.exists(proguardZip)) {
            write(new URL(this.url).openStream(), proguardZip);
        }
    }

    protected void write(InputStream stream, Path file) throws IOException {
        if (Files.exists(file)) {
            Files.delete(file);
        }
        Files.copy(stream, file);
    }

    private void extractProguard() throws Exception {
        Path proguardJar = getTemporaryFile(PROGUARD_JAR);
        if (!Files.exists(proguardJar)) {
            ZipFile zipFile = new ZipFile(getTemporaryFile(PROGUARD_ZIP).toFile());
            ZipEntry zipJarEntry = zipFile.getEntry(this.extract);
            write(zipFile.getInputStream(zipJarEntry), proguardJar);
            zipFile.close();
        }
    }

    private Path getProguardConfigFile() {
        return Paths.get(new File(this.getProject().getProjectDir(), ProguardTask.PROGUARD_CONFIG_TEMPLATE).getAbsolutePath());
    }

    private void generateConfigs(Path artifactPath) throws Exception {
        Files.copy(getProguardConfigFile(), getTemporaryFile(PROGUARD_CONFIG_DEST), REPLACE_EXISTING);

        // Setup the template that will be used to derive the API and Standalone configs
        List<String> template = Files.readAllLines(getTemporaryFile(PROGUARD_CONFIG_DEST));
        template.add(0, "-injars " + artifactPath.toString());
        template.add(1, "-outjars " + this.getTemporaryFile(PROGUARD_EXPORT_PATH));
        if (JavaVersion.current() == JavaVersion.VERSION_1_8) {
            template.add(2, "-libraryjars <java.home>/lib/rt.jar");
        } else {
            template.add(2, "-libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)");
            template.add(2, "-libraryjars <java.home>/jmods/java.desktop.jmod(!**.jar;!module-info.class)");
        }

        // Discover all of the libraries that we will need to acquire from gradle
        for (File f : classpath_.getFiles()) {
            template.add(2, "-libraryjars '" + f + "'");
        }

        // API config doesn't require any changes from the changes that we made to the template
        Files.write(getTemporaryFile(PROGUARD_CONFIG), template);
    }

    private void cleanup() {
        try {
            Files.delete(this.proguardOut);
        } catch (IOException ignored) {
        }
    }

    public String getUrl() {
        return url;
    }

    public String getExtract() {
        return extract;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setExtract(String extract) {
        this.extract = extract;
    }

    private void runProguard(Path config) throws Exception {
        // Delete the existing proguard output file. Proguard probably handles this already, but why not do it ourselves
        if (Files.exists(this.proguardOut)) {
            Files.delete(this.proguardOut);
        }

        Path proguardJar = getTemporaryFile(PROGUARD_JAR);
        Process p = new ProcessBuilder("java", "-jar", proguardJar.toString(), "@" + config.toString())
                .directory(getTemporaryFile("").toFile()) // Set the working directory to the temporary folder]
                .start();

        // We can't do output inherit process I/O with gradle for some reason and have it work, so we have to do this
        this.printOutputLog(p.getInputStream(), System.out);
        this.printOutputLog(p.getErrorStream(), System.err);

        // Halt the current thread until the process is complete, if the exit code isn't 0, throw an exception
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Proguard exited with code " + exitCode);
        }
    }

    private void printOutputLog(InputStream stream, PrintStream outerr) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outerr.println(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public ProguardTask classpath(FileCollection collection) {
        if (this.classpath_ == null) {
            this.classpath_ = collection;
        } else {
            this.classpath_ = this.classpath_.plus(collection);
        }

        return this;
    }

    @InputFile
    public RegularFileProperty getInput() {
        return input;
    }
}
