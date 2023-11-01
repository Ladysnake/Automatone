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
import baritone.api.utils.BetterBlockPos;
import com.mojang.blaze3d.glfw.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.*;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;
import static org.lwjgl.opengl.GL11.*;

public class GuiClick extends Screen {

    private final UUID callerUuid;
    private Matrix4f projectionViewMatrix;

    private BlockPos clickStart;
    private BlockPos currentMouseOver;

    public GuiClick(UUID callerUuid) {
        super(Text.literal("CLICK"));
        this.callerUuid = callerUuid;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double mx = mc.mouse.getX();
        double my = mc.mouse.getY();

        my = mc.getWindow().getHeight() - my;
        my *= mc.getWindow().getFramebufferHeight() / (double) mc.getWindow().getHeight();
        mx *= mc.getWindow().getFramebufferWidth() / (double) mc.getWindow().getWidth();
        Vec3d near = toWorld(mx, my, 0);
        Vec3d far = toWorld(mx, my, 1); // "Use 0.945 that's what stack overflow says" - leijurv

        if (near != null && far != null) {
            ///
            Vec3d viewerPos = new Vec3d(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ());
            PlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);
            BlockHitResult result = player.getWorld().raycast(new RaycastContext(near.add(viewerPos), far.add(viewerPos), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                currentMouseOver = result.getBlockPos();
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (currentMouseOver != null) { //Catch this, or else a click into void will result in a crash
            MinecraftClient client = this.client;
            assert client != null;
            assert client.player != null;
            assert client.world != null;
            if (mouseButton == 0) {
                if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                    client.player.networkHandler.sendChatCommand(String.format("execute as %s run automatone sel clear", callerUuid));
                    client.player.networkHandler.sendChatCommand(String.format("execute as %s run automatone sel 1 %d %d %d", callerUuid, clickStart.getX(), clickStart.getY(), clickStart.getZ()));
                    client.player.networkHandler.sendChatCommand(String.format("execute as %s run automatone sel 2 %d %d %d", callerUuid, currentMouseOver.getX(), currentMouseOver.getY(), currentMouseOver.getZ()));
                    MutableText component = Text.literal("").append(BaritoneAPI.getPrefix()).append(" Selection made! For usage: " + FORCE_COMMAND_PREFIX + "help sel");
                    component.setStyle(component.getStyle()
                            .withFormatting(Formatting.WHITE)
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FORCE_COMMAND_PREFIX + "help sel"
                            )));
                    client.inGameHud.getChatHud().addMessage(component);
                } else {
                    client.player.networkHandler.sendChatCommand(String.format("execute as %s run automatone goto %d %d %d", callerUuid, currentMouseOver.getX(), currentMouseOver.getY(), currentMouseOver.getZ()));
                }
            } else if (mouseButton == 1) {
                client.player.networkHandler.sendChatCommand(String.format("execute as %s run automatone goto %d %d %d", callerUuid, currentMouseOver.getX(), currentMouseOver.getY() + 1, currentMouseOver.getZ()));
            }
        }
        clickStart = null;
        return super.mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        clickStart = currentMouseOver;
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void onRender(MatrixStack modelViewStack, Matrix4f projectionMatrix) {
        this.projectionViewMatrix = new Matrix4f(projectionMatrix);
        this.projectionViewMatrix.mul(modelViewStack.peek().getModel());
        this.projectionViewMatrix.invert();

        if (currentMouseOver != null) {
            Entity e = MinecraftClient.getInstance().getCameraEntity();
            Camera c = MinecraftClient.getInstance().gameRenderer.getCamera();
            assert e != null;
            VertexConsumer vertexConsumer = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getLines());
            WorldRenderer.drawBox(modelViewStack, vertexConsumer, new Box(currentMouseOver).offset(-c.getPos().x, -c.getPos().y, -c.getPos().z).expand(0.002), 0, 1, 1, 1);
            if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
                RenderSystem.lineWidth(BaritoneAPI.getGlobalSettings().pathRenderLineWidthPixels.get());
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.depthMask(false);
                RenderSystem.disableDepthTest();
                BetterBlockPos a = new BetterBlockPos(currentMouseOver);
                BetterBlockPos b = new BetterBlockPos(clickStart);
                WorldRenderer.drawBox(modelViewStack, vertexConsumer, new Box(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x) + 1, Math.max(a.y, b.y) + 1, Math.max(a.z, b.z) + 1).offset(-c.getPos().x, -c.getPos().y, -c.getPos().z), 1, 0, 0, 0.4f);
                RenderSystem.enableDepthTest();

                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
            }
        }
    }

    private Vec3d toWorld(double x, double y, double z) {
        if (this.projectionViewMatrix == null) {
            return null;
        }

        Window window = MinecraftClient.getInstance().getWindow();
        x /= window.getFramebufferWidth();
        y /= window.getFramebufferHeight();
        x = x * 2 - 1;
        y = y * 2 - 1;

        Vector4f pos = new Vector4f((float) x, (float) y, (float) z, 1.0F);
        pos.mul(this.projectionViewMatrix);
        if (pos.w == 0) {
            return null;
        }

        pos.div(pos.w); // Normalize projection coordinates
        return new Vec3d(pos.x, pos.y, pos.z);
    }
}
