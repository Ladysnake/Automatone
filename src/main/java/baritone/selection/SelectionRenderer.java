package baritone.selection;

import baritone.api.selection.ISelection;
import baritone.utils.IRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;

public class SelectionRenderer implements IRenderer {

    public static final double SELECTION_BOX_EXPANSION = .005D;

    public static void renderSelections(MatrixStack stack, ISelection[] selections) {
        float opacity = settings.selectionOpacity.value;
        boolean ignoreDepth = settings.renderSelectionIgnoreDepth.value;
        float lineWidth = settings.selectionLineWidth.value;

        if (!settings.renderSelection.value) {
            return;
        }

        IRenderer.startLines(settings.colorSelection.value, opacity, lineWidth, ignoreDepth);

        for (ISelection selection : selections) {
            IRenderer.drawAABB(stack, selection.aabb(), SELECTION_BOX_EXPANSION);
        }

        if (settings.renderSelectionCorners.value) {
            IRenderer.glColor(settings.colorSelectionPos1.value, opacity);

            for (ISelection selection : selections) {
                IRenderer.drawAABB(stack, new Box(selection.pos1(), selection.pos1().add(1, 1, 1)));
            }

            IRenderer.glColor(settings.colorSelectionPos2.value, opacity);

            for (ISelection selection : selections) {
                IRenderer.drawAABB(stack, new Box(selection.pos2(), selection.pos2().add(1, 1, 1)));
            }
        }

        IRenderer.endLines(ignoreDepth);
    }

}
