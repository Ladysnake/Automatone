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

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.manager.ICommandManager;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Contains the pause, resume, and paused commands.
 * <p>
 * This thing is scoped to hell, private so far you can't even access it using reflection, because you AREN'T SUPPOSED
 * TO USE THIS to pause and resume Baritone. Make your own process that returns {@link PathingCommandType#REQUEST_PAUSE
 * REQUEST_PAUSE} as needed.
 */
public class ExecutionControlCommands {

    private final Command pauseCommand;
    private final Command resumeCommand;
    private final Command pausedCommand;
    private final Command cancelCommand;

    public ExecutionControlCommands() {
        pauseCommand = new Command("pause", "p") {
            @Override
            public void execute(String label, IArgConsumer args, IBaritone baritone) throws CommandException {
                args.requireMax(0);
                ExecControlProcess controlProcess = (ExecControlProcess) ((Baritone) baritone).getExecControlProcess();
                if (controlProcess.paused) {
                    throw new CommandInvalidStateException("Already paused");
                }
                controlProcess.paused = true;
                logDirect("Paused");
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return "Pauses Baritone until you use resume";
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        "The pause command tells Baritone to temporarily stop whatever it's doing.",
                        "",
                        "This can be used to pause pathing, building, following, whatever. A single use of the resume command will start it right back up again!",
                        "",
                        "Usage:",
                        "> pause"
                );
            }
        };
        resumeCommand = new Command("resume", "r") {
            @Override
            public void execute(String label, IArgConsumer args, IBaritone baritone) throws CommandException {
                args.requireMax(0);
                baritone.getBuilderProcess().resume();
                ExecControlProcess controlProcess = (ExecControlProcess) ((Baritone) baritone).getExecControlProcess();
                if (!controlProcess.paused) {
                    throw new CommandInvalidStateException("Not paused");
                }
                controlProcess.paused = false;
                logDirect("Resumed");
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return "Resumes Baritone after a pause";
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        "The resume command tells Baritone to resume whatever it was doing when you last used pause.",
                        "",
                        "Usage:",
                        "> resume"
                );
            }
        };
        pausedCommand = new Command("paused") {
            @Override
            public void execute(String label, IArgConsumer args, IBaritone baritone) throws CommandException {
                args.requireMax(0);
                boolean paused = ((ExecControlProcess) ((Baritone) baritone).getExecControlProcess()).paused;
                logDirect(String.format("Baritone is %spaused", paused ? "" : "not "));
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return "Tells you if Baritone is paused";
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        "The paused command tells you if Baritone is currently paused by use of the pause command.",
                        "",
                        "Usage:",
                        "> paused"
                );
            }
        };
        cancelCommand = new Command("cancel", "c", "stop") {
            @Override
            public void execute(String label, IArgConsumer args, IBaritone baritone) throws CommandException {
                args.requireMax(0);
                ((ExecControlProcess) ((Baritone) baritone).getExecControlProcess()).paused = false;
                baritone.getPathingBehavior().cancelEverything();
                logDirect("ok canceled");
            }

            @Override
            public Stream<String> tabComplete(String label, IArgConsumer args) {
                return Stream.empty();
            }

            @Override
            public String getShortDesc() {
                return "Cancel what Baritone is currently doing";
            }

            @Override
            public List<String> getLongDesc() {
                return Arrays.asList(
                        "The cancel command tells Baritone to stop whatever it's currently doing.",
                        "",
                        "Usage:",
                        "> cancel"
                );
            }
        };
    }

    public void registerCommands() {
        ICommandManager.registry.register(pauseCommand);
        ICommandManager.registry.register(resumeCommand);
        ICommandManager.registry.register(pausedCommand);
        ICommandManager.registry.register(cancelCommand);
    }

    public IBaritoneProcess registerProcess(IBaritone baritone) {
        ExecControlProcess proc = new ExecControlProcess();
        baritone.getPathingControlManager().registerProcess(proc);
        return proc;
    }

    private static class ExecControlProcess implements IBaritoneProcess {
        boolean paused;

        @Override
        public boolean isActive() {
            return paused;
        }

        @Override
        public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        @Override
        public boolean isTemporary() {
            return true;
        }

        @Override
        public void onLostControl() {
        }

        @Override
        public double priority() {
            return DEFAULT_PRIORITY + 1;
        }

        @Override
        public String displayName0() {
            return "Pause/Resume Commands";
        }
    }
}
