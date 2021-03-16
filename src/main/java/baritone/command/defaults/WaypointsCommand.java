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
import baritone.api.cache.IWaypoint;
import baritone.api.cache.Waypoint;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.api.command.Command;
import baritone.api.command.datatypes.ForWaypoints;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class WaypointsCommand extends Command {

    public WaypointsCommand() {
        super("waypoints", "waypoint", "wp");
    }

    @Override
    public void execute(ServerCommandSource source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        Action action = args.hasAny() ? Action.getByName(args.getString()) : Action.LIST;
        if (action == null) {
            throw new CommandInvalidTypeException(args.consumed(), "an action");
        }
        BiFunction<IWaypoint, Action, Text> toComponent = (waypoint, _action) -> {
            BaseText component = new LiteralText("");
            BaseText tagComponent = new LiteralText(waypoint.getTag().name() + " ");
            tagComponent.setStyle(tagComponent.getStyle().withFormatting(Formatting.GRAY));
            String name = waypoint.getName();
            BaseText nameComponent = new LiteralText(!name.isEmpty() ? name : "<empty>");
            nameComponent.setStyle(nameComponent.getStyle().withFormatting(!name.isEmpty() ? Formatting.GRAY : Formatting.DARK_GRAY));
            BaseText timestamp = new LiteralText(" @ " + new Date(waypoint.getCreationTimestamp()));
            timestamp.setStyle(timestamp.getStyle().withFormatting(Formatting.DARK_GRAY));
            component.append(tagComponent);
            component.append(nameComponent);
            component.append(timestamp);
            component.setStyle(component.getStyle()
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new LiteralText("Click to select")
                    ))
                    .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s %s %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    _action.names[0],
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            ))
                    ));
            return component;
        };
        Function<IWaypoint, Text> transform = waypoint ->
                toComponent.apply(waypoint, action == Action.LIST ? Action.INFO : action);
        if (action == Action.LIST) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag != null) {
                args.get();
            }
            IWaypoint[] waypoints = tag != null
                    ? ForWaypoints.getWaypointsByTag(baritone, tag)
                    : ForWaypoints.getWaypoints(baritone);
            if (waypoints.length > 0) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> logDirect(
                                source, tag != null
                                        ? String.format("All waypoints by tag %s:", tag.name())
                                        : "All waypoints:"
                        ),
                        transform,
                        String.format(
                                "%s%s %s%s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names[0],
                                tag != null ? " " + tag.getName() : ""
                        ), source
                );
            } else {
                args.requireMax(0);
                throw new CommandInvalidStateException(
                        tag != null
                                ? "No waypoints found by that tag"
                                : "No waypoints found"
                );
            }
        } else if (action == Action.SAVE) {
            IWaypoint.Tag tag = IWaypoint.Tag.getByName(args.getString());
            if (tag == null) {
                throw new CommandInvalidStateException(String.format("'%s' is not a tag ", args.consumedString()));
            }
            String name = args.hasAny() ? args.getString() : "";
            BetterBlockPos pos = args.hasAny()
                    ? args.getDatatypePost(RelativeBlockPos.INSTANCE, baritone.getPlayerContext().feetPos())
                    : baritone.getPlayerContext().feetPos();
            args.requireMax(0);
            IWaypoint waypoint = new Waypoint(name, tag, pos);
            ForWaypoints.waypoints(baritone).addWaypoint(waypoint);
            BaseText component = new LiteralText("Waypoint added: ");
            component.setStyle(component.getStyle().withFormatting(Formatting.GRAY));
            component.append(toComponent.apply(waypoint, Action.INFO));
            logDirect(source, component);
        } else if (action == Action.CLEAR) {
            args.requireMax(1);
            IWaypoint.Tag tag = IWaypoint.Tag.getByName(args.getString());
            IWaypoint[] waypoints = ForWaypoints.getWaypointsByTag(baritone, tag);
            for (IWaypoint waypoint : waypoints) {
                ForWaypoints.waypoints(baritone).removeWaypoint(waypoint);
            }
            logDirect(source, String.format("Cleared %d waypoints", waypoints.length));
        } else {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint = null;
            if (args.hasAny() && args.peekString().equals("@")) {
                args.requireExactly(2);
                args.get();
                long timestamp = args.getAs(Long.class);
                for (IWaypoint iWaypoint : waypoints) {
                    if (iWaypoint.getCreationTimestamp() == timestamp) {
                        waypoint = iWaypoint;
                        break;
                    }
                }
                if (waypoint == null) {
                    throw new CommandInvalidStateException("Timestamp was specified but no waypoint was found");
                }
            } else {
                switch (waypoints.length) {
                    case 0:
                        throw new CommandInvalidStateException("No waypoints found");
                    case 1:
                        waypoint = waypoints[0];
                        break;
                    default:
                        break;
                }
            }
            if (waypoint == null) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> logDirect(source, "Multiple waypoints were found:"),
                        transform,
                        String.format(
                                "%s%s %s %s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names[0],
                                args.consumedString()
                        ), source
                );
            } else {
                if (action == Action.INFO) {
                    logDirect(source, transform.apply(waypoint));
                    logDirect(source, String.format("Position: %s", waypoint.getLocation()));
                    BaseText deleteComponent = new LiteralText("Click to delete this waypoint");
                    deleteComponent.setStyle(deleteComponent.getStyle().withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s delete %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            )
                    )));
                    BaseText goalComponent = new LiteralText("Click to set goal to this waypoint");
                    goalComponent.setStyle(goalComponent.getStyle().withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s goal %s @ %d",
                                    FORCE_COMMAND_PREFIX,
                                    label,
                                    waypoint.getTag().getName(),
                                    waypoint.getCreationTimestamp()
                            )
                    )));
                    BaseText backComponent = new LiteralText("Click to return to the waypoints list");
                    backComponent.setStyle(backComponent.getStyle().withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format(
                                    "%s%s list",
                                    FORCE_COMMAND_PREFIX,
                                    label
                            )
                    )));
                    logDirect(source, deleteComponent);
                    logDirect(source, goalComponent);
                    logDirect(source, backComponent);
                } else if (action == Action.DELETE) {
                    ForWaypoints.waypoints(baritone).removeWaypoint(waypoint);
                    logDirect(source, "That waypoint has successfully been deleted");
                } else if (action == Action.GOAL) {
                    Goal goal = new GoalBlock(waypoint.getLocation());
                    baritone.getCustomGoalProcess().setGoal(goal);
                    logDirect(source, String.format("Goal: %s", goal));
                } else if (action == Action.GOTO) {
                    Goal goal = new GoalBlock(waypoint.getLocation());
                    baritone.getCustomGoalProcess().setGoalAndPath(goal);
                    logDirect(source, String.format("Going to: %s", goal));
                }
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            if (args.hasExactlyOne()) {
                return new TabCompleteHelper()
                        .append(Action.getAllNames())
                        .sortAlphabetically()
                        .filterPrefix(args.getString())
                        .stream();
            } else {
                Action action = Action.getByName(args.getString());
                if (args.hasExactlyOne()) {
                    if (action == Action.LIST || action == Action.SAVE || action == Action.CLEAR) {
                        return new TabCompleteHelper()
                                .append(IWaypoint.Tag.getAllNames())
                                .sortAlphabetically()
                                .filterPrefix(args.getString())
                                .stream();
                    } else {
                        return args.tabCompleteDatatype(ForWaypoints.INSTANCE);
                    }
                } else if (args.has(3) && action == Action.SAVE) {
                    args.get();
                    args.get();
                    return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Manage waypoints";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The waypoint command allows you to manage Baritone's waypoints.",
                "",
                "Waypoints can be used to mark positions for later. Waypoints are each given a tag and an optional name.",
                "",
                "Note that the info, delete, and goal commands let you specify a waypoint by tag. If there is more than one waypoint with a certain tag, then they will let you select which waypoint you mean.",
                "",
                "Usage:",
                "> wp [l/list] - List all waypoints.",
                "> wp <s/save> <tag> - Save your current position as an unnamed waypoint with the specified tag.",
                "> wp <s/save> <tag> <name> - Save the waypoint with the specified name.",
                "> wp <s/save> <tag> <name> <pos> - Save the waypoint with the specified name and position.",
                "> wp <i/info/show> <tag> - Show info on a waypoint by tag.",
                "> wp <d/delete> <tag> - Delete a waypoint by tag.",
                "> wp <g/goal> <tag> - Set a goal to a waypoint by tag.",
                "> wp <goto> <tag> - Set a goal to a waypoint by tag and start pathing."
        );
    }

    private enum Action {
        LIST("list", "get", "l"),
        CLEAR("clear", "c"),
        SAVE("save", "s"),
        INFO("info", "show", "i"),
        DELETE("delete", "d"),
        GOAL("goal", "g"),
        GOTO("goto");
        private final String[] names;

        Action(String... names) {
            this.names = names;
        }

        public static Action getByName(String name) {
            for (Action action : Action.values()) {
                for (String alias : action.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (Action action : Action.values()) {
                names.addAll(Arrays.asList(action.names));
            }
            return names.toArray(new String[0]);
        }
    }
}
