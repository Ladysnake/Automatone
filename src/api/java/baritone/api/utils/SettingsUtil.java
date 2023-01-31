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

package baritone.api.utils;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.awt.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class SettingsUtil {

    public static List<Settings.Setting<?>> modifiedSettings(Settings settings) {
        List<Settings.Setting<?>> modified = new ArrayList<>();
        for (Settings.Setting<?> setting : settings.allSettings) {
            if (setting.get() == null) {
                System.err.println("NULL SETTING?" + setting.getName());
                continue;
            }
            if (setting.getName().equals("logger")) {
                continue; // NO
            }
            if (setting.get() == setting.defaultValue()) {
                continue;
            }
            modified.add(setting);
        }
        return modified;
    }

    /**
     * Gets the type of a setting and returns it as a string, with package names stripped.
     * <p>
     * For example, if the setting type is {@code java.util.List<java.lang.String>}, this function returns
     * {@code List<String>}.
     *
     * @param setting The setting
     * @return The type
     */
    public static String settingTypeToString(Settings.Setting<?> setting) {
        return setting.getType().getTypeName()
                .replaceAll("(?:\\w+\\.)+(\\w+)", "$1");
    }

    public static <T> String settingValueToString(Settings.Setting<T> setting, T value) throws IllegalArgumentException {
        Parser io = Parser.getParser(setting.getType());

        if (io == null) {
            throw new IllegalStateException("Missing " + setting.getValueClass() + " " + setting.getName());
        }

        return io.toString(new ParserContext(setting), value);
    }

    public static <T> String settingValueToString(Settings.Setting<T> setting) throws IllegalArgumentException {
        return settingValueToString(setting, setting.get());
    }

    public static <T> String settingDefaultToString(Settings.Setting<T> setting) throws IllegalArgumentException {
        return settingValueToString(setting, setting.defaultValue());
    }

    public static String maybeCensor(int coord) {
        if (BaritoneAPI.getGlobalSettings().censorCoordinates.get()) {
            return "<censored>";
        }

        return Integer.toString(coord);
    }

    public static String settingToString(Settings.Setting<?> setting) throws IllegalStateException {
        if (setting.getName().equals("logger")) {
            return "logger";
        }

        return setting.getName() + " " + settingValueToString(setting);
    }

    public static void parseAndApply(Settings settings, String settingName, String settingValue) throws IllegalStateException, NumberFormatException {
        Settings.Setting<?> setting = settings.byLowerName.get(settingName);
        if (setting == null) {
            throw new IllegalStateException("No setting by that name");
        }
        parseAndApply(setting, settingValue);
    }

    private static <T> void parseAndApply(Settings.Setting<T> setting, String settingValue) {
        Class<T> intendedType = setting.getValueClass();
        Parser ioMethod = Parser.getParser(setting.getType());
        @SuppressWarnings("unchecked") T parsed = (T) ioMethod.parse(new ParserContext(setting), settingValue);
        if (!intendedType.isInstance(parsed)) {
            throw new IllegalStateException(ioMethod + " parser returned incorrect type, expected " + intendedType + " got " + parsed + " which is " + parsed.getClass());
        }
        setting.set(parsed);
    }

    private static class ParserContext {

        private final Settings.Setting<?> setting;

        private ParserContext(Settings.Setting<?> setting) {
            this.setting = setting;
        }

        private Settings.Setting<?> getSetting() {
            return this.setting;
        }
    }

    private enum Parser {

        DOUBLE(Double.class, Double::parseDouble),
        BOOLEAN(Boolean.class, Boolean::parseBoolean),
        INTEGER(Integer.class, Integer::parseInt),
        FLOAT(Float.class, Float::parseFloat),
        LONG(Long.class, Long::parseLong),
        STRING(String.class, String::new),
        DIRECTION(Direction.class, d -> Direction.valueOf(d.toUpperCase(Locale.ROOT))),
        COLOR(
                Color.class,
                str -> new Color(Integer.parseInt(str.split(",")[0]), Integer.parseInt(str.split(",")[1]), Integer.parseInt(str.split(",")[2])),
                color -> color.getRed() + "," + color.getGreen() + "," + color.getBlue()
        ),
        VEC3I(
                Vec3i.class,
                str -> new Vec3i(Integer.parseInt(str.split(",")[0]), Integer.parseInt(str.split(",")[1]), Integer.parseInt(str.split(",")[2])),
                vec -> vec.getX() + "," + vec.getY() + "," + vec.getZ()
        ),
        BLOCK(
                Block.class,
                str -> BlockUtils.stringToBlockRequired(str.trim()),
                BlockUtils::blockToString
        ),
        ITEM(
                Item.class,
                str -> Registries.ITEM.get(new Identifier(str.trim())), // TODO this now returns AIR on failure instead of null, is that an issue?
                item -> Registries.ITEM.getKey(item).toString()
        ),
        TAG() {
            @Override
            public Object parse(ParserContext context, String raw) {
                Type type = ((ParameterizedType) context.getSetting().getType()).getActualTypeArguments()[0];
                Identifier id = new Identifier(raw);
                if (type == Block.class) {
                    return TagKey.of(RegistryKeys.BLOCK, id);
                } else if (type == Item.class) {
                    return TagKey.of(RegistryKeys.ITEM, id);
                } else if (type == EntityType.class) {
                    return TagKey.of(RegistryKeys.ENTITY_TYPE, id);
                } else if (type == Fluid.class) {
                    return TagKey.of(RegistryKeys.FLUID, id);
                } else {
                    throw new IllegalArgumentException();
                }
            }

            @Override
            public String toString(ParserContext context, Object value) {
                return ((TagKey<?>) value).id().toString();
            }

            @Override
            public boolean accepts(Type type) {
                return TagKey.class.isAssignableFrom(TypeUtils.resolveBaseClass(type));
            }
        },
        LIST() {
            @Override
            public Object parse(ParserContext context, String raw) {
                Type type = ((ParameterizedType) context.getSetting().getType()).getActualTypeArguments()[0];
                Parser parser = Parser.getParser(type);
                return Stream.of(raw.split(","))
                        .map(s -> parser.parse(context, s))
                        .collect(Collectors.toList());
            }

            @Override
            public String toString(ParserContext context, Object value) {
                Type type = ((ParameterizedType) context.getSetting().getType()).getActualTypeArguments()[0];
                Parser parser = Parser.getParser(type);

                return ((List<?>) value).stream()
                        .map(o -> parser.toString(context, o))
                        .collect(Collectors.joining(","));
            }

            @Override
            public boolean accepts(Type type) {
                return List.class.isAssignableFrom(TypeUtils.resolveBaseClass(type));
            }
        };

        private final Class<?> cla$$;
        private final Function<String, Object> parser;
        private final Function<Object, String> toString;

        Parser() {
            this.cla$$ = null;
            this.parser = null;
            this.toString = null;
        }

        <T> Parser(Class<T> cla$$, Function<String, T> parser) {
            this(cla$$, parser, Object::toString);
        }

        <T> Parser(Class<T> cla$$, Function<String, T> parser, Function<T, String> toString) {
            this.cla$$ = cla$$;
            this.parser = parser::apply;
            this.toString = x -> toString.apply(cla$$.cast(x));
        }

        public Object parse(ParserContext context, String raw) {
            Object parsed = this.parser.apply(raw);
            Objects.requireNonNull(parsed);
            return parsed;
        }

        public String toString(ParserContext context, Object value) {
            return this.toString.apply(value);
        }

        public boolean accepts(Type type) {
            return type instanceof Class && this.cla$$.isAssignableFrom((Class<?>) type);
        }

        public static Parser getParser(Type type) {
            return Stream.of(values())
                    .filter(parser -> parser.accepts(type))
                    .findFirst().orElse(null);
        }
    }
}
