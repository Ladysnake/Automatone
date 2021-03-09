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

package automatone.utils;

import automatone.api.BaritoneAPI;
import automatone.api.Settings;
import automatone.utils.accessor.IEntityRenderManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Matrix4f;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public interface IRenderer {

    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder buffer = tessellator.getBuffer();
    IEntityRenderManager renderManager = (IEntityRenderManager) MinecraftClient.getInstance().getEntityRenderDispatcher();
    Settings settings = BaritoneAPI.getSettings();

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        RenderSystem.color4f(colorComponents[0], colorComponents[1], colorComponents[2], alpha);
    }

    static void startLines(Color color, float alpha, float lineWidth, boolean ignoreDepth) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        glColor(color, alpha);
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.disableTexture();
        RenderSystem.depthMask(false);

        if (ignoreDepth) {
            RenderSystem.disableDepthTest();
        }
    }

    static void startLines(Color color, float lineWidth, boolean ignoreDepth) {
        startLines(color, .4f, lineWidth, ignoreDepth);
    }

    static void endLines(boolean ignoredDepth) {
        if (ignoredDepth) {
            RenderSystem.enableDepthTest();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    static void drawAABB(MatrixStack stack, Box aabb) {
        Box toDraw = aabb.offset(-renderManager.renderPosX(), -renderManager.renderPosY(), -renderManager.renderPosZ());

        Matrix4f matrix4f = stack.peek().getModel();
        buffer.begin(GL_LINES, VertexFormats.POSITION);
        // bottom
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).next();
        // top
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).next();
        // corners
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).next();
        buffer.vertex(matrix4f, (float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).next();
        tessellator.draw();
    }

    static void drawAABB(MatrixStack stack, Box aabb, double expand) {
        drawAABB(stack, aabb.expand(expand, expand, expand));
    }
}
