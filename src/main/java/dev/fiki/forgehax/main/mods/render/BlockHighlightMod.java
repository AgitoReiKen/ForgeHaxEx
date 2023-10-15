package dev.fiki.forgehax.main.mods.render;

import dev.fiki.forgehax.api.cmd.settings.ColorSetting;
import dev.fiki.forgehax.api.cmd.settings.FloatSetting;
import dev.fiki.forgehax.api.color.Color;
import dev.fiki.forgehax.api.color.Colors;
import dev.fiki.forgehax.api.event.SubscribeListener;
import dev.fiki.forgehax.api.mod.Category;
import dev.fiki.forgehax.api.mod.ToggleMod;
import dev.fiki.forgehax.api.modloader.RegisterMod;
import dev.fiki.forgehax.asm.events.render.DrawBlockBoundingBoxEvent;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

@RegisterMod(
    name = "BlockHighlight",
    description = "Make selected block bounding box more visible",
    category = Category.RENDER
)
public class BlockHighlightMod extends ToggleMod {

  private final ColorSetting color = newColorSetting()
      .name("color")
      .description("Block highlight color")
      .defaultTo(Color.of(255,255,255, 127))
      .build();

  private final FloatSetting width = newFloatSetting()
      .name("width")
      .description("line width")
      .min(0.f)
      .defaultTo(5.f)
      .build();

//  @SubscribeListener
//  public void onRenderBoxPre(DrawBlockBoundingBoxEvent.Pre event) {
//    GlStateManager._disableDepthTest();
//    GlStateManager._lineWidth(width.getValue());
//    Color c = color.getValue();
//    event.setAlpha(c.getAlphaAsFloat());
//    event.setRed(c.getRedAsFloat());
//    event.setBlue(c.getBlueAsFloat());
//    event.setGreen(c.getGreenAsFloat());
//  }
//  @SubscribeListener
//  public void onRenderBoxPost(DrawBlockBoundingBoxEvent.Post event) {
//    GlStateManager._enableDepthTest();
//  }
}
