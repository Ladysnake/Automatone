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

package baritone.api;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Calendar;

/**
 * Exposes the {@link IBaritoneProvider} instance and the {@link Settings} instance for API usage.
 *
 * @author Brady
 * @since 9/23/2018
 */
public final class BaritoneAPI {

    private static final IBaritoneProvider provider;

    static {
        try {
            provider = (IBaritoneProvider) Class.forName("baritone.BaritoneProvider").getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static IBaritoneProvider getProvider() {
        return BaritoneAPI.provider;
    }

    public static Settings getGlobalSettings() {
        return getProvider().getGlobalSettings();
    }

    public static Text getPrefix() {
        // Inner text component
        final Calendar now = Calendar.getInstance();
        final boolean xd = now.get(Calendar.MONTH) == Calendar.APRIL && now.get(Calendar.DAY_OF_MONTH) <= 3;
        MutableText baritone = Text.literal(xd ? "Automatoe" : getGlobalSettings().shortBaritonePrefix.get() ? "A" : "Automatone");
        baritone.setStyle(baritone.getStyle().withFormatting(Formatting.GREEN));

        // Outer brackets
        MutableText prefix = Text.literal("");
        prefix.setStyle(baritone.getStyle().withFormatting(Formatting.DARK_GREEN));
        prefix.append("[");
        prefix.append(baritone);
        prefix.append("]");

        return prefix;
    }
}
