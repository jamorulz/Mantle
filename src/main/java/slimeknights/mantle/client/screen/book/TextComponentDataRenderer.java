package slimeknights.mantle.client.screen.book;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.book.data.element.TextComponentData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class TextComponentDataRenderer {

  /**
   * Renders the given Text Components on the screen and returns the action if any of them have one.
   *
   * @param matrixStack the matrix stack to render with
   * @param x           the x position to render at
   * @param y           the y position to render at
   * @param boxWidth    the width of the given render box
   * @param boxHeight   the height of the given render box
   * @param data        the list of text component data to draw
   * @param mouseX      the mouseY
   * @param mouseY      the mouseX
   * @param fr          the font renderer
   * @param tooltip     the list of tooltips
   * @return the action if there's any
   */
  public static String drawText(MatrixStack matrixStack, int x, int y, int boxWidth, int boxHeight, TextComponentData[] data, int mouseX, int mouseY, FontRenderer fr, List<ITextComponent> tooltip) {
    String action = "";

    int atX = x;
    int atY = y;

    float prevScale = 1.F;

    for (TextComponentData item : data) {
      int box1X, box1Y, box1W = 9999, box1H = y + fr.FONT_HEIGHT;
      int box2X, box2Y = 9999, box2W, box2H;
      int box3X = 9999, box3Y = 9999, box3W, box3H;

      if (item == null || item.text == null) {
        continue;
      }

      if (item.text.getString().equals("\n")) {
        atX = x;
        atY += fr.FONT_HEIGHT;
        continue;
      }

      if (item.isParagraph) {
        atX = x;
        atY += fr.FONT_HEIGHT * 2 * prevScale;
      }

      prevScale = item.scale;

      ITextComponent text = translateTextComponent(item.text);

      ITextComponent[] split = cropTextComponentsBySize(text, boxWidth, boxHeight - (atY - y), boxWidth - (atX - x), fr, item.scale);

      box1X = atX;
      box1Y = atY;
      box2X = x;
      box2W = x + boxWidth;

      for (int i = 0; i < split.length; i++) {
        if (i == split.length - 1) {
          box3X = atX;
          box3Y = atY;
        }

        ITextComponent textComponent = split[i];
        drawScaledTextComponent(matrixStack, fr, textComponent, atX, atY, item.dropShadow, item.scale);

        if (i < split.length - 1) {
          atY += fr.FONT_HEIGHT;
          atX = x;
        }

        if (i == 0) {
          box2Y = atY;

          if (atX == x) {
            box1W = x + boxWidth;
          } else {
            box1W = atX;
          }
        }
      }

      box2H = atY;

      atX += fr.func_243245_a(split[split.length - 1].func_241878_f()) * item.scale;
      if (atX - x >= boxWidth) {
        atX = x;
        atY += fr.FONT_HEIGHT * item.scale;
      }

      box3W = atX;
      box3H = (int) (atY + fr.FONT_HEIGHT * item.scale);

      boolean mouseCheck = (mouseX >= box1X && mouseX <= box1W && mouseY >= box1Y && mouseY <= box1H && box1X != box1W && box1Y != box1H) || (mouseX >= box2X && mouseX <= box2W && mouseY >= box2Y && mouseY <= box2H && box2X != box2W && box2Y != box2H) || (mouseX >= box3X && mouseX <= box3W && mouseY >= box3Y && mouseY <= box3H && box3X != box3W && box1Y != box3H);

      if (item.tooltips != null && item.tooltips.length > 0) {
        // Uncomment to render bounding boxes for event handling
        if (BookScreen.debug) {
          drawGradientRect(box1X, box1Y, box1W, box1H, 0xFF00FF00, 0xFF00FF00);
          drawGradientRect(box2X, box2Y, box2W, box2H, 0xFFFF0000, 0xFFFF0000);
          drawGradientRect(box3X, box3Y, box3W, box3H, 0xFF0000FF, 0xFF0000FF);
          drawGradientRect(mouseX, mouseY, mouseX + 5, mouseY + 5, 0xFFFF00FF, 0xFFFFFF00);
        }

        if (mouseCheck) {
          tooltip.addAll(Arrays.asList(item.tooltips));
        }
      }

      if (item.action != null && !item.action.isEmpty()) {
        if (mouseCheck) {
          action = item.action;
        }
      }

      if (atY >= y + boxHeight) {
        if (item.dropShadow) {
          fr.drawStringWithShadow(matrixStack, "...", atX, atY, 0);
        } else {
          fr.drawString(matrixStack, "...", atX, atY, 0);
        }
        break;
      }

      y = atY;
    }

    if (BookScreen.debug && !action.isEmpty()) {
      tooltip.add(StringTextComponent.EMPTY);
      tooltip.add(new StringTextComponent("Action: " + action).mergeStyle(TextFormatting.GRAY));
    }

    return action;
  }

  /**
   * Translates the text inside of the given text component
   * Works with the siblings of the text component also.
   *
   * @param text the given text component to translate
   * @return the translated text component
   * @deprecated Remove since the text components passed to this method should be using TranslationTextComponent
   */
  @Deprecated
  private static ITextComponent translateTextComponent(ITextComponent text) {
    IFormattableTextComponent iformattabletextcomponent = text.copyRaw().setStyle(text.getStyle());

    String s = iformattabletextcomponent.getString();

    s = s.replace("$$(", "$\0(").replace(")$$", ")\0$");

    while (s.contains("$(") && s.contains(")$") && s.indexOf("$(") < s.indexOf(")$")) {
      String loc = s.substring(s.indexOf("$(") + 2, s.indexOf(")$"));
      s = s.replace("$(" + loc + ")$", I18n.format(loc));
    }

    if (s.indexOf("$(") > s.indexOf(")$") || s.contains(")$")) {
      Mantle.logger.error("[Books] [TextDataRenderer] Detected unbalanced localization symbols \"$(\" and \")$\" in string: \"" + s + "\".");
    }

    iformattabletextcomponent = new StringTextComponent(s).setStyle(text.getStyle());

    for (ITextComponent itextcomponent : text.getSiblings()) {
      iformattabletextcomponent.append(translateTextComponent(itextcomponent));
    }

    return iformattabletextcomponent;
  }

  /**
   * Crops text components to the given size for rendering
   *
   * @param textComponent the text component to crop
   * @param width         the width to use
   * @param height        the height to use
   * @param fr            the font renderer
   * @param scale         the scale of text to use
   * @return a list of text components that are cropped.
   */
  // TODO this does not actually crop the text components, fix?
  public static ITextComponent[] cropTextComponentsBySize(ITextComponent textComponent, int width, int height, FontRenderer fr, float scale) {
    return cropTextComponentsBySize(textComponent, width, height, width, fr, scale);
  }

  /**
   * Crops text components to the given size for rendering
   *
   * @param textComponent the text component to crop
   * @param width         the width to use
   * @param height        the height to use
   * @param firstWidth    the actual width to use
   * @param fontRenderer  the font renderer
   * @param scale         the scale of text to use
   * @return a list of text components which is what got clipped due to sizes
   */
  // TODO this does not actually crop the text components, fix?
  public static ITextComponent[] cropTextComponentsBySize(ITextComponent textComponent, int width, int height, int firstWidth, FontRenderer fontRenderer, float scale) {
    IFormattableTextComponent iformattabletextcomponent = cropTextComponentBySize(textComponent, width, height, firstWidth, fontRenderer, scale);

    for (ITextComponent itextcomponent : textComponent.getSiblings()) {
      iformattabletextcomponent.append(cropTextComponentBySize(itextcomponent, width, height, firstWidth, fontRenderer, scale));
    }

    List<ITextComponent> textComponents = new ArrayList<>();

    textComponents.add(iformattabletextcomponent);

    return textComponents.toArray(new ITextComponent[0]);
  }

  /**
   * Crops text components to the given size for rendering
   *
   * @param textComponent the text component to crop
   * @param width         the width to use
   * @param height        the height to use
   * @param firstWidth    the actual width to use
   * @param fontRenderer  the font renderer
   * @param scale         the scale of text to use
   * @return a text components which is what got clipped due to sizes
   */
  // TODO this does not actually crop the text components, fix?
  public static IFormattableTextComponent cropTextComponentBySize(ITextComponent textComponent, int width, int height, int firstWidth, FontRenderer fontRenderer, float scale) {
    int curWidth = 0;
    int curHeight = (int) (fontRenderer.FONT_HEIGHT * scale);
    IFormattableTextComponent iformattabletextcomponent = textComponent.copyRaw().setStyle(textComponent.getStyle());
    String text = iformattabletextcomponent.getString();

    for (int i = 0; i < text.length(); i++) {
      curWidth += fontRenderer.getStringWidth(String.valueOf(text.charAt(i))) * scale;

      if (text.charAt(i) == '\n' || (curHeight == (int) (fontRenderer.FONT_HEIGHT * scale) && curWidth > firstWidth) || (curHeight != (int) (fontRenderer.FONT_HEIGHT * scale) && curWidth > width)) {
        int oldI = i;
        if (text.charAt(i) != '\n') {
          while (i >= 0 && text.charAt(i) != ' ') {
            i--;
          }
          if (i <= 0) {
            i = oldI;
          }
        } else {
          oldI++;
        }

        text = text.substring(0, i) + "\r" + StringUtils.stripStart(text.substring(i + (i == oldI ? 0 : 1)), " ");

        i++;
        curWidth = 0;
        curHeight += fontRenderer.FONT_HEIGHT * scale;
      }
    }

    IFormattableTextComponent splitTextComponent = null;

    for (String s : text.split("\r")) {
      if (splitTextComponent == null)
        splitTextComponent = new StringTextComponent(s).setStyle(textComponent.getStyle());
      else
        splitTextComponent.append(new StringTextComponent(s).setStyle(textComponent.getStyle()));
    }

    if (splitTextComponent != null)
      return splitTextComponent;
    else
      return iformattabletextcomponent;
  }

  /**
   * Draws a text component with the given scale at the given position
   *
   * @param matrixStack   the given matrix stack used for rendering.
   * @param font          the font renderer to render with
   * @param textComponent the text component to render
   * @param x             the x position to render at
   * @param y             the y position to render at
   * @param dropShadow    if there should be a shadow on the text
   * @param scale         the scale to render as
   */
  public static void drawScaledTextComponent(MatrixStack matrixStack, FontRenderer font, ITextComponent textComponent, float x, float y, boolean dropShadow, float scale) {
    RenderSystem.pushMatrix();
    RenderSystem.translatef(x, y, 0);
    RenderSystem.scalef(scale, scale, 1F);

    if (dropShadow) {
      IReorderingProcessor ireorderingprocessor = textComponent.func_241878_f();
      font.func_238407_a_(matrixStack, ireorderingprocessor, 0, 0, 0);
    } else {
      font.func_243246_a(matrixStack, textComponent, 0, 0, 0);
    }

    RenderSystem.popMatrix();
  }

  /**
   * Draws a gradient box with the from the given points
   * Only used in debug
   *
   * @param left       the left position
   * @param top        the top position
   * @param right      the right position
   * @param bottom     the bottom position
   * @param startColor the start color to use
   * @param endColor   the end color to use
   */
  private static void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
    float f = (float) (startColor >> 24 & 255) / 255.0F;
    float f1 = (float) (startColor >> 16 & 255) / 255.0F;
    float f2 = (float) (startColor >> 8 & 255) / 255.0F;
    float f3 = (float) (startColor & 255) / 255.0F;
    float f4 = (float) (endColor >> 24 & 255) / 255.0F;
    float f5 = (float) (endColor >> 16 & 255) / 255.0F;
    float f6 = (float) (endColor >> 8 & 255) / 255.0F;
    float f7 = (float) (endColor & 255) / 255.0F;
    RenderSystem.disableTexture();
    RenderSystem.disableAlphaTest();
    RenderSystem.blendFuncSeparate(770, 771, 1, 0);
    RenderSystem.shadeModel(7425);
    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder vertexBuffer = tessellator.getBuffer();
    vertexBuffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
    vertexBuffer.pos((double) right, (double) top, 0D).color(f1, f2, f3, f).endVertex();
    vertexBuffer.pos((double) left, (double) top, 0D).color(f1, f2, f3, f).endVertex();
    vertexBuffer.pos((double) left, (double) bottom, 0D).color(f5, f6, f7, f4).endVertex();
    vertexBuffer.pos((double) right, (double) bottom, 0D).color(f5, f6, f7, f4).endVertex();
    tessellator.draw();
    RenderSystem.shadeModel(7424);
    RenderSystem.enableAlphaTest();
    RenderSystem.enableTexture();
  }
}
