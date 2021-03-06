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

package baritone.api.command.manager;

import baritone.api.IBaritone;
import baritone.api.command.ICommand;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandException;
import baritone.api.command.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Pair;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * @author Brady
 * @since 9/21/2019
 */
public interface ICommandManager {

    Registry<ICommand> registry = new Registry<>();

    /**
     * @param name The command name to search for.
     * @return The command, if found.
     */
    static ICommand getCommand(String name) {
        for (ICommand command : registry.entries) {
            if (command.getNames().contains(name.toLowerCase(Locale.ROOT))) {
                return command;
            }
        }
        return null;
    }

    IBaritone getBaritone();

    Registry<ICommand> getRegistry();

    boolean execute(ServerCommandSource source, String string) throws CommandException;

    boolean execute(ServerCommandSource source, Pair<String, List<ICommandArgument>> expanded) throws CommandException;

    Stream<String> tabComplete(Pair<String, List<ICommandArgument>> expanded);

    Stream<String> tabComplete(String prefix);
}
