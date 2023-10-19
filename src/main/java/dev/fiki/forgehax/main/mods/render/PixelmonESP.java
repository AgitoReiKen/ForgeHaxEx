package dev.fiki.forgehax.main.mods.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.api.pokemon.species.Pokedex;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.stats.*;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.entities.WormholeEntity;
import com.pixelmonmod.pixelmon.entities.npcs.NPCTrainer;
import dev.fiki.forgehax.api.cmd.AbstractParentCommand;
import dev.fiki.forgehax.api.cmd.ICommand;
import dev.fiki.forgehax.api.cmd.IParentCommand;
import dev.fiki.forgehax.api.cmd.argument.Arguments;
import dev.fiki.forgehax.api.cmd.argument.IArgument;
import dev.fiki.forgehax.api.cmd.flag.EnumFlag;
import dev.fiki.forgehax.api.cmd.listener.ICommandListener;
import dev.fiki.forgehax.api.cmd.listener.IOnUpdate;
import dev.fiki.forgehax.api.cmd.settings.*;
import dev.fiki.forgehax.api.cmd.settings.collections.SimpleSettingList;
import dev.fiki.forgehax.api.color.Color;
import dev.fiki.forgehax.api.color.Colors;
import dev.fiki.forgehax.api.common.PriorityEnum;
import dev.fiki.forgehax.api.draw.GeometryMasks;
import dev.fiki.forgehax.api.draw.RenderTypeEx;
import dev.fiki.forgehax.api.draw.Render2D;
import dev.fiki.forgehax.api.event.SubscribeListener;
import dev.fiki.forgehax.api.events.render.RenderPlaneEvent;
import dev.fiki.forgehax.api.events.render.RenderSpaceEvent;
import dev.fiki.forgehax.api.extension.EntityEx;
import dev.fiki.forgehax.api.extension.StringEx;
import dev.fiki.forgehax.api.extension.VectorEx;
import dev.fiki.forgehax.api.extension.VertexBuilderEx;
import dev.fiki.forgehax.api.math.ScreenPos;
import dev.fiki.forgehax.api.math.VectorUtil;
import dev.fiki.forgehax.api.mod.AbstractMod;
import dev.fiki.forgehax.api.mod.Category;
import dev.fiki.forgehax.api.mod.ToggleMod;
import dev.fiki.forgehax.api.modloader.RegisterMod;
import dev.fiki.forgehax.main.services.GuiService;
import lombok.*;
import lombok.experimental.ExtensionMethod;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import org.apache.commons.lang3.tuple.MutablePair;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;

import static com.mojang.blaze3d.systems.RenderSystem.disableBlend;
import static com.mojang.blaze3d.systems.RenderSystem.enableTexture;
import static dev.fiki.forgehax.main.Common.*;
import static org.lwjgl.opengl.GL11.*;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import org.lwjgl.opengl.GL11;

@RegisterMod(
    name = "PixelmonESP",
    description = "Shows pokemons locations and info",
    category = Category.RENDER
)
@ExtensionMethod({StringEx.class, EntityEx.class, VectorEx.class, VertexBuilderEx.class})
public class PixelmonESP extends ToggleMod {
  ArrayList<String> starterPokemon = new ArrayList<>(
      Arrays.asList(
          "Bulbasaur", "Charmander", "Squirtle",   // Generation I
          "Chikorita", "Cyndaquil", "Totodile",   // Generation II
          "Treecko", "Torchic", "Mudkip",         // Generation III
          "Turtwig", "Chimchar", "Piplup",        // Generation IV
          "Snivy", "Tepig", "Oshawott",           // Generation V
          "Chespin", "Fennekin", "Froakie",       // Generation VI
          "Rowlet", "Litten", "Popplio",          // Generation VII
          "Grookey", "Scorbunny", "Sobble",        // Generation VIII
          "Sprigatito", "Fuecoco ", "Quaxly"       // Generation IX
      ));
  ArrayList<String> rarePokemon = new ArrayList<>(
      Arrays.asList(
          "Eevee", "Lapras", "Dratini", "Larvitar", "Bagon", "Gible", "Axew", "Deino",
          "Trapinch", "Feebas", "Spiritomb", "Porygon", "Tropius", "Snorlax", "Riolu",
          "Beldum", "Cranidos", "Shieldon", "Gligar", "Skarmory", "Heracross", "Scyther",
          "Pinsir", "Chansey", "Kangaskhan", "Aerodactyl", "Togepi", "Ditto", "Sudowoodo",
          "Qwilfish", "Corsola", "Delibird", "Sableye", "Mawile", "Plusle", "Minun",
          "Roselia", "Absol", "Relicanth", "Luvdisc", "Chingling", "Mantyke", "Snorunt",
          "Glalie", "Froslass", "Vanillite", "Vanillish", "Vanilluxe", "Durant", "Heatmor",
          "Maractus", "Solrock", "Lunatone", "Hawlucha", "Druddigon", "Bouffalant", "Stantler",
          "Smeargle", "Kecleon", "Chatot", "Basculin", "Alomomola"
      )
  );

  SimpleSettingList<String> search = newSimpleSettingList(String.class)
      .name("search")
      .description("Searches for pokemon names, case insensitive")
      .supplier(ArrayList::new)
      .listener(new SearchListener())
      .build();
  BooleanSetting starter = newBooleanSetting()
      .name("starter")
      .description("Look for starters")
      .defaultTo(true)
      .build();
  BooleanSetting rarity = newBooleanSetting()
      .name("rarity")
      .description("Look for rarities")
      .defaultTo(true)
      .build();
  ContainedSimpleSettingList<String> starters = newContainedListSetting(String.class)
      .name("starters")
      .description("Starters list")
      .argument(Arguments.newStringArgument().label("name").build())
      .supplier(ArrayList::new)
      .listener(new StartersListener())
      .defaultTo(starterPokemon)
      .build();
  ContainedSimpleSettingList<String> rarities = newContainedListSetting(String.class)
      .name("rarities")
      .description("Rarities list")
      .argument(Arguments.newStringArgument().label("name").build())
      .supplier(ArrayList::new)
      .listener(new RaritiesListener())
      .defaultTo(rarePokemon)
      .build();
  ColorPaletteSettings colorPalette = newColorPalette()
      .name("colors")
      .description("Color palette")
      .build();

  DrawSettings drawSettings = newDrawSettings()
      .name("draw")
      .description("Draw settings")
      .build();

  FormatSettings formatSettings = newFormatSettings()
      .name("format")
      .description("Format settings")
      .build();
  ArrayList<RegistryValue<Species>> searchSpecies = new ArrayList<>();
  ArrayList<RegistryValue<Species>> starterSpecies = new ArrayList<>();
  ArrayList<RegistryValue<Species>> rareSpecies = new ArrayList<>();
  Field bossTierField = null;

  public void PixelmonESP() {

  }

  @Override
  public void onLoad() {
    try {
      bossTierField = Pokemon.class.getDeclaredField("bossTier");
      bossTierField.setAccessible(true);
    } catch (NoSuchFieldException e) {
      log.info("Couldn't find Pokemon.bossTier field");
    }
    new SearchListener().onUpdate(null);
    new StartersListener().onUpdate(null);
    new RaritiesListener().onUpdate(null);
  }

  boolean isPlayerLookingAt(Entity ent, AxisAlignedBB box, Vector3d bottomPos) {
    final Vector3d topPos = bottomPos.add(0.D, box.maxY - ent.getY(), 0.D);

    final ScreenPos top = topPos.toScreen();
    final ScreenPos bot = bottomPos.toScreen();

    if (!top.isVisible() && !bot.isVisible()) {
      return false;
    }
    double topX = top.getX();
    double topY = top.getY() + 1.D;
    double botX = bot.getX();
    double botY = bot.getY() + 1.D;
    float screenWidth = getScreenWidth();
    float screenHeight = getScreenHeight();
    float cx = screenWidth / 2.f;
    float cy = screenHeight / 2.f;
    //float distance = screenWidth / 16.f;
    boolean nearX = cx >= topX && cx <= botX;
    boolean nearY = cy >= topY && cy <= botY;

    return nearX || nearY;
  }

  Priority getPriorityForBossTier(String bossTierID, boolean isMega) {
    Priority priority;
    if ("common".equals(bossTierID)) {
      priority = Priority.LOWEST;
    } else if ("uncommon".equals(bossTierID)) {
      priority = Priority.LOWEST;
    } else if ("rare".equals(bossTierID)) {
      priority = Priority.LOW;
    } else if ("epic".equals(bossTierID)) {
      priority = Priority.BELOW_AVERAGE;
    } else if ("legendary".equals(bossTierID)) {
      priority = Priority.AVERAGE;
    } else if ("ultimate".equals(bossTierID)) {
      priority = Priority.ABOVE_AVERAGE;
    } else if ("spooky".equals(bossTierID)) {
      priority = Priority.ABOVE_AVERAGE;
    } else if ("drowned".equals(bossTierID)) {
      priority = Priority.LOW;
    } else {
      priority = isMega ? Priority.HIGH : Priority.NONE;
    }
    return priority;
  }

  void makePokemonInfo(Pokemon ent, DrawData out) {
    out.drawInfo = true;

    final PermanentStats stats = ent.getStats();
    final IVStore ivs = stats.getIVs();
    final EVStore evs = stats.getEVs();
    HashMap<String, Object> statArguments = new HashMap<String, Object>();
    statArguments.put("hp", stats.getHP());
    statArguments.put("atk", stats.getAttack());
    statArguments.put("def", stats.getDefense());
    statArguments.put("sat", stats.getSpecialAttack());
    statArguments.put("sdf", stats.getSpecialDefense());
    statArguments.put("spd", stats.getSpeed());

    statArguments.put("ivhp", ivs.getStat(BattleStatsType.HP));
    statArguments.put("ivatk", ivs.getStat(BattleStatsType.ATTACK));
    statArguments.put("ivdef", ivs.getStat(BattleStatsType.DEFENSE));
    statArguments.put("ivsat", ivs.getStat(BattleStatsType.SPECIAL_ATTACK));
    statArguments.put("ivsdf", ivs.getStat(BattleStatsType.SPECIAL_DEFENSE));
    statArguments.put("ivspd", ivs.getStat(BattleStatsType.SPEED));

    statArguments.put("evhp", evs.getStat(BattleStatsType.HP));
    statArguments.put("evatk", evs.getStat(BattleStatsType.ATTACK));
    statArguments.put("evdef", evs.getStat(BattleStatsType.DEFENSE));
    statArguments.put("evsat", evs.getStat(BattleStatsType.SPECIAL_ATTACK));
    statArguments.put("evsdf", evs.getStat(BattleStatsType.SPECIAL_DEFENSE));
    statArguments.put("evspd", evs.getStat(BattleStatsType.SPEED));

    String[] statsStr = formatSettings.statFormat.getValue().format(statArguments).split("\n");

    HashMap<String, Object> skillArguments = new HashMap<String, Object>();
    skillArguments.put("0", "");
    skillArguments.put("1", "");
    skillArguments.put("2", "");
    skillArguments.put("3", "");
    final Moveset moveset = ent.getMoveset();

    for (int i = 0; i < moveset.attacks.length; ++i) {
      skillArguments.put((new Integer(i)).toString(), moveset.attacks[i].toString());
    }

    String[] skillsStr = formatSettings.skillFormat.getValue().format(skillArguments).split("\n");

    for (String s : statsStr) {
      out.text.add(new MutablePair<>(s, Colors.WHITE_SMOKE));
    }
    for (String s : skillsStr) {
      out.text.add(new MutablePair<>(s, Colors.WHITE_SMOKE));
    }

  }

  DrawData getDrawData(Entity ent, float partialTicks) {

    DrawData data = new DrawData();
//    float x = (float) (Render2D.getStringWidth(name) / 2.f);
//    float y = (float) Render2D.getStringHeight();

    if (ent instanceof PixelmonEntity) {
      PixelmonEntity pokemonEntity = (PixelmonEntity) ent;
      Pokemon pokemon = pokemonEntity.getPokemon();
      Pokedex.loadPokedex();
      NPCTrainer npcTrainer = pokemon.getOwnerTrainer();
      BossTier bossTier = null;
      try {
        bossTier = bossTierField != null ? (BossTier) bossTierField.get(pokemon) : null;
      } catch (Throwable exception) {
      }
      Vector3d bottomPos = ent.getInterpolatedPos(partialTicks);
      data.entityBottomPos = bottomPos;
      AxisAlignedBB box = ent.getBoundingBox();
      Vector3d center = new Vector3d(bottomPos.x + box.getXsize() * .5, bottomPos.y + box.getYsize() * .5, bottomPos.z + box.getZsize() * .5);
      ScreenPos screenPos = VectorUtil.toScreen(center);
      data.entityScreenPos = screenPos;
      boolean isLookingAt = isPlayerLookingAt(ent, box, bottomPos);
      int distanceTo = (int) getGameRenderer().getMainCamera().getPosition().distanceTo(bottomPos);
      isLookingAt &= distanceTo <= drawSettings.maxLookingDistance.getValue();

      String pokemonName = pokemon.getSpecies().getName();
      int level = pokemon.getPokemonLevel();
      boolean isLegendary = pokemon.isLegendary();
      boolean isUltraBeast = pokemon.isUltraBeast();
      boolean isWild = pokemon.getOwnerPlayer() == null;
      boolean isTrainer = npcTrainer != null;
      boolean isShiny = pokemon.getPalette().getName().equals("shiny");
      boolean isBoss = bossTier != null && bossTier.isBoss();
      boolean isCatchable = !pokemon.isUncatchable();
      val registry = pokemon.getSpecies().getRegistryValue();
      boolean isSearching = searchSpecies.contains(registry);
      boolean isStarter = false;
      boolean isRare = false;
      boolean isRaid = pokemonEntity.isRaidPokemon();
      if (!isSearching && starter.getValue()) isStarter = starterSpecies.contains(registry);
      if (!isSearching && !isStarter && rarity.getValue()) isRare = rareSpecies.contains(registry);
      int infoPriority = drawSettings.info.getValue().value;


      String bossRarity = isBoss ? bossTier.getName() : "";
      HashMap<String, Object> nameArguments = new HashMap<String, Object>();
      nameArguments.put("name", pokemonName);
      nameArguments.put("lvl", level);
      nameArguments.put("dist", distanceTo);
      String name = null;
      if (isBoss || isTrainer) {
        nameArguments.put("rarity", bossRarity);
        name = formatSettings.nameFormat.getValue().format(nameArguments);
      } else {
        name = formatSettings.bossNameFormat.getValue().format(nameArguments);
      }
      if (!isWild) {
        if (isLookingAt && drawSettings.infoAllowedOnPlayers.getValue()) {

          data.text.add(new MutablePair<>(name, Colors.WHITE));
          makePokemonInfo(pokemon, data);
        }
        return data;
      }

      if (isTrainer) {
        BossTier npcBossTier = npcTrainer.getBossTier();
        String bossTierID = npcBossTier.getID();
        data.priority = getPriorityForBossTier(bossTierID, npcBossTier.isMega());
        data.text.add(new MutablePair<>(name, Color.of(npcBossTier.getColor().getRGB())));
        return data;
      }
      if (isBoss) {
        String bossTierID = bossTier.getID();
        data.priority = getPriorityForBossTier(bossTierID, bossTier.isMega());
        data.text.add(new MutablePair<>(name, Color.of(bossTier.getColor().getRGB())));
        if (isLookingAt && data.priority.value <= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }

        return data;
      }
      if (isRare) {
        data.priority = Priority.LOW;
        data.text.add(new MutablePair<>(name, colorPalette.rare.getValue()));
        if (isLookingAt && data.priority.value <= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
        return data;
      }
      if (isStarter) {
        data.priority = Priority.LOW;
        data.text.add(new MutablePair<>(name, colorPalette.starter.getValue()));
        if (isLookingAt && data.priority.value <= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
        return data;
      }
      if (isShiny) {
        data.priority = Priority.AVERAGE;
        data.text.add(new MutablePair<>(name, colorPalette.shiny.getValue()));
        if (isLookingAt && data.priority.value <= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
        return data;
      }
      if (isSearching) {
        data.priority = Priority.AVERAGE;
        data.text.add(new MutablePair<>(name, colorPalette.search.getValue()));
        if (isLookingAt && data.priority.value <= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
        return data;
      }
      if (isLegendary) {
        data.priority = Priority.CRITICAL;
        data.text.add(new MutablePair<>(name, colorPalette.legendary.getValue()));
        if (isLookingAt && data.priority.value <= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
        return data;
      }
      if (isUltraBeast) {
        data.priority = Priority.CRITICAL;
        data.text.add(new MutablePair<>(name, colorPalette.ultrabeast.getValue()));
        if (isLookingAt && data.priority.value <= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
        return data;
      }
      if (isRaid) {
        data.priority = Priority.HIGH;
        data.text.add(new MutablePair<>(name, colorPalette.ultrabeast.getValue()));
        if (isLookingAt && data.priority.value <= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
        return data;
      }
      if (data.priority == Priority.NONE) {
        data.text.add(new MutablePair<>(name, colorPalette.common.getValue()));
        data.priority = Priority.LOWEST;
      }
      String longestText = "";
      for (MutablePair<String, Color> text : data.text) {
        if (text.left.length() > longestText.length())
          longestText = text.left;
      }
      double textWidth = Render2D.getStringWidth(longestText) + (drawSettings.boxWidthMargin * 2);
      double textHeight = (Render2D.getStringHeight() + (drawSettings.boxHeightMargin * 2)) * (data.text.size());
      data.background.right = isCatchable ? colorPalette.catchable.getValue() : colorPalette.uncatchable.getValue();
      data.background.left = new Vector2f((float) textWidth, (float) textHeight);
      data.drawWidget = !data.drawInfo &&
          (drawSettings.widget.getValue().value  != Priority.NONE.value) &&
          (data.priority.value <= drawSettings.widget.getValue().value);
      data.drawBox = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value <= drawSettings.box.getValue().value);
      data.drawTrace = (drawSettings.trace.getValue().value != Priority.NONE.value) &&
          (data.priority.value <= drawSettings.trace.getValue().value);
      data.drawName = (drawSettings.names.getValue().value != Priority.NONE.value) &&
          (data.priority.value <= drawSettings.names.getValue().value);

      if (data.drawTrace)
      {
        data.trace.left = data.priority.value <= Priority.BELOW_AVERAGE.value
            ? drawSettings.lineThicknessBelowAverage.getValue()
            : drawSettings.lineThicknessAboveAverage.getValue();
        data.trace.right = data.text.get(0).right.setAlpha(drawSettings.traceOpacity.getValue());
      }
      if (data.drawBox)
      {
        data.box.left = box;
        data.box.right = data.text.get(0).right.setAlpha(drawSettings.boxOpacity.getValue());
      }
    }
    else if (ent instanceof WormholeEntity) {
      Vector3d bottomPos = ent.getInterpolatedPos(partialTicks);
      data.entityBottomPos = bottomPos;
      AxisAlignedBB box = ent.getBoundingBox();
      Vector3d center = new Vector3d(bottomPos.x + box.getXsize() * .5, bottomPos.y + box.getYsize() * .5, bottomPos.z + box.getZsize() * .5);
      ScreenPos screenPos = VectorUtil.toScreen(center);
      data.entityScreenPos = screenPos;
      data.priority = Priority.BELOW_AVERAGE;
      data.drawWidget = (drawSettings.widget.getValue().value  != Priority.NONE.value) &&
          (data.priority.value <= drawSettings.widget.getValue().value);
      data.drawBox = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value <= drawSettings.box.getValue().value);
      data.drawTrace = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value <= drawSettings.trace.getValue().value);
      data.drawName = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value <= drawSettings.names.getValue().value);
      data.text.add(new MutablePair<>("Wormhole", colorPalette.wormhole.getValue()));

      if (data.drawTrace)
      {
        data.trace.left = data.priority.value <= Priority.BELOW_AVERAGE.value
            ? drawSettings.lineThicknessBelowAverage.getValue()
            : drawSettings.lineThicknessAboveAverage.getValue();
        data.trace.right = data.text.get(0).right.setAlpha(drawSettings.traceOpacity.getValue());
      }
      if (data.drawBox)
      {
        data.box.left = box;
        data.box.right = data.text.get(0).right.setAlpha(drawSettings.boxOpacity.getValue());
      }
    }
    return data;
  }
  ArrayList<DrawData> cachedDrawData = new ArrayList<DrawData>();
  private long lastUpdate = 0;
  @SubscribeListener
  public void onRender(RenderSpaceEvent event) {
    if (!this.isEnabled()) return;
    RenderSystem.enableBlend();
    BufferBuilder buffer = event.getBuffer();
    val stack = event.getStack();
    stack.pushPose();
    stack.translateVec(event.getProjectedPos().scale(-1));
    buffer.beginQuads(DefaultVertexFormats.POSITION_COLOR);

    final int sides = GeometryMasks.Quad.ALL;
    for (DrawData data : cachedDrawData)
    {
      if (!data.drawBox)
      buffer.filledCube(data.box.left, sides, data.box.right, stack.getLastMatrix());
    }
    buffer.draw();
    stack.popPose();
  }
  @SubscribeListener(priority = PriorityEnum.LOW)
  public void onRender2D(final RenderPlaneEvent.Back event) throws IllegalAccessException {
    final float partialTicks = event.getPartialTicks();
    final double screenWidth = event.getScreenWidth();
    final double screenHeight = event.getScreenHeight();
    val buffers = getBufferProvider();
    val triangles = getBufferProvider().getBuffer(RenderTypeEx.glTriangle());
    val quads = getBufferProvider().getBuffer(RenderTypeEx.glQuads());
    val stack = event.getStack();
    val source = buffers.getBufferSource();
    ArrayList<DrawData> drawDatas = new ArrayList<>();
    DrawData info = new DrawData();
    final ArrayList<DrawData> widgets = new ArrayList<>();
    final ArrayList<DrawData> boxes = new ArrayList<>();
    final ArrayList<DrawData> names = new ArrayList<>();
    final ArrayList<DrawData> traces = new ArrayList<>();
    int maxWidgets = 4;
    long now = System.currentTimeMillis();


    boolean shouldUpdate = true;
    if (now >= lastUpdate + 64) {
      lastUpdate = now + 64;
      shouldUpdate = true;
    }

    if (shouldUpdate) {
      // distance to chunk
      cachedDrawData.clear();
      for (Entity ent : getWorld().entitiesForRendering()) {
        cachedDrawData.add(getDrawData(ent, partialTicks));
      }
    }
    for (DrawData drawData : cachedDrawData) {
      if (drawData.drawInfo && info.priority.value < drawData.priority.value )
      {
        info = drawData;
      }
      if (drawData.drawTrace)
      {
        traces.add(drawData);
      }
      if (drawData.drawBox)
      {
        boxes.add(drawData);
      }
      if (drawData.drawName)
      {
        names.add(drawData);
      }
      if (drawData.drawWidget)
      {
        if (widgets.size() >= maxWidgets)
        {
          for (int i = 0; i < widgets.size(); i++) {
            if (widgets.get(i).priority.value < drawData.priority.value) {
              widgets.set(i, drawData);
              break;
            }
          }
        }
        widgets.add(drawData);
      }
    }

    if (info.drawInfo)
    {
      float maxWidth = 0;
      final Vector2f size = info.background.left;

      Vector2f baseOffset = new Vector2f(
          drawSettings.infoOffset.x.getValue() - (size.x  * .5f),
          drawSettings.infoOffset.y.getValue()
      );

      float textHeight = (size.y - 4) / info.text.size();

      float textOffset = textHeight + drawSettings.boxHeightMargin;
      float drawAtX = baseOffset.x;
      float drawAtY = baseOffset.y;
      Render2D.rect(quads, drawAtX, drawAtY, size.x, size.y, info.background.right);
      for (val text : info.text) {
        Render2D.renderString(triangles, text.left, drawAtX + drawSettings.boxWidthMargin, drawAtY + textOffset, text.right, true);
        textOffset+=textOffset;
      }
    }
    else {
      float offsetY = 0;
      float maxWidth = 0;
      for (DrawData widget : widgets) {
        maxWidth = Math.max(maxWidth, widget.background.left.x);
      }
      Vector2f baseOffset = new Vector2f(
          drawSettings.widgetOffset.x.getValue() - (maxWidth * .5f),
          drawSettings.widgetOffset.y.getValue()
      );
      widgets.sort(Comparator.comparingInt(a -> a.priority.value));

      for (int i = 0; i < widgets.size(); i++) {
        final DrawData data = widgets.get(i);
        final Vector2f size = data.background.left;
        float drawAtX = baseOffset.x;
        float drawAtY = baseOffset.y + offsetY;
        val text = data.text.get(0);
        Render2D.rect(quads, drawAtX, drawAtY, size.x, size.y, data.background.right);
        Render2D.renderString(triangles, text.left, drawAtX + drawSettings.boxWidthMargin, drawAtY + drawSettings.boxHeightMargin, text.right, true);
        offsetY += drawSettings.boxHeightMargin + size.y;
      }
    }
    source.endBatch();

    for (DrawData data : names)
    {
      val name = data.text.get(0);
      ScreenPos pos = data.entityScreenPos;
      double width = Render2D.getStringWidth(name.left);

      stack.pushPose();

      float x = (float) (width / 2.f);
      float y = (float) Render2D.getStringHeight();

      stack.scale(1.0f, 1.0f, 0.f);
      stack.translate(-x, -y, 0.d);

      Render2D.renderString(triangles, name.left,
              (float) pos.getX(), (float) pos.getY(), name.right, true);

      stack.popPose();
    }
    source.endBatch();

    final double cx = event.getScreenWidth() / 2.f;
    final double cy = event.getScreenHeight() / 2.f;
    for (DrawData data : traces)
    {
      val trace = data.trace;
      glColor4f(trace.right.getRedAsFloat(),
          trace.right.getGreenAsFloat(),
          trace.right.getBlueAsFloat(),
          trace.right.getAlphaAsFloat());
      glBegin(GL_QUADS);
      {
        GL11.glLineWidth(trace.left);
        GL11.glVertex2d(cx, cy);
        GL11.glVertex2d(data.entityScreenPos.getX(), data.entityScreenPos.getY());
      }
      glEnd();
    }
    source.endBatch();
    enableTexture();
    disableBlend();
    GL11.glColor4i(255, 255, 255, 255);
    RenderHelper.setupFor3DItems();
  }

  ///////////// Structures

  public class DrawData {
    public boolean drawWidget;
    public boolean drawTrace;
    public boolean drawBox;
    public boolean drawName;
    public boolean drawInfo;
    public ArrayList<MutablePair<String, Color>> text;
    public MutablePair<Vector2f, Color> background;
    public ScreenPos entityScreenPos;
    public Vector3d entityBottomPos;

    public MutablePair<AxisAlignedBB, Color> box;
    public MutablePair<Float, Color> trace;
    Priority priority;

    DrawData() {
      drawWidget = false;
      drawTrace = false;
      drawBox = false;
      drawName = false;
      text = new ArrayList<>();
      background = new MutablePair<>();
      entityScreenPos = new ScreenPos(-1, -1, false);
      box = new MutablePair<>();
      trace = new MutablePair<>();
      priority = Priority.NONE;
      entityBottomPos = new Vector3d(-1, -1, -1);
    }
  }

  //  public static class Priority{
//    static final int LOWEST = 0;
//    static final int LOW = 5;
//    static final int BELOW_AVERAGE = 10;
//    static  final int AVERAGE = 15;
//    static final int ABOVE_AVERAGE = 20;
//    static final int HIGH = 25;
//    static final int ESSENTIAL = 30;
//    static final int CRITICAL = 35;
//    static final int VITAL = 40;
//  }
  public enum Priority {
    NONE(-1),
    LOWEST(0),
    LOW(5),
    BELOW_AVERAGE(10),
    AVERAGE(15),
    ABOVE_AVERAGE(20),
    HIGH(25),
    ESSENTIAL(30),
    CRITICAL(35),
    VITAL(40);

    private final int value;

    Priority(int value) {
      this.value = value;
    }

    // Custom method to get the string representation based on the integer value
    public static String getStringValue(int intValue) {
      for (Priority en : Priority.values()) {
        if (en.value == intValue) {
          return en.toString();
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  public class SearchListener implements IOnUpdate {
    public void onUpdate(ICommand command) {
      searchSpecies.clear();
      Optional<AbstractMod> guiService = getForgeHax().getModManager().getMods().filter(mod -> mod instanceof GuiService).findFirst();
      GuiService gui = (GuiService) guiService.orElse(null);
      search.forEach(x -> {
        Optional<RegistryValue<Species>> registry = PixelmonSpecies.get(x);
        if (!registry.isPresent()) {
          gui.getConsole().addMessage(String.format("Couldn't find %s", x));
          return;
        }
        searchSpecies.add(registry.get());
      });
      gui.getConsole().addMessage(String.format("Looking for %d pokemons", searchSpecies.size()));
    }
  }

  public class StartersListener implements IOnUpdate {
    public void onUpdate(ICommand command) {
      Optional<AbstractMod> guiService = getForgeHax().getModManager().getMods().filter(mod -> mod instanceof GuiService).findFirst();
      GuiService gui = guiService.isPresent() ? (GuiService) guiService.get() : null;
      starters.list.forEach(x -> {
        Optional<RegistryValue<Species>> registry = PixelmonSpecies.get((String) x);
        if (!registry.isPresent()) {
          gui.getConsole().addMessage(String.format("Couldn't find %s", x));
          return;
        }
        starterSpecies.add(registry.get());
      });
    }
  }

  public class RaritiesListener implements IOnUpdate {
    public void onUpdate(ICommand command) {
      Optional<AbstractMod> guiService = getForgeHax().getModManager().getMods().filter(mod -> mod instanceof GuiService).findFirst();
      GuiService gui = guiService.isPresent() ? (GuiService) guiService.get() : null;
      rarities.list.forEach(x -> {
        Optional<RegistryValue<Species>> registry = PixelmonSpecies.get((String) x);
        if (!registry.isPresent()) {
          gui.getConsole().addMessage(String.format("Couldn't find %s", x));
          return;
        }
        rareSpecies.add(registry.get());
      });
    }
  }

  private <T> ContainedSimpleSettingList.ContainedSimpleSettingListBuilder<T> newContainedListSetting(Class<T> cls) {
    return ContainedSimpleSettingList.<T>builder().parent(this);
  }

  private ColorPaletteSettings.ColorPaletteSettingsBuilder newColorPalette() {
    return ColorPaletteSettings.builder().parent(this);
  }

  private FormatSettings.FormatSettingsBuilder newFormatSettings() {
    return FormatSettings.builder().parent(this);
  }

  private DrawSettings.DrawSettingsBuilder newDrawSettings() {
    return DrawSettings.builder().parent(this);
  }



  @Getter
  @Log4j2
  private static class ContainedSimpleSettingList<T> extends AbstractParentCommand {

    public final SimpleSettingList<T> list;


    @Builder
    public ContainedSimpleSettingList(IParentCommand parent,
                                      String name, @Singular Collection<String> aliases, String description,
                                      @Singular Collection<EnumFlag> flags,
                                      @NonNull Supplier<List<T>> supplier,
                                      @Singular("defaultsTo") Collection<T> defaultTo,
                                      @NonNull IArgument<T> argument,
                                      @Singular List<ICommandListener> listeners) {
      super(parent, name, aliases, description, flags);
      log.info("ContainedSimpleSettingList 0");

      list = SimpleSettingList.<T>builder().parent(this)
          .name("list")
          .description("")
          .supplier(supplier)
          .argument(argument)
          .defaultTo(defaultTo)
          .listeners(listeners)
          .build();


      onFullyConstructed();
      log.info("ContainedSimpleSettingList 1");

    }
  }

  @Getter
  @Log4j2
  private static class ColorPaletteSettings extends AbstractParentCommand {

    public final ColorSetting shiny;
    public final ColorSetting legendary;
    public final ColorSetting ultrabeast;
    public final ColorSetting search;
    public final ColorSetting starter;
    public final ColorSetting rare;
    public final ColorSetting common;
    public final ColorSetting catchable;
    public final ColorSetting uncatchable;
    public final ColorSetting wormhole;
    public final ColorSetting raid;


    @Builder
    public ColorPaletteSettings(IParentCommand parent,
                                String name, @Singular Collection<String> aliases, String description,
                                @Singular Collection<EnumFlag> flags) {
      super(parent, name, aliases, description, flags);
      log.info("ColorPaletteSettings 0");
      shiny = newColorSetting().name("shiny").description("").defaultTo(Colors.LIGHT_CORAL).build();
      legendary = newColorSetting().name("legendary").description("").defaultTo(Colors.MEDIUM_ORCHID).build();
      search = newColorSetting().name("search").description("").defaultTo(Colors.LIGHT_BLUE).build();
      ultrabeast = newColorSetting().name("ultrabeast").description("").defaultTo(Colors.MEDIUM_ORCHID).build();
      starter = newColorSetting().name("starter").description("").defaultTo(Colors.DARK_GOLDEN_ROD).build();
      rare = newColorSetting().name("rare").description("").defaultTo(Colors.SIENNA).build();
      wormhole = newColorSetting().name("wormhole").description("").defaultTo(Colors.DARK_MAGENTA).build();
      raid = newColorSetting()
          .name("raid")
          .description("")
          .defaultTo(Colors.DARK_RED)
          .build();
      common = newColorSetting()
          .name("common")
          .description("")
          .defaultTo(Color.of(1.0, 1.0, 1.0, 0.25))
          .build();
      catchable = newColorSetting()
          .name("catchable")
          .description("Sets color of background for catchable pokemons")
          .defaultTo(Color.of(0.f, 0.f, 0.f, 0.75f))
          .build();

      uncatchable = newColorSetting()
          .name("uncatchable")
          .description("Sets color of background for uncatchable pokemons, trainers, bosses")
          .defaultTo(Color.of(0.2, 0.f, 0.f, 0.75f))
          .build();

      onFullyConstructed();
      log.info("ColorPaletteSettings 1");
    }

  }

  @Getter
  @Log4j2
  private static class FormatSettings extends AbstractParentCommand {

    public final StringSetting nameFormat;
    public final StringSetting bossNameFormat;
    public final StringSetting skillFormat;
    public final StringSetting statFormat;
    public final StringSetting infoFormat;


    @Builder
    public FormatSettings(IParentCommand parent,
                          String name, @Singular Collection<String> aliases, String description,
                          @Singular Collection<EnumFlag> flags) {
      super(parent, name, aliases, description, flags);
      log.info("FormatSettings 0");
      nameFormat = newStringSetting()
          .name("nameFormat")
          .description("Available keys {name, lvl, dist}")
          .defaultTo("{name} lv{lvl} {dist}m.")
          .build();
      bossNameFormat = newStringSetting()
          .name("bossNameFormat")
          .description("Available keys {name, lvl, rarity}")
          .defaultTo("{name} lv{lvl} ({rarity}) {dist}m.")
          .build();
      skillFormat = newStringSetting()
          .name("skillFormat")
          .description("Available keys {0, 1, 2, 3}")
          .defaultTo("{0} {1}\n{2} {3}")
          .build();
      statFormat = newStringSetting()
          .name("statFormat")
          .description("Available keys {hp, atk, def, spd, spa, spd, (ivhp, evhp, etc..)}")
          .defaultTo("HP{hp}|{ivhp} AT{atk}|{ivatk} PD{def}|{ivdef}\nSA{sat}|{ivsat} SD{sdf}|{ivsdf} SPD{spd}|{ivspd}")
          .build();
      infoFormat = newStringSetting()
          .name("infoFormat")
          .description("Available keys {name, stat, skill}")
          .defaultTo("{name}\n{stat}\n{skill}")
          .build();
      onFullyConstructed();
      log.info("FormatSettings 1");
    }
  }

  @Getter
  @Log4j2
  private static class OffsetSettings extends AbstractParentCommand {

    public final FloatSetting x;
    public final FloatSetting y;

    @Builder
    public OffsetSettings(IParentCommand parent,
                          String name, @Singular Collection<String> aliases, String description,
                          @Singular Collection<EnumFlag> flags, float x, float y) {
      super(parent, name, aliases, description, flags);
      log.info("OffsetSettings 0");


      this.x = newFloatSetting()
          .name("x")
          .description("Offset by x axis")
          .defaultTo(x)
          .build();

      this.y = newFloatSetting()
          .name("y")
          .description("Offset by y axis")
          .defaultTo(y)
          .build();

      onFullyConstructed();
      log.info("OffsetSettings 1");
    }
  }

  @Getter
  @Log4j2
  private static class DrawSettings extends AbstractParentCommand {

    public final EnumSetting<Priority> box;
    public final EnumSetting<Priority> trace;
    public final EnumSetting<Priority> widget;
    public final EnumSetting<Priority> names;
    public final EnumSetting<Priority> info;
    public final BooleanSetting infoAllowedOnPlayers;
    public final FloatSetting boxOpacity;
    public final FloatSetting traceOpacity;
    int boxWidthMargin = 4;
    int boxHeightMargin = 2;

    public final IntegerSetting maxLookingDistance;
    public final FloatSetting lineThicknessBelowAverage;
    public final FloatSetting lineThicknessAboveAverage;
    public final FloatSetting nameScale;
    public final FloatSetting infoScale;
    public final FloatSetting widgetScale;
    public final OffsetSettings widgetOffset;
    public final OffsetSettings infoOffset;
    OffsetSettings.OffsetSettingsBuilder newOffsetSettings() {
      return OffsetSettings.builder().parent(this);
    }
    @Builder
    public DrawSettings(IParentCommand parent,
                        String name, @Singular Collection<String> aliases, String description,
                        @Singular Collection<EnumFlag> flags) {
      super(parent, name, aliases, description, flags);
      log.info("DrawSettings 0");

      info = newEnumSetting(Priority.class)
          .name("info")
          .description("Draw info if priority is higher or equal than")
          .defaultTo(Priority.AVERAGE)
          .build();
      this.names = newEnumSetting(Priority.class)
          .name("names")
          .description("Draw widget if priority is higher or equal than")
          .defaultTo(Priority.LOW)
          .build();
      widget = newEnumSetting(Priority.class)
          .name("widget")
          .description("Draw widget if priority is higher or equal than")
          .defaultTo(Priority.BELOW_AVERAGE)
          .build();
      trace = newEnumSetting(Priority.class)
          .name("trace")
          .description("Draw trace if priority is higher or equal than")
          .defaultTo(Priority.AVERAGE)
          .build();
      box = newEnumSetting(Priority.class)
          .name("box")
          .description("Draw box if priority is higher or equal than")
          .defaultTo(Priority.LOW)
          .build();
      maxLookingDistance = newIntegerSetting()
          .name("distance")
          .description("Max looking distance used to describe advanced information")
          .defaultTo(16)
          .build();
      lineThicknessBelowAverage = newFloatSetting()
          .name("linet0")
          .description("Line thickness below average")
          .defaultTo(3.f)
          .build();
      lineThicknessAboveAverage = newFloatSetting()
          .name("linet1")
          .description("Line thickness above average")
          .defaultTo(6.f)
          .build();
      nameScale = newFloatSetting()
          .name("nameScale")
          .description("Scale of names displayed in world")
          .defaultTo(0.5f)
          .build();

      infoScale = newFloatSetting()
          .name("infoScale")
          .description("Scale of infos")
          .defaultTo(1.f)
          .build();
      widgetScale = newFloatSetting()
          .name("widgetScale")
          .description("Scale of widgets")
          .defaultTo(1.f)
          .build();
      widgetOffset = newOffsetSettings()
          .name("widgetOffset")
          .description("Widget offset")
          .x(0.5f)
          .y(0.75f)
          .build();
      infoOffset = newOffsetSettings()
          .name("infoOffset")
          .description("Info offset")
          .x(0.5f)
          .y(0.75f)
          .build();
      infoAllowedOnPlayers = newBooleanSetting()
          .name("infoAllowedOnPlayers")
          .description("Shows info on other players pokemons")
          .defaultTo(false)
          .build();
      boxOpacity = newFloatSetting()
          .name("boxOpacity")
          .description("Opacity applied to box")
          .defaultTo(.25f)
          .build();
      traceOpacity = newFloatSetting()
          .name("traceOpacity")
          .description("Opacity applied to trace")
          .defaultTo(.75f)
          .build();
      onFullyConstructed();
      log.info("DrawSettings 1");

    }
  }

}

