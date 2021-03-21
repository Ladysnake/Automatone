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

package baritone.utils;

import baritone.Automatone;
import baritone.api.event.events.RenderEvent;
import baritone.api.pathing.goals.*;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.render.ClientPathingBehaviour;
import baritone.render.RenderedPath;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Brady
 * @since 8/9/2018
 */
public final class PathRenderer implements IRenderer {

    private static final Identifier TEXTURE_BEACON_BEAM = new Identifier("textures/entity/beacon_beam.png");


    private PathRenderer() {}

    public static double posX() {
        return renderManager.camera.getPos().x;
    }

    public static double posY() {
        return renderManager.camera.getPos().y;
    }

    public static double posZ() {
        return renderManager.camera.getPos().z;
    }

    public static void render(RenderEvent event, ClientPathingBehaviour behavior) {
        float partialTicks = event.getPartialTicks();
        Goal goal = behavior.getGoal();
        MinecraftClient mc = MinecraftClient.getInstance();

        DimensionType thisPlayerDimension = behavior.entity.world.getDimension();
        World world = Objects.requireNonNull(MinecraftClient.getInstance().world);
        DimensionType currentRenderViewDimension = world.getDimension();

        if (thisPlayerDimension != currentRenderViewDimension) {
            // this is a path for a bot in a different dimension, don't render it
            return;
        }

        Entity renderView = mc.getCameraEntity();

        if (renderView.world != world) {
            Automatone.LOGGER.error("I have no idea what's going on");
            Automatone.LOGGER.error("The primary baritone is in a different world than the render view entity");
            Automatone.LOGGER.error("Not rendering the path");
            return;
        }

        if (goal != null && settings.renderGoal.get()) {
            drawDankLitGoalBox(event.getModelViewStack(), renderView, goal, partialTicks, settings.colorGoalBox.get());
        }

        if (!settings.renderPath.get()) {
            return;
        }

        RenderedPath current = behavior.getCurrent(); // this should prevent most race conditions?
        RenderedPath next = behavior.getNext(); // like, now it's not possible for current!=null to be true, then suddenly false because of another thread
        if (current != null && settings.renderSelectionBoxes.get()) {
            drawManySelectionBoxes(event.getModelViewStack(), renderView, current.toBreak(), settings.colorBlocksToBreak.get());
            drawManySelectionBoxes(event.getModelViewStack(), renderView, current.toPlace(), settings.colorBlocksToPlace.get());
            drawManySelectionBoxes(event.getModelViewStack(), renderView, current.toWalkInto(), settings.colorBlocksToWalkInto.get());
        }

        //drawManySelectionBoxes(player, Collections.singletonList(behavior.pathStart()), partialTicks, Color.WHITE);

        // Render the current path, if there is one
        if (current != null && current.pathPositions() != null) {
            int renderBegin = Math.max(current.getPosition() - 3, 0);
            drawPath(event.getModelViewStack(), current.pathPositions(), renderBegin, settings.colorCurrentPath.get(), settings.fadePath.get(), 10, 20);
        }

        if (next != null && next.pathPositions() != null) {
            drawPath(event.getModelViewStack(), next.pathPositions(), 0, settings.colorNextPath.get(), settings.fadePath.get(), 10, 20);
        }

        // If there is a path calculation currently running, render the path calculation process
        behavior.getInProgress().ifPresent(currentlyRunning -> {
            currentlyRunning.bestPathSoFar().ifPresent(p -> drawPath(event.getModelViewStack(), p.positions(), 0, settings.colorBestPathSoFar.get(), settings.fadePath.get(), 10, 20));

            currentlyRunning.pathToMostRecentNodeConsidered().ifPresent(mr -> {
                drawPath(event.getModelViewStack(), mr.positions(), 0, settings.colorMostRecentConsidered.get(), settings.fadePath.get(), 10, 20);
                drawManySelectionBoxes(event.getModelViewStack(), renderView, Collections.singletonList(mr.getDest()), settings.colorMostRecentConsidered.get());
            });
        });
    }

    public static void drawPath(MatrixStack stack, List<? extends BlockPos> positions, int startIndex, Color color, boolean fadeOut, int fadeStart0, int fadeEnd0) {
        IRenderer.startLines(color, settings.pathRenderLineWidthPixels.get(), settings.renderPathIgnoreDepth.get());

        int fadeStart = fadeStart0 + startIndex;
        int fadeEnd = fadeEnd0 + startIndex;

        for (int i = startIndex, next; i < positions.size() - 1; i = next) {
            BlockPos start = positions.get(i);
            BlockPos end = positions.get(next = i + 1);

            int dirX = end.getX() - start.getX();
            int dirY = end.getY() - start.getY();
            int dirZ = end.getZ() - start.getZ();

            while (next + 1 < positions.size() && (!fadeOut || next + 1 < fadeStart) &&
                    (dirX == positions.get(next + 1).getX() - end.getX() &&
                            dirY == positions.get(next + 1).getY() - end.getY() &&
                            dirZ == positions.get(next + 1).getZ() - end.getZ())) {
                end = positions.get(++next);
            }

            if (fadeOut) {
                float alpha;

                if (i <= fadeStart) {
                    alpha = 0.4F;
                } else {
                    if (i > fadeEnd) {
                        break;
                    }
                    alpha = 0.4F * (1.0F - (float) (i - fadeStart) / (float) (fadeEnd - fadeStart));
                }
                IRenderer.glColor(color, alpha);
            }

            drawLine(stack, start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ());

            tessellator.draw();
        }

        IRenderer.endLines(settings.renderPathIgnoreDepth.get());
    }


    public static void drawLine(MatrixStack stack, double x1, double y1, double z1, double x2, double y2, double z2) {
        Matrix4f matrix4f = stack.peek().getModel();

        double vpX = posX();
        double vpY = posY();
        double vpZ = posZ();
        boolean renderPathAsFrickinThingy = !settings.renderPathAsLine.get();

        buffer.begin(renderPathAsFrickinThingy ? GL_LINE_STRIP : GL_LINES, VertexFormats.POSITION);
        buffer.vertex(matrix4f, (float) (x1 + 0.5D - vpX), (float) (y1 + 0.5D - vpY), (float) (z1 + 0.5D - vpZ)).next();
        buffer.vertex(matrix4f, (float) (x2 + 0.5D - vpX), (float) (y2 + 0.5D - vpY), (float) (z2 + 0.5D - vpZ)).next();

        if (renderPathAsFrickinThingy) {
            buffer.vertex(matrix4f, (float) (x2 + 0.5D - vpX), (float) (y2 + 0.53D - vpY), (float) (z2 + 0.5D - vpZ)).next();
            buffer.vertex(matrix4f, (float) (x1 + 0.5D - vpX), (float) (y1 + 0.53D - vpY), (float) (z1 + 0.5D - vpZ)).next();
            buffer.vertex(matrix4f, (float) (x1 + 0.5D - vpX), (float) (y1 + 0.5D - vpY), (float) (z1 + 0.5D - vpZ)).next();
        }
    }

    public static void drawManySelectionBoxes(MatrixStack stack, Entity player, Collection<BlockPos> positions, Color color) {
        IRenderer.startLines(color, settings.pathRenderLineWidthPixels.get(), settings.renderSelectionBoxesIgnoreDepth.get());

        positions.forEach(pos -> {
            BlockState state = player.world.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(player.world, pos);
            Box toDraw = shape.isEmpty() ? VoxelShapes.fullCube().getBoundingBox() : shape.getBoundingBox();
            toDraw = toDraw.offset(pos);
            IRenderer.drawAABB(stack, toDraw, .002D);
        });

        IRenderer.endLines(settings.renderSelectionBoxesIgnoreDepth.get());
    }

    public static void drawDankLitGoalBox(MatrixStack stack, Entity player, Goal goal, float partialTicks, Color color) {
        double renderPosX = posX();
        double renderPosY = posY();
        double renderPosZ = posZ();
        double minX, maxX;
        double minZ, maxZ;
        double minY, maxY;
        double y1, y2;
        double y = MathHelper.cos((float) (((float) ((System.nanoTime() / 100000L) % 20000L)) / 20000F * Math.PI * 2));
        if (goal instanceof IGoalRenderPos) {
            BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y /= 2;
            }
            y1 = 1 + y + goalPos.getY() - renderPosY;
            y2 = 1 - y + goalPos.getY() - renderPosY;
            minY = goalPos.getY() - renderPosY;
            maxY = minY + 2;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y1 -= 0.5;
                y2 -= 0.5;
                maxY--;
            }
        } else if (goal instanceof GoalXZ) {
            GoalXZ goalPos = (GoalXZ) goal;

            if (settings.renderGoalXZBeacon.get()) {
                glPushAttrib(GL_LIGHTING_BIT);

                MinecraftClient.getInstance().getTextureManager().bindTexture(TEXTURE_BEACON_BEAM);
                if (settings.renderGoalIgnoreDepth.get()) {
                    RenderSystem.disableDepthTest();
                }

                stack.push(); // push
                stack.translate(goalPos.getX() - renderPosX, -renderPosY, goalPos.getZ() - renderPosZ); // translate

                BeaconBlockEntityRenderer.renderLightBeam(
                        stack,
                        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                        TEXTURE_BEACON_BEAM,
                        partialTicks,
                        1.0F,
                        player.world.getTime(),
                        0,
                        player.world.getHeight(),
                        color.getColorComponents(null),

                        // Arguments filled by the private method lol
                        0.2F,
                        0.25F
                );

                stack.pop(); // pop

                if (settings.renderGoalIgnoreDepth.get()) {
                    RenderSystem.enableDepthTest();
                }

                glPopAttrib();
                return;
            }

            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;

            y1 = 0;
            y2 = 0;
            minY = 0 - renderPosY;
            maxY = player.world.getHeight() - renderPosY;
        } else if (goal instanceof GoalComposite) {
            for (Goal g : ((GoalComposite) goal).goals()) {
                drawDankLitGoalBox(stack, player, g, partialTicks, color);
            }
            return;
        } else if (goal instanceof GoalInverted) {
            drawDankLitGoalBox(stack, player, ((GoalInverted) goal).origin, partialTicks, settings.colorInvertedGoalBox.get());
            return;
        } else if (goal instanceof GoalYLevel) {
            GoalYLevel goalpos = (GoalYLevel) goal;
            minX = player.getX() - settings.yLevelBoxSize.get() - renderPosX;
            minZ = player.getZ() - settings.yLevelBoxSize.get() - renderPosZ;
            maxX = player.getX() + settings.yLevelBoxSize.get() - renderPosX;
            maxZ = player.getZ() + settings.yLevelBoxSize.get() - renderPosZ;
            minY = ((GoalYLevel) goal).level - renderPosY;
            maxY = minY + 2;
            y1 = 1 + y + goalpos.level - renderPosY;
            y2 = 1 - y + goalpos.level - renderPosY;
        } else {
            return;
        }

        IRenderer.startLines(color, settings.goalRenderLineWidthPixels.get(), settings.renderGoalIgnoreDepth.get());

        renderHorizontalQuad(stack, minX, maxX, minZ, maxZ, y1);
        renderHorizontalQuad(stack, minX, maxX, minZ, maxZ, y2);

        Matrix4f matrix4f = stack.peek().getModel();
        buffer.begin(GL_LINES, VertexFormats.POSITION);
        buffer.vertex(matrix4f, (float) minX, (float) minY, (float) minZ).next();
        buffer.vertex(matrix4f, (float) minX, (float) maxY, (float) minZ).next();
        buffer.vertex(matrix4f, (float) maxX, (float) minY, (float) minZ).next();
        buffer.vertex(matrix4f, (float) maxX, (float) maxY, (float) minZ).next();
        buffer.vertex(matrix4f, (float) maxX, (float) minY, (float) maxZ).next();
        buffer.vertex(matrix4f, (float) maxX, (float) maxY, (float) maxZ).next();
        buffer.vertex(matrix4f, (float) minX, (float) minY, (float) maxZ).next();
        buffer.vertex(matrix4f, (float) minX, (float) maxY, (float) maxZ).next();
        tessellator.draw();

        IRenderer.endLines(settings.renderGoalIgnoreDepth.get());
    }

    private static void renderHorizontalQuad(MatrixStack stack, double minX, double maxX, double minZ, double maxZ, double y) {
        if (y != 0) {
            Matrix4f matrix4f = stack.peek().getModel();
            buffer.begin(GL_LINE_LOOP, VertexFormats.POSITION);
            buffer.vertex(matrix4f, (float) minX, (float) y, (float) minZ).next();
            buffer.vertex(matrix4f, (float) maxX, (float) y, (float) minZ).next();
            buffer.vertex(matrix4f, (float) maxX, (float) y, (float) maxZ).next();
            buffer.vertex(matrix4f, (float) minX, (float) y, (float) maxZ).next();
            tessellator.draw();
        }
    }
}
