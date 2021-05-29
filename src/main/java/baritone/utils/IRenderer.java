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

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public interface IRenderer {

    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder buffer = tessellator.getBuffer();
    EntityRenderDispatcher renderManager = MinecraftClient.getInstance().getEntityRenderDispatcher();
    Settings settings = BaritoneAPI.getGlobalSettings();

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        State.red = colorComponents[0];
        State.green = colorComponents[1];
        State.blue = colorComponents[2];
        State.alpha = alpha;
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

    static void drawAABB(Box aabb) {
        Vec3d cameraPos = renderManager.camera.getPos();
        Box toDraw = aabb.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        // bottom
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        // top
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        // corners
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.minZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.maxX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.minY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        buffer.vertex((float) toDraw.minX, (float) toDraw.maxY, (float) toDraw.maxZ).color(State.red, State.green, State.blue, State.alpha).next();
        tessellator.draw();
    }

    static void drawAABB(Box aabb, double expand) {
        drawAABB(aabb.expand(expand, expand, expand));
    }

    class State {
        static float red, green, blue, alpha;
    }
}
