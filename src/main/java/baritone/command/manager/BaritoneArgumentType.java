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

import baritone.api.IBaritone;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandNotEnoughArgumentsException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.command.manager.ICommandManager;
import baritone.command.argument.ArgConsumer;
import baritone.command.argument.CommandArguments;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class BaritoneArgumentType implements ArgumentType<String> {
    public static BaritoneArgumentType baritone() {
        return new BaritoneArgumentType();
    }

    public static String getCommand(final CommandContext<?> context, final String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) {
        final String text = reader.getRemaining();
        reader.setCursor(reader.getTotalLength());
        return text;
    }

    public Stream<String> tabComplete(ICommandManager manager, String msg) {
        try {
            List<ICommandArgument> args = CommandArguments.from(msg, true);
            ArgConsumer argc = new ArgConsumer(manager, args, manager.getBaritone());
            if (argc.hasAtMost(2)) {
                if (argc.hasExactly(1)) {
                    return new TabCompleteHelper()
                            .addCommands()
                            .filterPrefix(argc.getString())
                            .stream();
                }
            }
            return manager.tabComplete(msg);
        } catch (CommandNotEnoughArgumentsException ignored) { // Shouldn't happen, the operation is safe
            return Stream.empty();
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            assert MinecraftClient.getInstance().player != null;
            IBaritone baritone = IBaritone.KEY.get(MinecraftClient.getInstance().player);
            tabComplete(baritone.getCommandManager(), builder.getRemaining()).forEach(builder::suggest);
            return builder.buildFuture();
        }
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return Arrays.asList("goto x y z", "click");
    }
}
