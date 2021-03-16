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

package baritone.command.defaults;

import baritone.Automatone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class VersionCommand extends Command {

    public VersionCommand() {
        super("version");
    }

    @Override
    public void execute(ServerCommandSource source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        args.requireMax(0);
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) {
            throw new CommandInvalidStateException("Null version (this is normal in a dev environment)");
        } else {
            logDirect(source, String.format("You are running Baritone v%s", version));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View Automatone's version";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The version command prints the version of Automatone you're currently running.",
                "",
                "Usage:",
                "> version - View version information, if present"
        );
    }
}
