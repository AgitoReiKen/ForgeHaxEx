package dev.fiki.forgehax.api.mod;

import dev.fiki.forgehax.api.cmd.ICommand;
import dev.fiki.forgehax.api.cmd.settings.KeyBindingSetting;
import dev.fiki.forgehax.api.key.KeyConflictContexts;
import lombok.AccessLevel;
import lombok.Getter;
import net.minecraft.client.settings.KeyBinding;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

@Getter(AccessLevel.PROTECTED)
public abstract class KeyBoundMod extends AbstractMod {
  private final KeyBindingSetting keyBindingSetting = newKeyBindingSetting()
      .name("bind")
      .description("Key bind to enable the mod")
      .unbound()
      .defaultKeyName()
      .defaultKeyCategory()
      .conflictContext(KeyConflictContexts.inGame())
      .keyDownListener(this::onKeyDown)
      .keyPressedListener(this::onKeyPressed)
      .keyReleasedListener(this::onKeyReleased)
      .build();

  {
    newSimpleCommand()
        .name("unbind")
        .description("Unbind the key this mod is set to")
        .executor(args -> keyBindingSetting.unbind())
        .build();
  }

  public KeyBoundMod() {
    super();
    newSimpleCommand()
        .name("help")
        .alias("info")
        .description("Shows information about mod commands and variables")
        .executor((x) -> {
          StringBuilder string = new StringBuilder();
          ArrayList<String> processed = new ArrayList<>();
          for (Map.Entry<String, ICommand> entry : this.subCommands.entrySet()) {
            ICommand cmd = entry.getValue();
            if (processed.containsAll(cmd.getNameAndAliases())) continue;
            processed.addAll(cmd.getNameAndAliases());
            string.append(String.format("%s - %s\n",
                cmd.getNameAndAliases().stream().collect(Collectors.joining(",")).toString(),
                cmd.getDescription())
            );
          }
          x.inform(string.toString());
        }).build();
  }

  public abstract void onKeyPressed(KeyBinding key);

  public abstract void onKeyDown(KeyBinding key);

  public abstract void onKeyReleased(KeyBinding key);
}
