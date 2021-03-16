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

package baritone.render;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RenderedPath {
    private final int position;
    private final List<BlockPos> pathPositions;
    private final List<BlockPos> toBreak;
    private final List<BlockPos> toPlace;
    private final List<BlockPos> toWalkInto;

    private RenderedPath(int position, @Nullable List<BlockPos> pathPositions, @Nullable List<BlockPos> toBreak, @Nullable List<BlockPos> toPlace, @Nullable List<BlockPos> toWalkInto) {
        this.position = position;
        this.pathPositions = pathPositions;
        this.toBreak = toBreak;
        this.toPlace = toPlace;
        this.toWalkInto = toWalkInto;
    }

    public List<BlockPos> pathPositions() {
        return this.pathPositions;
    }

    public int getPosition() {
        return this.position;
    }

    public Collection<BlockPos> toBreak() {
        return this.toBreak;
    }

    public Collection<BlockPos> toPlace() {
        return this.toPlace;
    }

    public Collection<BlockPos> toWalkInto() {
        return this.toWalkInto;
    }

    public static @Nullable RenderedPath fromPacket(PacketByteBuf buf) {
        int position = buf.readInt();
        if (position == -1) return null;
        List<BlockPos> pathPositions = readPositions(buf);
        List<BlockPos> toBreak = readPositions(buf);
        List<BlockPos> toPlace = readPositions(buf);
        List<BlockPos> toWalkInto = readPositions(buf);
        return new RenderedPath(position, pathPositions, toBreak, toPlace, toWalkInto);
    }

    private static List<BlockPos> readPositions(PacketByteBuf buf) {
        int length = buf.readVarInt();
        List<BlockPos> ret = new ArrayList<>(length);

        for (int i = 0; i < length; i++) {
            ret.add(new BlockPos(buf.readBlockPos()));
        }

        return ret;
    }
}
