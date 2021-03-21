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

package baritone.command.manager;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.ICommand;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.command.manager.ICommandManager;
import baritone.api.command.registry.Registry;
import baritone.command.CommandUnhandledException;
import baritone.command.argument.ArgConsumer;
import baritone.command.argument.CommandArguments;
import baritone.command.defaults.DefaultCommands;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Pair;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * The default, internal implementation of {@link ICommandManager}
 *
 * @author Brady
 * @since 9/21/2019
 */
public class BaritoneCommandManager implements ICommandManager {

    static {
        DefaultCommands.controlCommands.registerCommands();
    }

    private final IBaritone baritone;

    public BaritoneCommandManager(Baritone baritone) {
        this.baritone = baritone;
    }

    @Override
    public IBaritone getBaritone() {
        return this.baritone;
    }

    @Override
    public Registry<ICommand> getRegistry() {
        return ICommandManager.registry;
    }

    @Override
    public boolean execute(ServerCommandSource source, String string) throws CommandException {
        return this.execute(source, expand(string));
    }

    @Override
    public boolean execute(ServerCommandSource source, Pair<String, List<ICommandArgument>> expanded) throws CommandException {
        ExecutionWrapper execution = this.from(expanded);
        if (execution != null) {
            execution.execute(source);
        }
        return execution != null;
    }

    @Override
    public Stream<String> tabComplete(Pair<String, List<ICommandArgument>> expanded) {
        ExecutionWrapper execution = this.from(expanded);
        return execution == null ? Stream.empty() : execution.tabComplete();
    }

    @Override
    public Stream<String> tabComplete(String prefix) {
        Pair<String, List<ICommandArgument>> pair = expand(prefix, true);
        String label = pair.getLeft();
        List<ICommandArgument> args = pair.getRight();
        if (args.isEmpty()) {
            return new TabCompleteHelper()
                    .addCommands()
                    .filterPrefix(label)
                    .stream();
        } else {
            return tabComplete(pair);
        }
    }

    private ExecutionWrapper from(Pair<String, List<ICommandArgument>> expanded) {
        String label = expanded.getLeft();
        ArgConsumer args = new ArgConsumer(this, expanded.getRight(), this.getBaritone());

        ICommand command = ICommandManager.getCommand(label);
        return command == null ? null : new ExecutionWrapper(baritone, command, label, args);
    }

    private static Pair<String, List<ICommandArgument>> expand(String string, boolean preserveEmptyLast) {
        String label = string.split("\\s", 2)[0];
        List<ICommandArgument> args = CommandArguments.from(string.substring(label.length()), preserveEmptyLast);
        return new Pair<>(label, args);
    }

    public static Pair<String, List<ICommandArgument>> expand(String string) {
        return expand(string, false);
    }

    private static final class ExecutionWrapper {
        private final IBaritone baritone;
        private final ICommand command;
        private final String label;
        private final ArgConsumer args;

        private ExecutionWrapper(IBaritone baritone, ICommand command, String label, ArgConsumer args) {
            this.baritone = baritone;
            this.command = command;
            this.label = label;
            this.args = args;
        }

        private void execute(ServerCommandSource source) throws CommandException {
            try {
                this.command.execute(source, this.label, this.args, baritone);
            } catch (Throwable t) {
                // Create a handleable exception, wrap if needed
                throw t instanceof CommandException
                        ? (CommandException) t
                        : new CommandUnhandledException("An unhandled exception occurred. " +
                        "The error is in your game's log, please report this at https://github.com/Ladysnake/Automatone/issues", t);
            }
        }

        private Stream<String> tabComplete() {
            try {
                return this.command.tabComplete(this.label, this.args).map(s -> {
                    Deque<ICommandArgument> confirmedArgs = new ArrayDeque<>(this.args.getConsumed());
                    confirmedArgs.removeLast();
                    return Stream.concat(Stream.of(this.label), confirmedArgs.stream().map(ICommandArgument::getValue)).collect(Collectors.joining(" ")) + " " + s;
                });
            } catch (Throwable t) {
                return Stream.empty();
            }
        }
    }
}
