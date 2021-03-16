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

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ComeCommand extends Command {

    public ComeCommand() {
        super("come");
    }

    @Override
    public void execute(ServerCommandSource source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
        args.requireMax(0);
        List<Entity> entities = ((ServerWorld) baritone.getPlayerContext().world()).getEntitiesByType(EntityType.HUSK, e -> true);
        if (entities.isEmpty()) {
            throw new CommandInvalidStateException("no entity found");
        }
        LivingEntity entity = (LivingEntity) entities.get(0);
        BaritoneAPI.getProvider().getBaritone(entity).getCustomGoalProcess().setGoalAndPath(new GoalBlock(baritone.getPlayerContext().entity().getBlockPos()));
        logDirect(source, "Coming");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Start heading towards your camera";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The come command tells Automatone to head towards the source of the command.",
                "",
                "This can be useful alongside redirection commands like \"/execute as\".",
                "",
                "Usage:",
                "> come"
        );
    }
}
