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

package baritone.api.command.helpers;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidTypeException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Paginator<E> {

    private final ServerCommandSource source;
    public final List<E> entries;
    public int pageSize = 8;
    public int page = 1;

    public Paginator(ServerCommandSource source, List<E> entries) {
        this.source = source;
        this.entries = entries;
    }

    public Paginator<E> setPageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public int getMaxPage() {
        return (entries.size() - 1) / pageSize + 1;
    }

    public boolean validPage(int page) {
        return page > 0 && page <= getMaxPage();
    }

    public Paginator<E> skipPages(int pages) {
        page += pages;
        return this;
    }

    public void display(Function<E, Text> transform, String commandPrefix) {
        int offset = (page - 1) * pageSize;
        for (int i = offset; i < offset + pageSize; i++) {
            if (i < entries.size()) {
                source.sendFeedback(transform.apply(entries.get(i)), false);
            } else {
                source.sendFeedback(Text.literal("--").formatted(Formatting.DARK_GRAY), false);
            }
        }
        boolean hasPrevPage = commandPrefix != null && validPage(page - 1);
        boolean hasNextPage = commandPrefix != null && validPage(page + 1);
        MutableText prevPageComponent = Text.literal("<<");
        if (hasPrevPage) {
            prevPageComponent.setStyle(prevPageComponent.getStyle()
                    .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format("%s %d", commandPrefix, page - 1)
                    ))
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Text.literal("Click to view previous page")
                    )));
        } else {
            prevPageComponent.setStyle(prevPageComponent.getStyle().withFormatting(Formatting.DARK_GRAY));
        }
        MutableText nextPageComponent = Text.literal(">>");
        if (hasNextPage) {
            nextPageComponent.setStyle(nextPageComponent.getStyle()
                    .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            String.format("%s %d", commandPrefix, page + 1)
                    ))
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Text.literal("Click to view next page")
                    )));
        } else {
            nextPageComponent.setStyle(nextPageComponent.getStyle().withFormatting(Formatting.DARK_GRAY));
        }
        MutableText pagerComponent = Text.literal("");
        pagerComponent.setStyle(pagerComponent.getStyle().withFormatting(Formatting.GRAY));
        pagerComponent.append(prevPageComponent);
        pagerComponent.append(" | ");
        pagerComponent.append(nextPageComponent);
        pagerComponent.append(String.format(" %d/%d", page, getMaxPage()));
        source.sendFeedback(pagerComponent, false);
    }

    public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Runnable pre, Function<T, Text> transform, String commandPrefix) throws CommandException {
        int page = 1;
        consumer.requireMax(1);
        if (consumer.hasAny()) {
            page = consumer.getAs(Integer.class);
            if (!pagi.validPage(page)) {
                throw new CommandInvalidTypeException(
                        consumer.consumed(),
                        String.format(
                                "a valid page (1-%d)",
                                pagi.getMaxPage()
                        ),
                        consumer.consumed().getValue()
                );
            }
        }
        pagi.skipPages(page - pagi.page);
        if (pre != null) {
            pre.run();
        }
        pagi.display(transform, commandPrefix);
    }

    public static <T> void paginate(IArgConsumer consumer, List<T> elems, Runnable pre, Function<T, Text> transform, String commandPrefix, ServerCommandSource source) throws CommandException {
        paginate(consumer, new Paginator<>(source, elems), pre, transform, commandPrefix);
    }

    public static <T> void paginate(IArgConsumer consumer, T[] elems, Runnable pre, Function<T, Text> transform, String commandPrefix, ServerCommandSource source) throws CommandException {
        paginate(consumer, Arrays.asList(elems), pre, transform, commandPrefix, source);
    }

    public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Function<T, Text> transform, String commandPrefix) throws CommandException {
        paginate(consumer, pagi, null, transform, commandPrefix);
    }

    public static <T> void paginate(IArgConsumer consumer, List<T> elems, Function<T, Text> transform, String commandPrefix, ServerCommandSource source) throws CommandException {
        paginate(consumer, new Paginator<>(source, elems), null, transform, commandPrefix);
    }

    public static <T> void paginate(IArgConsumer consumer, T[] elems, Function<T, Text> transform, String commandPrefix, ServerCommandSource source) throws CommandException {
        paginate(consumer, Arrays.asList(elems), null, transform, commandPrefix, source);
    }

    public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Runnable pre, Function<T, Text> transform) throws CommandException {
        paginate(consumer, pagi, pre, transform, null);
    }

    public static <T> void paginate(IArgConsumer consumer, List<T> elems, Runnable pre, Function<T, Text> transform, ServerCommandSource source) throws CommandException {
        paginate(consumer, new Paginator<>(source, elems), pre, transform, null);
    }

    public static <T> void paginate(IArgConsumer consumer, T[] elems, Runnable pre, Function<T, Text> transform, ServerCommandSource source) throws CommandException {
        paginate(consumer, Arrays.asList(elems), pre, transform, null, source);
    }

    public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Function<T, Text> transform) throws CommandException {
        paginate(consumer, pagi, null, transform, null);
    }

    public static <T> void paginate(IArgConsumer consumer, List<T> elems, Function<T, Text> transform, ServerCommandSource source) throws CommandException {
        paginate(consumer, new Paginator<>(source, elems), null, transform, null);
    }

    public static <T> void paginate(IArgConsumer consumer, T[] elems, Function<T, Text> transform, ServerCommandSource source) throws CommandException {
        paginate(consumer, Arrays.asList(elems), null, transform, null, source);
    }
}
