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
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.command.ICommand;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandNotEnoughArgumentsException;
import baritone.api.command.manager.ICommandManager;
import baritone.command.argument.ArgConsumer;
import baritone.command.manager.BaritoneArgumentType;
import baritone.command.manager.BaritoneCommandManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.BaseText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public final class DefaultCommands {

    public static final ExecutionControlCommands controlCommands = new ExecutionControlCommands();
    public static final SelCommand selCommand = new SelCommand();
    public static final DynamicCommandExceptionType BARITONE_COMMAND_FAILED_EXCEPTION = new DynamicCommandExceptionType(Message.class::cast);

    public static void registerAll() {
        List<ICommand> commands = new ArrayList<>(Arrays.asList(
                new HelpCommand(),
                new SetCommand(),
                new CommandAlias(Arrays.asList("modified", "mod", "baritone", "modifiedsettings"), "List modified settings", "set modified"),
                new CommandAlias("reset", "Reset all settings or just one", "set reset"),
                new GoalCommand(),
                new GotoCommand(),
                new PathCommand(),
                new ProcCommand(),
                new ETACommand(),
                new VersionCommand(),
                new RepackCommand(),
                new BuildCommand(),
                new SchematicaCommand(),
                new ComeCommand(),
                new AxisCommand(),
                new ForceCancelCommand(),
                new GcCommand(),
                new InvertCommand(),
                new TunnelCommand(),
                new RenderCommand(),
                new FarmCommand(),
                new ChestsCommand(),
                new FollowCommand(),
                new ExploreFilterCommand(),
                new ReloadAllCommand(),
                new SaveAllCommand(),
                new ExploreCommand(),
                new BlacklistCommand(),
                new FindCommand(),
                new MineCommand(),
                new SurfaceCommand(),
                new ThisWayCommand(),
                new WaypointsCommand(),
                new CommandAlias("sethome", "Sets your home waypoint", "waypoints save home"),
                new CommandAlias("home", "Path to your home waypoint", "waypoints goto home"),
                selCommand
        ));
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            commands.add(new ClickCommand());
        }
        for (ICommand command : commands) {
            ICommandManager.registry.register(command);
        }
        CommandRegistrationCallback.EVENT.register(((dispatcher, dedicated) -> register(dispatcher)));
    }

    private static void logRanCommand(ServerCommandSource source, String command, String rest) {
        if (BaritoneAPI.getSettings().echoCommands.value) {
            String msg = command + rest;
            String toDisplay = BaritoneAPI.getSettings().censorRanCommands.value ? command + " ..." : msg;
            BaseText component = new LiteralText(String.format("> %s", toDisplay));
            component.setStyle(component.getStyle()
                    .withFormatting(Formatting.WHITE)
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new LiteralText("Click to rerun command")
                    ))
                    .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            FORCE_COMMAND_PREFIX + msg
                    )));
            source.sendFeedback(component, false);
        }
    }

    public static boolean runCommand(ServerCommandSource source, String msg, IBaritone baritone) throws CommandException {
        if (msg.trim().equalsIgnoreCase("damn")) {
            source.sendFeedback(new LiteralText("daniel"), false);
            return false;
        } else if (msg.trim().equalsIgnoreCase("orderpizza")) {
            Automatone.LOGGER.fatal("No pizza :(");
            return false;
        }
        if (msg.isEmpty()) {
            return runCommand(source, "help", baritone);
        }
        Pair<String, List<ICommandArgument>> pair = BaritoneCommandManager.expand(msg);
        String command = pair.getLeft();
        String rest = msg.substring(pair.getLeft().length());
        ArgConsumer argc = new ArgConsumer(baritone.getCommandManager(), pair.getRight(), baritone);
        if (!argc.hasAny()) {
            Settings.Setting<?> setting = BaritoneAPI.getSettings().byLowerName.get(command.toLowerCase(Locale.ROOT));
            if (setting != null) {
                logRanCommand(source, command, rest);
                if (setting.getValueClass() == Boolean.class) {
                    baritone.getCommandManager().execute(source, String.format("set toggle %s", setting.getName()));
                } else {
                    baritone.getCommandManager().execute(source, String.format("set %s", setting.getName()));
                }
                return true;
            }
        } else if (argc.hasExactlyOne()) {
            for (Settings.Setting<?> setting : BaritoneAPI.getSettings().allSettings) {
                if (setting.getName().equals("logger")) {
                    continue;
                }
                if (setting.getName().equalsIgnoreCase(pair.getLeft())) {
                    logRanCommand(source, command, rest);
                    try {
                        baritone.getCommandManager().execute(source, String.format("set %s %s", setting.getName(), argc.getString()));
                    } catch (CommandNotEnoughArgumentsException ignored) {
                    } // The operation is safe
                    return true;
                }
            }
        }

        // If the command exists, then handle echoing the input
        if (ICommandManager.getCommand(pair.getLeft()) != null) {
            logRanCommand(source, command, rest);
        }

        return baritone.getCommandManager().execute(source, pair);
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("automatone")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.argument("command", BaritoneArgumentType.baritone()).executes(command ->
                        runCommand(command.getSource(), command.getSource().getEntityOrThrow(), BaritoneArgumentType.getCommand(command, "command"))))
        );
    }

    private static int runCommand(ServerCommandSource source, Entity target, String command) throws CommandSyntaxException {
        if (!(target instanceof LivingEntity)) throw EntityArgumentType.ENTITY_NOT_FOUND_EXCEPTION.create();
        try {
            return runCommand(source, command, BaritoneAPI.getProvider().getBaritone((LivingEntity) target)) ? Command.SINGLE_SUCCESS : 0;
        } catch (baritone.api.command.exception.CommandException e) {
            throw BARITONE_COMMAND_FAILED_EXCEPTION.create(e.handle());
        }
    }
}
