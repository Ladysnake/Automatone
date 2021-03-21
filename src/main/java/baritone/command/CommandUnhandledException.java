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

package baritone.command;

import baritone.Automatone;
import baritone.api.command.exception.CommandException;
import net.minecraft.text.Text;

public class CommandUnhandledException extends CommandException {

    public CommandUnhandledException(String message) {
        super(message);
    }

    public CommandUnhandledException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Text handle() {
        Automatone.LOGGER.error("An unhandled exception occurred while running a command", this.getCause());
        return super.handle();
    }
}
