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

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.ICommand;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandNotFoundException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.command.manager.ICommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.BaseText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class HelpCommand extends Command {

    public HelpCommand() {
        super("help", "?");
    }

    @Override
    public void execute(ServerCommandSource source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        execute(source, label, args);
    }

    public void execute(ServerCommandSource source, String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny() || args.is(Integer.class)) {
            Paginator.paginate(
                    args, new Paginator<>(
                            source, ICommandManager.registry.descendingStream()
                                    .filter(command -> !command.hiddenFromHelp())
                                    .collect(Collectors.toList())
                    ),
                    () -> logDirect(source, "All Automatone commands (clickable):"),
                    command -> {
                        String names = String.join("/", command.getNames());
                        String name = command.getNames().get(0);
                        BaseText shortDescComponent = new LiteralText(" - " + command.getShortDesc());
                        shortDescComponent.setStyle(shortDescComponent.getStyle().withFormatting(Formatting.DARK_GRAY));
                        BaseText namesComponent = new LiteralText(names);
                        namesComponent.setStyle(namesComponent.getStyle().withFormatting(Formatting.WHITE));
                        BaseText hoverComponent = new LiteralText("");
                        hoverComponent.setStyle(hoverComponent.getStyle().withFormatting(Formatting.GRAY));
                        hoverComponent.append(namesComponent);
                        hoverComponent.append("\n" + command.getShortDesc());
                        hoverComponent.append("\n\nClick to view full help");
                        String clickCommand = FORCE_COMMAND_PREFIX + String.format("%s %s", label, command.getNames().get(0));
                        BaseText component = new LiteralText(name);
                        component.setStyle(component.getStyle().withFormatting(Formatting.GRAY));
                        component.append(shortDescComponent);
                        component.setStyle(component.getStyle()
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent))
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand)));
                        return component;
                    },
                    FORCE_COMMAND_PREFIX + label
            );
        } else {
            String commandName = args.getString().toLowerCase();
            ICommand command = ICommandManager.getCommand(commandName);
            if (command == null) {
                throw new CommandNotFoundException(commandName);
            }
            logDirect(source, String.format("%s - %s", String.join(" / ", command.getNames()), command.getShortDesc()));
            logDirect(source, "");
            command.getLongDesc().forEach(message -> logDirect(source, message));
            logDirect(source, "");
            BaseText returnComponent = new LiteralText("Click to return to the help menu");
            returnComponent.setStyle(returnComponent.getStyle().withClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    FORCE_COMMAND_PREFIX + label
            )));
            logDirect(source, returnComponent);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .addCommands()
                    .filterPrefix(args.getString())
                    .stream();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View all commands or help on specific ones";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Using this command, you can view detailed help information on how to use certain commands of Baritone.",
                "",
                "Usage:",
                "> help - Lists all commands and their short descriptions.",
                "> help <command> - Displays help information on a specific command."
        );
    }
}
