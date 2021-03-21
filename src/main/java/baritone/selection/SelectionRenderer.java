package baritone.selection;

import baritone.api.selection.ISelection;
import baritone.utils.IRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;

public class SelectionRenderer implements IRenderer {

    public static final double SELECTION_BOX_EXPANSION = .005D;

    public static void renderSelections(MatrixStack stack, ISelection[] selections) {
        float opacity = settings.selectionOpacity.get();
        boolean ignoreDepth = settings.renderSelectionIgnoreDepth.get();
        float lineWidth = settings.selectionLineWidth.get();

        if (!settings.renderSelection.get()) {
            return;
        }

        IRenderer.startLines(settings.colorSelection.get(), opacity, lineWidth, ignoreDepth);

        for (ISelection selection : selections) {
            IRenderer.drawAABB(stack, selection.aabb(), SELECTION_BOX_EXPANSION);
        }

        if (settings.renderSelectionCorners.get()) {
            IRenderer.glColor(settings.colorSelectionPos1.get(), opacity);

            for (ISelection selection : selections) {
                IRenderer.drawAABB(stack, new Box(selection.pos1(), selection.pos1().add(1, 1, 1)));
            }

            IRenderer.glColor(settings.colorSelectionPos2.get(), opacity);

            for (ISelection selection : selections) {
                IRenderer.drawAABB(stack, new Box(selection.pos2(), selection.pos2().add(1, 1, 1)));
            }
        }

        IRenderer.endLines(ignoreDepth);
    }

}
