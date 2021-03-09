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

package automatone.command.defaults;

import automatone.api.BaritoneAPI;
import automatone.api.IBaritone;
import automatone.api.command.Command;
import automatone.api.command.argument.IArgConsumer;
import automatone.api.command.exception.CommandException;
import automatone.api.command.exception.CommandInvalidStateException;
import automatone.api.pathing.goals.GoalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ComeCommand extends Command {

    public ComeCommand(IBaritone baritone) {
        super(baritone, "come");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        List<Entity> entities = ((ServerWorld) ctx.world()).getEntitiesByType(EntityType.HUSK, e -> true);
        if (entities.isEmpty()) {
            throw new CommandInvalidStateException("no entity found");
        }
        LivingEntity entity = (LivingEntity) entities.get(0);
        BaritoneAPI.getProvider().getBaritone(entity).getCustomGoalProcess().setGoalAndPath(new GoalBlock(ctx.entity().getBlockPos()));
        logDirect("Coming");
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
                "The come command tells Baritone to head towards your camera.",
                "",
                "This can be useful in hacked clients where freecam doesn't move your player position.",
                "",
                "Usage:",
                "> come"
        );
    }
}
