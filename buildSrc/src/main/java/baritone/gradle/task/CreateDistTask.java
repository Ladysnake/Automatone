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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Brady
 * @since 10/12/2018
 */
public class CreateDistTask extends DefaultTask {

    private static final MessageDigest SHA1_DIGEST;

    static {
        try {
            SHA1_DIGEST = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // haha no thanks
            throw new IllegalStateException(e);
        }
    }

    private final ConfigurableFileCollection files;

    public CreateDistTask() {
        files = getProject().getObjects().fileCollection();
    }

    @TaskAction
    protected void exec() throws Exception {
        // Define the distribution file paths
        List<Path> inputPaths = files.getFiles().stream().map(File::toPath).collect(Collectors.toList());
        List<String> shasums = new ArrayList<>();
        Path distDir = this.getProject().getProjectDir().toPath().resolve("dist");

        for (Path inputPath : inputPaths) {
            Path output = distDir.resolve(inputPath.getFileName());

            Files.createDirectories(output.getParent());

            Files.copy(inputPath, output, REPLACE_EXISTING);

            // Calculate all checksums and format them like "shasum"
            String sum = String.format("%s %s", sha1(output), output.getFileName());
            getProject().getLogger().info(sum);
            shasums.add(sum);
        }

        // Write the checksums to a file
        Files.write(distDir.resolve("checksums.txt"), shasums);
    }

    private static String getFileName(Path p) {
        return p.getFileName().toString();
    }

    public void input(Object... inputFiles) {
        this.files.from(inputFiles);
    }

    @InputFiles
    public ConfigurableFileCollection getFiles() {
        return files;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static synchronized String sha1(Path path) {
        try {
            return bytesToHex(SHA1_DIGEST.digest(Files.readAllBytes(path))).toLowerCase();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
