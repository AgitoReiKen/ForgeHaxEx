package dev.fiki.forgehax.api.events.entity;

import dev.fiki.forgehax.api.event.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GameRenderer;

@Getter
@AllArgsConstructor
public class EntityRenderEvent extends Event {
  private final GameRenderer renderer;
  private final ActiveRenderInfo info;
}
