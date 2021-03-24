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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeterminizingTask extends Jar {
    private final RegularFileProperty input;

    public DeterminizingTask() {
        super();
        this.input = this.getProject().getObjects().fileProperty();
    }

    @TaskAction
    protected void exec() throws IOException {
        Path input = this.getInput().getAsFile().get().toPath();
        Path output = this.getArchiveFile().get().getAsFile().toPath();
        if (Files.exists(output)) {
            Files.delete(output);
        }

        Determinizer.determinize(input, output);
    }

    @InputFile
    public RegularFileProperty getInput() {
        return input;
    }
}
