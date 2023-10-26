package dev.fiki.forgehax.main.mods.render;

import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.pokemon.Nature;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.boss.BossTier;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.species.gender.Gender;
import com.pixelmonmod.pixelmon.api.pokemon.stats.*;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.entities.WormholeEntity;
import com.pixelmonmod.pixelmon.entities.npcs.*;
import com.pixelmonmod.pixelmon.enums.EnumGrowth;
import com.pixelmonmod.pixelmon.enums.EnumNPCTutorType;
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
import dev.fiki.forgehax.api.cmd.settings.collections.SimpleSettingSet;
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
import dev.fiki.forgehax.api.typeconverter.TypeConverter;
import dev.fiki.forgehax.main.services.GuiService;
import lombok.*;
import lombok.experimental.ExtensionMethod;
import lombok.extern.log4j.Log4j2;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import org.apache.commons.lang3.tuple.MutablePair;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dev.fiki.forgehax.main.Common.*;
import static org.lwjgl.opengl.GL11.*;

import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import org.lwjgl.opengl.GL11;

@RegisterMod(
    name = "PixelmonESP",
    description = "Shows pokemons locations and info",
    category = Category.RENDER,
    flags = EnumFlag.TOGGLE_MOD
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
          "Dratini", "Dragonair", "Dragonite",
          "Larvitar", "Pupitar", "Tyranitar",
          "Bagon", "Slegon", "Salamence",
          "Beldum", "Metang", "Metagross",
          "Gible", "Gabite", "Garchomp",
          "Deino", "Zweilous", "Hydreigon",
          "Goomy", "Sliggoo", "Goodra",
          "Jangmo-o", "Hakamo-o", "Kommo-o",
          "Dreepy", "Drakloak", "Dragapult",
          "Frigibax", "Arctibax", "Baxcalibur",
          "Diancie"
      )
  );

  SimpleSettingList<SearchQuery> search;
  SimpleSettingSet<GrowthEnum> searchByGrowth;
  SimpleSettingSet<NatureEnum> searchByNature;
  FloatSetting searchByIvs;
  BooleanSetting searchGroup1;
  BooleanSetting searchGroup2;
  BooleanSetting searchNonStandard;
  ContainedSimpleSettingList<String> group1;
  ContainedSimpleSettingList<String> group2;
  ColorPaletteSettings colorPalette;

  DrawSettings drawSettings;

  FormatSettings formatSettings;
  PrioritySettings priorities;
  ArrayList<RegistryValue<Species>> searchSpecies;
  ArrayList<RegistryValue<Species>> group1Species;
  ArrayList<RegistryValue<Species>> group2Species;
  Field baseAttackField = null;
  Field overrideAttackField = null;

  public PixelmonESP() {
    newSimpleCommand()
        .name("searchhelp")
        .description("Shows all gender, nature, growth values")
        .executor((args) -> {
          args.inform(String.format("Genders: %s",
              Arrays.stream(GenderEnum.values())
                  .map(GenderEnum::toString).map(String::toLowerCase)
                  .sorted().collect(Collectors.joining(", "))
          ));
          args.inform(String.format("Growth: %s",
              Arrays.stream(GrowthEnum.values())
                  .map(GrowthEnum::toString).map(String::toLowerCase)
                  .sorted().collect(Collectors.joining(", "))
          ));
          args.inform(String.format("Natures: %s",
              Arrays.stream(NatureEnum.values())
                  .map(NatureEnum::toString).map(String::toLowerCase)
                  .sorted().collect(Collectors.joining(", "))
          ));
        })
        .build();
    search = newSimpleSettingList(SearchQuery.class)
        .name("search")
        .description("Searches for pokemon names, case insensitive")
        .supplier(ArrayList::new)
        .listener(new SearchListener())
        .argument(
            Arguments.newConverterArgument(SearchQuery.class)
                .converter(searchQueryTypeConverter)
                .minArgumentsConsumed(1)
                .maxArgumentsConsumed(38)
                .label("query")
                .build()
        )
        .build();
    searchNonStandard = newBooleanSetting()
        .name("nssearch")
        .description("Look for non standard palettes")
        .defaultTo(true)
        .build();
    searchGroup1 = newBooleanSetting()
        .name("g1search")
        .description("Look for group 1")
        .defaultTo(false)
        .build();
    searchGroup2 = newBooleanSetting()
        .name("g2search")
        .description("Look for group 2")
        .defaultTo(false)
        .build();
    group1 = newContainedListSetting(String.class)
        .name("group1")
        .description("Group 1")
        .argument(Arguments.newStringArgument().label("name").build())
        .multiarg(Arguments.newStringArgument().label("names")
            .minArgumentsConsumed(1).maxArgumentsConsumed(1000).build())
        .supplier(ArrayList::new)
        .listener(new Group1Listener())
        .defaultTo(starterPokemon)
        .build();
    group2 = newContainedListSetting(String.class)
        .name("group2")
        .description("Group 2")
        .argument(Arguments.newStringArgument().label("name").build())
        .multiarg(Arguments.newStringArgument().label("names")
            .minArgumentsConsumed(1).maxArgumentsConsumed(1000).build())
        .supplier(ArrayList::new)
        .listener(new Group2Listener())
        .defaultTo(rarePokemon)
        .build();
    colorPalette = newColorPalette()
        .name("colors")
        .description("Color palette")
        .build();
    drawSettings = newDrawSettings()
        .name("draw")
        .description("Draw settings")
        .build();
    formatSettings = newFormatSettings()
        .name("format")
        .description("Format settings")
        .build();
    priorities = newPrioritySettings()
        .name("priorities")
        .description("Priority settings")
        .build();
    searchSpecies = new ArrayList<>();
    group1Species = new ArrayList<>();
    group2Species = new ArrayList<>();
    searchByGrowth = newSimpleSettingEnumSet(GrowthEnum.class)
        .name("growthsearch")
        .alias("gsearch")
        .argument(Arguments.newEnumArgument(GrowthEnum.class).label("id").build())
        .description("Search by growth")
        .supplier(HashSet::new)
        .build();
    searchByNature = newSimpleSettingEnumSet(NatureEnum.class)
        .name("naturesearch")
        .alias("nsearch")
        .argument(Arguments.newEnumArgument(NatureEnum.class).label("id").build())
        .description("Search by nature")
        .supplier(HashSet::new)
        .build();
    searchByIvs = newFloatSetting()
        .name("ivsearch")
        .description("Search by ivs (if higher than)")
        .defaultTo(0.f)
        .min(0.f)
        .max(100.f)
        .build();
  }

  @Override
  public void onLoad() {
    try {
      baseAttackField = Attack.class.getDeclaredField("baseAttack");
      baseAttackField.setAccessible(true);
    } catch (NoSuchFieldException e) {
      log.info("Couldn't find Attack.baseAttack field");
    }
    try {
      overrideAttackField = Attack.class.getDeclaredField("overrideAttack");
      overrideAttackField.setAccessible(true);
    } catch (NoSuchFieldException e) {
      log.info("Couldn't find Attack.overrideAttack field");
    }

    updateSearchSpecies();
    updateGroup1Species();
    updateGroup2Species();
  }

  boolean isPlayerLookingAt(ScreenPos centerPos) {
    float screenWidth = getScreenWidth();
    float screenHeight = getScreenHeight();
    float cx = screenWidth / 2.f;
    float cy = screenHeight / 2.f;
    //float distance = screenWidth / 16.f;
    boolean nearX = Math.abs(cx - centerPos.getX()) <= drawSettings.lookThreshold.getValue() * screenWidth;
    boolean nearY = Math.abs(cy - centerPos.getY()) <= drawSettings.lookThreshold.getValue() * screenHeight;
    ;

    return nearX || nearY;
  }

  Priority getPriorityForBossTier(String bossTierID, boolean isMega) {
    Priority priority;
    if ("common".equals(bossTierID) || "notboss".equalsIgnoreCase(bossTierID)) {
      priority = priorities.bossCommon.getValue();
    } else if ("uncommon".equals(bossTierID)) {
      priority = priorities.bossUncommon.getValue();
    } else if ("rare".equals(bossTierID)) {
      priority = priorities.bossRare.getValue();
    } else if ("epic".equals(bossTierID)) {
      priority = priorities.bossEpic.getValue();
    } else if ("legendary".equals(bossTierID)) {
      priority = priorities.bossLegendary.getValue();
    } else if ("ultimate".equals(bossTierID)) {
      priority = priorities.bossUltimate.getValue();
    } else if ("spooky".equals(bossTierID)) {
      priority = priorities.bossSpooky.getValue();
    } else if ("drowned".equals(bossTierID)) {
      priority = priorities.bossDrowned.getValue();
    } else {
      priority = isMega ? priorities.bossMega.getValue() : Priority.NONE;
    }
    return priority;
  }

  void makePokemonInfo(Pokemon ent, DrawData out) {
    out.drawInfo = true;

    final PermanentStats stats = ent.getStats();
    final IVStore ivs = stats.getIVs();
    final EVStore evs = stats.getEVs();
    HashMap<String, Object> statArguments = new HashMap<String, Object>();
    HashMap<String, Object> specArguments = new HashMap<String, Object>();
    specArguments.put("palette", ent.getPalette().getName());
    specArguments.put("nature", ent.getNature().toString());
    specArguments.put("gender", ent.getGender().toString());
    specArguments.put("growth", ent.getGrowth().toString().toUpperCase());

    statArguments.put("hp", String.format("%03d", stats.getHP()));
    statArguments.put("atk", String.format("%03d", stats.getAttack()));
    statArguments.put("def", String.format("%03d", stats.getDefense()));
    statArguments.put("sat", String.format("%03d", stats.getSpecialAttack()));
    statArguments.put("sdf", String.format("%03d", stats.getSpecialDefense()));
    statArguments.put("spd", String.format("%03d", stats.getSpeed()));

    statArguments.put("ivprc", String.format("%.2f%%", ivs.getPercentage(2)));
    statArguments.put("ivhp", String.format("%03d", ivs.getStat(BattleStatsType.HP)));
    statArguments.put("ivatk", String.format("%03d", ivs.getStat(BattleStatsType.ATTACK)));
    statArguments.put("ivdef", String.format("%03d", ivs.getStat(BattleStatsType.DEFENSE)));
    statArguments.put("ivsat", String.format("%03d", ivs.getStat(BattleStatsType.SPECIAL_ATTACK)));
    statArguments.put("ivsdf", String.format("%03d", ivs.getStat(BattleStatsType.SPECIAL_DEFENSE)));
    statArguments.put("ivspd", String.format("%03d", ivs.getStat(BattleStatsType.SPEED)));

    statArguments.put("evhp", String.format("%03d", evs.getStat(BattleStatsType.HP)));
    statArguments.put("evatk", String.format("%03d", evs.getStat(BattleStatsType.ATTACK)));
    statArguments.put("evdef", String.format("%03d", evs.getStat(BattleStatsType.DEFENSE)));
    statArguments.put("evsat", String.format("%03d", evs.getStat(BattleStatsType.SPECIAL_ATTACK)));
    statArguments.put("evsdf", String.format("%03d", evs.getStat(BattleStatsType.SPECIAL_DEFENSE)));
    statArguments.put("evspd", String.format("%03d", evs.getStat(BattleStatsType.SPEED)));

    String[] statsStr = formatSettings.statFormat.getValue().format(statArguments).split("\n");
    String[] specsStr = formatSettings.specFormat.getValue().format(specArguments).split("\n");

//    HashMap<String, Object> skillArguments = new HashMap<String, Object>();
//    skillArguments.put("0", "");
//    skillArguments.put("1", "");
//    skillArguments.put("2", "");
//    skillArguments.put("3", "");
//    final Moveset moveset = ent.getMoveset();
//    //@BUG Pixelmon loads some shitty moveset (not actual), no idea where to get actual attacks
//    for (int i = 0; i < moveset.attacks.length; ++i) {
//      String attackName = "";
//      if (overrideAttackField != null) {
//        try {
//          ImmutableAttack overrideAttack = (ImmutableAttack) overrideAttackField.get(moveset.attacks[i]);
//          attackName = overrideAttack.getAttackName();
//        } catch (Throwable ex) {
//        }
//      }
//      if (attackName.isEmpty()) {
//        try {
//          ImmutableAttack attack = (ImmutableAttack) baseAttackField.get(moveset.attacks[i]);
//          attackName = attack.getAttackName();
//        } catch (Throwable ex) {
//        }
//      }
//      skillArguments.put((new Integer(i)).toString(), attackName);
//    }
//
//    String[] skillsStr = formatSettings.skillFormat.getValue().format(skillArguments).split("\n");

    for (String s : statsStr) {
      out.text.add(new MutablePair<>(s, Colors.WHITE_SMOKE));
    }
//    for (String s : skillsStr) {
//      out.text.add(new MutablePair<>(s, Colors.WHITE_SMOKE));
//    }
    for (String s : specsStr) {
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
      if (pokemonEntity.getOwner() == getLocalPlayer()) {
        return data;
      }
      BossTier bossTier = pokemonEntity.getBossTier();
      Vector3d bottomPos = ent.getInterpolatedPos(partialTicks);
      data.entityBottomPos = bottomPos;
      AxisAlignedBB box = ent.getBoundingBox();
      Vector3d center = new Vector3d(bottomPos.x, bottomPos.y + box.getYsize() * .5, bottomPos.z);
      ScreenPos screenPos = VectorUtil.toScreen(center);
      data.entityScreenPos = screenPos;
      boolean isLookingAt = isPlayerLookingAt(screenPos);
      int distanceTo = (int) getGameRenderer().getMainCamera().getPosition().distanceTo(bottomPos);
      data.distanceTo = distanceTo;

      isLookingAt &= distanceTo <= drawSettings.maxLookingDistance.getValue();
      String pokemonName = pokemon.getSpecies().getName();
      int level = pokemon.getPokemonLevel();
      boolean isLegendary = pokemon.isLegendary();
      boolean isUltraBeast = pokemon.isUltraBeast();
      boolean isWild = pokemonEntity.getOwnerUUID() == null;
      boolean isShiny = pokemon.getPalette().getName().equals("shiny");
      boolean isNonStandard = !isShiny && searchNonStandard.getValue() && !pokemon.getPalette().getName().equals("none");
      boolean isBoss = bossTier != null && bossTier.isBoss();
      boolean isCatchable = !pokemon.isUncatchable();
      val registry = pokemon.getSpecies().getRegistryValue();
      boolean isSearching = searchSpecies.contains(registry);
      if (isSearching) {
        EnumGrowth growth = pokemon.getGrowth();
        Nature nature = pokemon.getNature();
        Gender gender = pokemon.getGender();
        float ivs = (float) pokemon.getIVs().getPercentage(2);
        isSearching = search.stream().anyMatch(x ->
            x.pokemon.equalsIgnoreCase(pokemonName)
                && (x.growth.isEmpty() || x.growth.stream().anyMatch(gr -> gr.value == growth))
                && (x.nature.isEmpty() || x.nature.stream().anyMatch(nt -> nt.value == nature))
                && (x.gender.isEmpty() || x.gender.stream().anyMatch(gn -> gn.value == gender))
                && ivs >= x.ivs
        );
      } else {
        EnumGrowth growth = pokemon.getGrowth();
        Nature nature = pokemon.getNature();
        float ivs = (float) pokemon.getIVs().getPercentage(2);
        isSearching =
            searchByGrowth.stream().anyMatch(x -> x.value == growth) ||
                searchByNature.stream().anyMatch(x -> x.value == nature) ||
                (searchByIvs.getValue() > 0 && ivs >= searchByIvs.getValue());
      }
      boolean isInGroup1 = false;
      boolean isInGroup2 = false;
      boolean isRaid = pokemonEntity.isRaidPokemon();
      if (!isSearching && searchGroup1.getValue()) isInGroup1 = group1Species.contains(registry);
      if (!isSearching && !isInGroup1 && searchGroup2.getValue()) isInGroup2 = group2Species.contains(registry);
      int infoPriority = drawSettings.info.getValue().value;


      String bossRarity = isBoss ? bossTier.getID() : "";
      HashMap<String, Object> nameArguments = new HashMap<String, Object>();
      nameArguments.put("name", pokemonName);
      if (!isBoss) {
        nameArguments.put("lvl", level);
      }
      nameArguments.put("dist", distanceTo);
      String name = isBoss ? null : formatSettings.nameFormat.getValue().format(nameArguments);

      if (!isWild) {
        if (isLookingAt && drawSettings.infoAllowedOnPlayers.getValue()) {

          data.text.add(new MutablePair<>(name, Colors.WHITE));
          makePokemonInfo(pokemon, data);
        }
        return data;
      }
      if (isBoss) {
        String bossTierID = bossTier.getID();
        nameArguments.put("rarity", bossRarity);
        nameArguments.put("lvl", level + bossTier.getExtraLevels());
        name = formatSettings.bossNameFormat.getValue().format(nameArguments);
        data.priority = getPriorityForBossTier(bossTierID, bossTier.isMega());
        data.text.add(new MutablePair<>(name, Color.of(bossTier.getColor().getRGB())));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      //Bosses are shiny
      else if (isShiny) {
        data.priority = priorities.shiny.getValue();
        data.text.add(new MutablePair<>(name, colorPalette.shiny.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      if (isNonStandard && data.priority.value < priorities.nonstandard.getValue().value) {
        data.priority = priorities.nonstandard.getValue();
        data.text.clear();
        data.text.add(new MutablePair<>(name, colorPalette.nonstandard.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      if (isInGroup1 && data.priority.value < priorities.group1.getValue().value) {
        data.priority = priorities.group1.getValue();
        data.text.clear();
        data.text.add(new MutablePair<>(name, colorPalette.group1.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      if (isInGroup2 && data.priority.value < priorities.group2.getValue().value) {
        data.priority = priorities.group2.getValue();
        data.text.clear();
        data.text.add(new MutablePair<>(name, colorPalette.group2.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      if (isSearching && data.priority.value < priorities.search.getValue().value) {
        data.priority = priorities.search.getValue();
        data.text.clear();
        data.text.add(new MutablePair<>(name, colorPalette.search.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      if (isLegendary && data.priority.value < priorities.legendary.getValue().value) {
        data.priority = priorities.legendary.getValue();
        data.text.clear();
        data.text.add(new MutablePair<>(name, colorPalette.legendary.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      if (isUltraBeast && data.priority.value < priorities.ultrabeast.getValue().value) {
        data.priority = priorities.ultrabeast.getValue();
        data.text.clear();
        data.text.add(new MutablePair<>(name, colorPalette.ultrabeast.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      if (isRaid && data.priority.value < priorities.raid.getValue().value) {
        data.priority = priorities.raid.getValue();
        data.text.clear();
        data.text.add(new MutablePair<>(name, colorPalette.ultrabeast.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
      }
      if (data.priority == Priority.NONE) {
        data.priority = priorities.common.getValue();
        data.text.add(new MutablePair<>(name, colorPalette.common.getValue()));
        if (isLookingAt && data.priority.value >= infoPriority) {
          makePokemonInfo(pokemon, data);
          data.drawInfo = true;
        }
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
          (drawSettings.widget.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.widget.getValue().value);
      data.drawBox = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.box.getValue().value);
      data.drawTrace = (drawSettings.trace.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.trace.getValue().value);
      data.drawName = (drawSettings.names.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.names.getValue().value);

      if (data.drawTrace) {
        data.trace.left = data.priority.value <= Priority.BELOW_AVERAGE.value
            ? drawSettings.lineThicknessBelowAverage.getValue()
            : drawSettings.lineThicknessAboveAverage.getValue();
        data.trace.right = data.text.get(0).right.setAlpha(drawSettings.traceOpacity.getValue());
      }
      if (data.drawBox) {
        data.box.left = box;
        data.box.right = data.text.get(0).right.setAlpha(drawSettings.boxOpacity.getValue());
      }
    } else if (ent instanceof WormholeEntity) {
      Vector3d bottomPos = ent.getInterpolatedPos(partialTicks);
      data.entityBottomPos = bottomPos;
      AxisAlignedBB box = ent.getBoundingBox();
      Vector3d center = new Vector3d(bottomPos.x, bottomPos.y + box.getYsize() * .5, bottomPos.z);
      ScreenPos screenPos = VectorUtil.toScreen(center);
      int distanceTo = (int) getGameRenderer().getMainCamera().getPosition().distanceTo(bottomPos);
      HashMap<String, Object> nameArguments = new HashMap<String, Object>();
      nameArguments.put("name", "Wormhole");
      nameArguments.put("dist", distanceTo);
      String name = formatSettings.npcNameFormat.getValue().format(nameArguments);

      data.text.add(new MutablePair<>(name, colorPalette.wormhole.getValue()));
      double textWidth = Render2D.getStringWidth(name) + (drawSettings.boxWidthMargin * 2);
      double textHeight = (Render2D.getStringHeight() + (drawSettings.boxHeightMargin * 2)) * (data.text.size());
      data.background = new MutablePair<>(new Vector2f((float) textWidth, (float) textHeight), colorPalette.uncatchable.getValue());
      data.distanceTo = distanceTo;
      data.entityScreenPos = screenPos;
      data.priority = priorities.wormhole.getValue();
      data.drawWidget = (drawSettings.widget.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.widget.getValue().value);
      data.drawBox = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.box.getValue().value);
      data.drawTrace = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.trace.getValue().value);
      data.drawName = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.names.getValue().value);

      if (data.drawTrace) {
        data.trace.left = data.priority.value <= Priority.BELOW_AVERAGE.value
            ? drawSettings.lineThicknessBelowAverage.getValue()
            : drawSettings.lineThicknessAboveAverage.getValue();
        data.trace.right = data.text.get(0).right.setAlpha(drawSettings.traceOpacity.getValue());
      }
      if (data.drawBox) {
        data.box.left = box;
        data.box.right = data.text.get(0).right.setAlpha(drawSettings.boxOpacity.getValue());
      }
    } else if (ent instanceof NPCEntity) {
      boolean isTutor = ent instanceof NPCTutor;
      boolean isTrainer = ent instanceof NPCTrainer;
      boolean isTrader = ent instanceof NPCTrader;
      boolean isRelearner = ent instanceof NPCRelearner;
      boolean isFisherman = ent instanceof NPCFisherman;

      Vector3d bottomPos = ent.getInterpolatedPos(partialTicks);
      data.entityBottomPos = bottomPos;
      AxisAlignedBB box = ent.getBoundingBox();
      Vector3d center = new Vector3d(bottomPos.x, bottomPos.y + box.getYsize() * .5, bottomPos.z);
      ScreenPos screenPos = VectorUtil.toScreen(center);
      data.entityScreenPos = screenPos;
      int distanceTo = (int) getGameRenderer().getMainCamera().getPosition().distanceTo(bottomPos);
      data.distanceTo = distanceTo;
      HashMap<String, Object> nameArguments = new HashMap<String, Object>();
      nameArguments.put("dist", distanceTo);

      if (isTrainer) {
        NPCTrainer npc = (NPCTrainer) ent;
        BossTier npcBossTier = npc.getBossTier();
        String bossTierID = npcBossTier.getID();
        int level = npc.pokemonLevel;
        nameArguments.put("name", "Trainer");
        nameArguments.put("lvl", level);
        nameArguments.put("rarity", bossTierID);
        String name = formatSettings.bossNameFormat.getValue().format(nameArguments);
        data.priority = getPriorityForBossTier(bossTierID, npcBossTier.isMega());
        data.text.add(new MutablePair<>(name, Color.of(npcBossTier.getColor().getRGB())));
      } else if (isTutor) {
        data.priority = priorities.tutor.getValue();
        NPCTutor npc = (NPCTutor) ent;
        nameArguments.put("name", npc.getTutorType() == EnumNPCTutorType.TUTOR ? "Tutor" : "Tutor (Transfer)");
        String name = formatSettings.npcNameFormat.getValue().format(nameArguments);
        data.text.add(new MutablePair<>(name, colorPalette.tutor.getValue()));
      } else if (isTrader) {
        data.priority = priorities.trader.getValue();
        NPCTrader npc = (NPCTrader) ent;
        nameArguments.put("name", "Trader");
        String name = formatSettings.npcNameFormat.getValue().format(nameArguments);
        data.text.add(new MutablePair<>(name, colorPalette.trader.getValue()));
      } else if (isRelearner) {
        data.priority = priorities.relearner.getValue();
        NPCRelearner npc = (NPCRelearner) ent;
        nameArguments.put("name", "Relearner");
        String name = formatSettings.npcNameFormat.getValue().format(nameArguments);
        data.text.add(new MutablePair<>(name, colorPalette.relearner.getValue()));
      } else if (isFisherman) {
        data.priority = priorities.fisherman.getValue();
        NPCFisherman npc = (NPCFisherman) ent;
        nameArguments.put("name", "Fisherman");
        String name = formatSettings.npcNameFormat.getValue().format(nameArguments);
        data.text.add(new MutablePair<>(name, colorPalette.fisherman.getValue()));
      } else {
        return data;
      }
      double textWidth = Render2D.getStringWidth(data.text.get(0).left) + (drawSettings.boxWidthMargin * 2);
      double textHeight = (Render2D.getStringHeight() + (drawSettings.boxHeightMargin * 2));
      data.background = new MutablePair<>(new Vector2f((float) textWidth, (float) textHeight), colorPalette.uncatchable.getValue());
      data.drawWidget = (drawSettings.widget.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.widget.getValue().value);
      data.drawBox = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.box.getValue().value);
      data.drawTrace = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.trace.getValue().value);
      data.drawName = (drawSettings.box.getValue().value != Priority.NONE.value) &&
          (data.priority.value >= drawSettings.names.getValue().value);
      if (data.drawTrace) {
        data.trace.left = data.priority.value <= Priority.BELOW_AVERAGE.value
            ? drawSettings.lineThicknessBelowAverage.getValue()
            : drawSettings.lineThicknessAboveAverage.getValue();
        data.trace.right = data.text.get(0).right.setAlpha(drawSettings.traceOpacity.getValue());
      }
      if (data.drawBox) {
        data.box.left = box;
        data.box.right = data.text.get(0).right.setAlpha(drawSettings.boxOpacity.getValue());
      }
    }
    return data;
  }

  ArrayList<DrawData> cachedDrawData = new ArrayList<DrawData>();
  private long lastUpdate = 0;

  public double interpolate(double minValue, double maxValue, double fraction) {
    fraction = Math.max(0.0, Math.min(1.0, fraction));
    return minValue + (maxValue - minValue) * fraction;
  }

  @SubscribeListener
  public void onRender(RenderSpaceEvent event) {
    if (!this.isEnabled()) return;
    BufferBuilder buffer = event.getBuffer();
    val stack = event.getStack();
    stack.pushPose();
    stack.translateVec(event.getProjectedPos().scale(-1));
    buffer.beginQuads(DefaultVertexFormats.POSITION_COLOR);

    final int sides = GeometryMasks.Quad.ALL;
    for (DrawData data : cachedDrawData) {
      if (!data.drawBox) continue;
      buffer.filledCube(data.box.left, sides, data.box.right, stack.getLastMatrix());
    }
    buffer.draw();
    stack.popPose();
  }

  @SubscribeListener(priority = PriorityEnum.LOW)
  public void onRender2D(final RenderPlaneEvent.Back event) {
    if (!this.isEnabled()) return;
    final float partialTicks = event.getPartialTicks();
    final float screenWidth = event.getScreenWidth();
    final float screenHeight = event.getScreenHeight();
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
    long now = System.currentTimeMillis();

    boolean shouldUpdate = true;
    if (now >= lastUpdate + 33) {
      lastUpdate = now;
      shouldUpdate = true;
    }

    if (shouldUpdate) {
      // distance to chunk
      cachedDrawData.clear();
      for (Entity ent : getWorld().entitiesForRendering()) {
        DrawData data = getDrawData(ent, partialTicks);
        if (data.priority == Priority.NONE) continue;
        cachedDrawData.add(data);
      }
    }
    for (DrawData drawData : cachedDrawData) {
      if (drawData.drawInfo && info.priority.value < drawData.priority.value) {
        info = drawData;
      }
      if (drawData.drawTrace) {
        traces.add(drawData);
      }
      if (drawData.drawBox) {
        boxes.add(drawData);
      }
      if (drawData.drawName) {
        names.add(drawData);
      }
      if (drawData.drawWidget) {
        if (widgets.size() >= drawSettings.maxWidgets.getValue()) {
          for (int i = 0; i < widgets.size(); i++) {
            if (widgets.get(i).priority.value < drawData.priority.value) {
              widgets.set(i, drawData);
              break;
            }
          }
        } else {
          widgets.add(drawData);
        }
      }
    }

    if (info.drawInfo) {
      final Vector2f size = info.background.left;

      Vector2f baseOffset = new Vector2f(
          screenWidth * drawSettings.infoOffset.x.getValue() - (size.x * .5f),
          screenHeight * drawSettings.infoOffset.y.getValue()
      );

      float textHeight = (float) (drawSettings.boxHeightMargin + Render2D.getStringHeight());

      float textOffset = drawSettings.boxHeightMargin;
      float drawAtX = baseOffset.x;
      float drawAtY = baseOffset.y;
      stack.pushPose();
      //glEnable(GL_DEPTH_TEST);
      stack.translate(0, 0, -1.0);
      float scale = drawSettings.infoScale.getValue();
      for (val text : info.text) {
        Render2D.renderString(source, stack.getLastMatrix(), text.left,
            drawAtX + drawSettings.boxWidthMargin,
            drawAtY + textOffset, text.right, true);
        textOffset += textHeight;
      }
      triangles.rect(GL_TRIANGLES, drawAtX, drawAtY, size.x, size.y, info.background.right, stack.getLastMatrix());
      //glClear(GL_DEPTH_BUFFER_BIT);
      stack.popPose();
    } else {
      float offsetY = 0;
      float maxWidth = 0;
      for (DrawData widget : widgets) {
        maxWidth = Math.max(maxWidth, widget.background.left.x);
      }
      Vector2f baseOffset = new Vector2f(
          screenWidth * drawSettings.widgetOffset.x.getValue() - (maxWidth * .5f),
          screenHeight * drawSettings.widgetOffset.y.getValue()
      );
      //widgets.sort(Comparator.comparingInt(a -> a.priority.value));
      widgets.sort((a, b) -> {
        int vA = a.priority.value;
        int vB = b.priority.value;
        return (vA > vB) ? -1 : ((vA == vB) ? 0 : 1);
      });
      float scale = drawSettings.widgetScale.getValue();

      for (int i = 0; i < widgets.size(); i++) {
        final DrawData data = widgets.get(i);
        final Vector2f size = data.background.left;
        float drawAtX = baseOffset.x;
        float drawAtY = baseOffset.y + offsetY;
        val text = data.text.get(0);

        stack.pushPose();
        Render2D.renderString(source, stack.getLastMatrix(), text.left, drawAtX + drawSettings.boxWidthMargin, drawAtY + drawSettings.boxHeightMargin, text.right, true);
        triangles.rect(GL_TRIANGLES, drawAtX, drawAtY, size.x, size.y, data.background.right, stack.getLastMatrix());
        stack.popPose();

        offsetY += drawSettings.boxHeightMargin + size.y;
      }
    }

    for (DrawData data : names) {
      val name = data.text.get(0);
      ScreenPos pos = data.entityScreenPos;
      double width = Render2D.getStringWidth(name.left);

      float minNameScale = drawSettings.minNameScale.getValue();
      float maxNameScale = drawSettings.maxNameScale.getValue();
      int minDistScale = drawSettings.mindNameScale.getValue();
      int maxDistScale = drawSettings.maxdNameScale.getValue();
      float distFraction =
          data.distanceTo <= minDistScale ? 0
              : data.distanceTo >= maxDistScale ? 1
              : (float) maxDistScale / (float) data.distanceTo;

      float scale = (float) interpolate(minNameScale, maxNameScale, distFraction);
      boolean render = !(drawSettings.removeNameAtMinDistance.getValue() && data.distanceTo <= minDistScale);
      if (render) {
        stack.pushPose();
        stack.translate((float) pos.getX(), (float) pos.getY(), 0.f);
        float x = (float) (width / 2.f);
        float y = (float) Render2D.getStringHeight() / 2.f;
        stack.scale(scale, scale, 0.f);
        stack.translate(-x, -y, 0.f);
        Render2D.renderString(source, stack.getLastMatrix(), name.left,
            0, 0, name.right, true);
        stack.popPose();
      }
    }

    source.endBatch();

    final double cx = event.getScreenWidth() / 2.f;
    final double cy = event.getScreenHeight() / 2.f;
    for (DrawData data : traces) {
      val trace = data.trace;
      glColor4f(trace.right.getRedAsFloat(),
          trace.right.getGreenAsFloat(),
          trace.right.getBlueAsFloat(),
          trace.right.getAlphaAsFloat());
      GL11.glLineWidth(trace.left);
      glBegin(GL_LINES);
      {
        GL11.glVertex2d(cx, cy);
        GL11.glVertex2d(data.entityScreenPos.getX(), data.entityScreenPos.getY());
      }
      glEnd();
    }
    source.endBatch();
    GL11.glColor4i(255, 255, 255, 255);
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
    public int distanceTo;
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
      distanceTo = 0;
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

  public enum GrowthEnum {
    PYGMY(EnumGrowth.Pygmy),
    RUNT(EnumGrowth.Runt),
    SMALL(EnumGrowth.Small),
    ORDINARY(EnumGrowth.Ordinary),
    HUGE(EnumGrowth.Huge),
    GIANT(EnumGrowth.Giant),
    ENORMOUS(EnumGrowth.Enormous),
    GINORMOUS(EnumGrowth.Ginormous),
    MICROSCOPIC(EnumGrowth.Microscopic);

    private final EnumGrowth value;

    GrowthEnum(EnumGrowth growth) {
      this.value = growth;
    }

    // Custom method to get the string representation based on the integer value
    public static String getStringValue(EnumGrowth value) {
      for (GrowthEnum en : GrowthEnum.values()) {
        if (en.value == value) {
          return en.toString();
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return String.valueOf(value).toUpperCase();
    }
  }

  public enum NatureEnum {
    HARDY(Nature.HARDY),
    SERIOUS(Nature.SERIOUS),
    DOCILE(Nature.DOCILE),
    BASHFUL(Nature.BASHFUL),
    QUIRKY(Nature.QUIRKY),
    LONELY(Nature.LONELY),
    BRAVE(Nature.BRAVE),
    ADAMANT(Nature.ADAMANT),
    NAUGHTY(Nature.NAUGHTY),
    BOLD(Nature.BOLD),
    RELAXED(Nature.RELAXED),
    IMPISH(Nature.IMPISH),
    LAX(Nature.LAX),
    TIMID(Nature.TIMID),
    HASTY(Nature.HASTY),
    JOLLY(Nature.JOLLY),
    NAIVE(Nature.NAIVE),
    MODEST(Nature.MODEST),
    MILD(Nature.MILD),
    QUIET(Nature.QUIET),
    RASH(Nature.RASH),
    CALM(Nature.CALM),
    GENTLE(Nature.GENTLE),
    SASSY(Nature.SASSY),
    CAREFUL(Nature.CAREFUL);

    private final Nature value;

    NatureEnum(Nature value) {
      this.value = value;
    }

    // Custom method to get the string representation based on the integer value
    public static String getStringValue(Nature value) {
      for (NatureEnum en : NatureEnum.values()) {
        if (en.value == value) {
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

  public enum GenderEnum {
    MALE(Gender.MALE),
    FEMALE(Gender.FEMALE),
    NONE(Gender.NONE);

    private final Gender value;

    GenderEnum(Gender value) {
      this.value = value;
    }

    // Custom method to get the string representation based on the integer value
    public static String getStringValue(Gender value) {
      for (GenderEnum en : GenderEnum.values()) {
        if (en.value == value) {
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

  void updateSearchSpecies() {
    searchSpecies.clear();
    Optional<AbstractMod> guiService = getForgeHax().getModManager().getMods().filter(mod -> mod instanceof GuiService).findFirst();
    GuiService gui = (GuiService) guiService.orElse(null);
    search.forEach(x -> {
      Optional<RegistryValue<Species>> registry = PixelmonSpecies.get(x.pokemon);
      if (!registry.isPresent()) {
        if (gui != null)
          gui.getConsole().addMessage(String.format("Couldn't find %s", x));
        return;
      }
      searchSpecies.add(registry.get());
    });
    if (gui != null)
      gui.getConsole().addMessage(String.format("[Search] Looking for %d pokemons", searchSpecies.size()));
  }

  void updateGroup1Species() {
    group1Species.clear();
    Optional<AbstractMod> guiService = getForgeHax().getModManager().getMods().filter(mod -> mod instanceof GuiService).findFirst();
    GuiService gui = guiService.isPresent() ? (GuiService) guiService.get() : null;
    group1.list.forEach(x -> {
      Optional<RegistryValue<Species>> registry = PixelmonSpecies.get((String) x);
      if (!registry.isPresent()) {
        if (gui != null)
          gui.getConsole().addMessage(String.format("Couldn't find %s", x));
        return;
      }
      group1Species.add(registry.get());
    });
    if (gui != null)
      gui.getConsole().addMessage(String.format("[Group1] Looking for %d pokemons", group1Species.size()));
  }

  void updateGroup2Species() {
    group2Species.clear();
    Optional<AbstractMod> guiService = getForgeHax().getModManager().getMods().filter(mod -> mod instanceof GuiService).findFirst();
    GuiService gui = guiService.isPresent() ? (GuiService) guiService.get() : null;
    group2.list.forEach(x -> {
      Optional<RegistryValue<Species>> registry = PixelmonSpecies.get((String) x);
      if (!registry.isPresent()) {
        if (gui != null)
          gui.getConsole().addMessage(String.format("Couldn't find %s", x));
        return;
      }
      group2Species.add(registry.get());
    });
    if (gui != null)
      gui.getConsole().addMessage(String.format("[Group2] Looking for %d pokemons", group2Species.size()));
  }

  public class SearchListener implements IOnUpdate {
    public void onUpdate(ICommand command) {
      updateSearchSpecies();
    }
  }

  public class Group1Listener implements IOnUpdate {
    public void onUpdate(ICommand command) {
      updateGroup1Species();
    }
  }

  public class Group2Listener implements IOnUpdate {
    public void onUpdate(ICommand command) {
      updateGroup2Species();
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

  private PrioritySettings.PrioritySettingsBuilder newPrioritySettings() {
    return PrioritySettings.builder().parent(this);
  }

  @Getter
  @Log4j2
  private static class PrioritySettings extends AbstractParentCommand {

    public final EnumSetting<Priority> common;
    public final EnumSetting<Priority> group1;
    public final EnumSetting<Priority> group2;
    public final EnumSetting<Priority> search;
    public final EnumSetting<Priority> shiny;
    public final EnumSetting<Priority> nonstandard;
    public final EnumSetting<Priority> ultrabeast;
    public final EnumSetting<Priority> legendary;
    public final EnumSetting<Priority> raid;
    public final EnumSetting<Priority> wormhole;

    public final EnumSetting<Priority> bossCommon;
    public final EnumSetting<Priority> bossUncommon;
    public final EnumSetting<Priority> bossRare;
    public final EnumSetting<Priority> bossEpic;
    public final EnumSetting<Priority> bossLegendary;
    public final EnumSetting<Priority> bossUltimate;
    public final EnumSetting<Priority> bossSpooky;
    public final EnumSetting<Priority> bossDrowned;
    public final EnumSetting<Priority> bossMega;

    public final EnumSetting<Priority> tutor;
    public final EnumSetting<Priority> relearner;
    public final EnumSetting<Priority> trader;
    public final EnumSetting<Priority> fisherman;

    @Builder
    public PrioritySettings(IParentCommand parent,
                            String name, @Singular Collection<String> aliases, String description,
                            @Singular Collection<EnumFlag> flags) {
      super(parent, name, aliases, description, flags);
      common = newEnumSetting(Priority.class).name("common").description("").defaultTo(Priority.LOWEST).build();
      group1 = newEnumSetting(Priority.class).name("group1").description("").defaultTo(Priority.BELOW_AVERAGE).build();
      group2 = newEnumSetting(Priority.class).name("group2").description("").defaultTo(Priority.BELOW_AVERAGE).build();
      search = newEnumSetting(Priority.class).name("search").description("").defaultTo(Priority.BELOW_AVERAGE).build();
      shiny = newEnumSetting(Priority.class).name("shiny").description("").defaultTo(Priority.AVERAGE).build();
      nonstandard = newEnumSetting(Priority.class).name("nonstandard").description("").defaultTo(Priority.LOWEST).build();
      ultrabeast = newEnumSetting(Priority.class).name("ultrabeast").description("").defaultTo(Priority.CRITICAL).build();
      legendary = newEnumSetting(Priority.class).name("legendary").description("").defaultTo(Priority.CRITICAL).build();
      raid = newEnumSetting(Priority.class).name("raid").description("").defaultTo(Priority.HIGH).build();
      wormhole = newEnumSetting(Priority.class).name("wormhole").description("").defaultTo(Priority.LOW).build();

      bossCommon = newEnumSetting(Priority.class).name("bossCommon").description("").defaultTo(Priority.NONE).build();
      bossUncommon = newEnumSetting(Priority.class).name("bossUncommon").description("").defaultTo(Priority.LOWEST).build();
      bossRare = newEnumSetting(Priority.class).name("bossRare").description("").defaultTo(Priority.LOW).build();
      bossEpic = newEnumSetting(Priority.class).name("bossEpic").description("").defaultTo(Priority.BELOW_AVERAGE).build();
      bossLegendary = newEnumSetting(Priority.class).name("bossLegendary").description("").defaultTo(Priority.BELOW_AVERAGE).build();
      bossUltimate = newEnumSetting(Priority.class).name("bossUltimate").description("").defaultTo(Priority.AVERAGE).build();
      bossSpooky = newEnumSetting(Priority.class).name("bossSpooky").description("").defaultTo(Priority.ABOVE_AVERAGE).build();
      bossDrowned = newEnumSetting(Priority.class).name("bossDrowned").description("").defaultTo(Priority.LOWEST).build();
      bossMega = newEnumSetting(Priority.class).name("bossMega").description("").defaultTo(Priority.HIGH).build();

      tutor = newEnumSetting(Priority.class).name("tutor").description("").defaultTo(Priority.LOW).build();
      relearner = newEnumSetting(Priority.class).name("relearner").description("").defaultTo(Priority.LOW).build();
      trader = newEnumSetting(Priority.class).name("trader").description("").defaultTo(Priority.LOW).build();
      fisherman = newEnumSetting(Priority.class).name("fisherman").description("").defaultTo(Priority.LOW).build();
      onFullyConstructed();
    }

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
                                      @NonNull IArgument<T> multiarg,
                                      @Singular List<ICommandListener> listeners) {
      super(parent, name, aliases, description, flags);
      list = SimpleSettingList.<T>builder().parent(this)
          .name("list")
          .description("")
          .supplier(supplier)
          .argument(argument)
          .defaultTo(defaultTo)
          .listeners(listeners)
          .build();

      newSimpleCommand()
          .name("multiadd")
          .description("Multi add space split items")
          .argument(multiarg)
          .executor(args -> {
            String arg = (String) args.get(0).getValue();
            String[] split = arg.split(" ");
            for (String s : split) {
              list.add(multiarg.parse(s));
            }
          })
          .build();
      onFullyConstructed();
    }
  }

  @Getter
  @Log4j2
  private static class ColorPaletteSettings extends AbstractParentCommand {

    public final ColorSetting common;
    public final ColorSetting group1;
    public final ColorSetting group2;
    public final ColorSetting search;
    public final ColorSetting shiny;
    public final ColorSetting nonstandard;
    public final ColorSetting ultrabeast;
    public final ColorSetting legendary;
    public final ColorSetting raid;
    public final ColorSetting wormhole;
    public final ColorSetting trader;
    public final ColorSetting relearner;
    public final ColorSetting fisherman;
    public final ColorSetting tutor;

    public final ColorSetting catchable;
    public final ColorSetting uncatchable;

    @Builder
    public ColorPaletteSettings(IParentCommand parent,
                                String name, @Singular Collection<String> aliases, String description,
                                @Singular Collection<EnumFlag> flags) {
      super(parent, name, aliases, description, flags);
      common = newColorSetting().name("common").description("").defaultTo(Colors.SILVER).build();
      group1 = newColorSetting().name("group1").description("").defaultTo(Colors.ALICE_BLUE).build();
      group2 = newColorSetting().name("group2").description("").defaultTo(Colors.AQUA).build();
      shiny = newColorSetting().name("shiny").description("").defaultTo(Colors.GOLDEN_ROD).build();
      legendary = newColorSetting().name("legendary").description("").defaultTo(Colors.MEDIUM_ORCHID).build();
      search = newColorSetting().name("search").description("").defaultTo(Colors.LIGHT_BLUE).build();
      ultrabeast = newColorSetting().name("ultrabeast").description("").defaultTo(Colors.MEDIUM_ORCHID).build();
      wormhole = newColorSetting().name("wormhole").description("").defaultTo(Colors.DARK_MAGENTA).build();
      raid = newColorSetting().name("raid").description("").defaultTo(Colors.DARK_RED).build();
      tutor = newColorSetting().name("tutor").description("").defaultTo(Colors.KHAKI).build();
      relearner = newColorSetting().name("relearner").description("").defaultTo(Colors.KHAKI).build();
      trader = newColorSetting().name("trader").description("").defaultTo(Colors.KHAKI).build();
      fisherman = newColorSetting().name("fisherman").description("").defaultTo(Colors.KHAKI).build();
      nonstandard = newColorSetting().name("nonstandard").description("").defaultTo(Colors.CHOCOLATE).build();


      catchable = newColorSetting()
          .name("catchable")
          .description("Sets color of background for catchable pokemons")
          .defaultTo(Color.of(0, 0, 0, 50))
          .build();

      uncatchable = newColorSetting()
          .name("uncatchable")
          .description("Sets color of background for uncatchable pokemons, trainers, bosses")
          .defaultTo(Color.of(50, 0, 0, 50))
          .build();

      onFullyConstructed();
    }

  }

  @Getter
  @Log4j2
  private static class FormatSettings extends AbstractParentCommand {

    public final StringSetting nameFormat;
    public final StringSetting bossNameFormat;
    public final StringSetting npcNameFormat;
    public final StringSetting skillFormat;
    public final StringSetting statFormat;
    public final StringSetting specFormat;


    @Builder
    public FormatSettings(IParentCommand parent,
                          String name, @Singular Collection<String> aliases, String description,
                          @Singular Collection<EnumFlag> flags) {
      super(parent, name, aliases, description, flags);
      nameFormat = newStringSetting()
          .name("nameFormat")
          .description("Available keys {name, lvl, dist}")
          .defaultTo("{name} lv{lvl} {dist}m.")
          .build();
      bossNameFormat = newStringSetting()
          .name("bossNameFormat")
          .description("Available keys {name, lvl, rarity, dist}")
          .defaultTo("{name} lv{lvl} ({rarity}) {dist}m.")
          .build();
      npcNameFormat = newStringSetting()
          .name("npcNameFormat")
          .description("Available keys {name, dist}")
          .defaultTo("{name} {dist}m.")
          .build();
      skillFormat = newStringSetting()
          .name("skillFormat")
          .description("Available keys {0, 1, 2, 3}")
          .defaultTo("{0} | {1}\n{2} | {3}")
          .build();
      statFormat = newStringSetting()
          .name("statFormat")
          .description("Available keys {hp, atk, def, spd, spa, spd, ivprc (ivhp, evhp, etc..)}")
          .defaultTo("HP: {hp} {ivhp} SP: {spd} {ivspd}\nAT: {atk} {ivatk} PD: {def} {ivdef}\nSA: {sat} {ivsat} SD: {sdf} {ivsdf}\nIvs: {ivprc}")
          .build();
      specFormat = newStringSetting()
          .name("specFormat")
          .description("Available keys {gender, growth, nature, palette}")
          .defaultTo("Gender: {gender}   {palette}\nGrowth: {growth}\nPersona: {nature}")
          .build();
      onFullyConstructed();
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
    public final FloatSetting lookThreshold;
    public final IntegerSetting maxWidgets;

    public final IntegerSetting maxLookingDistance;
    public final FloatSetting lineThicknessBelowAverage;
    public final FloatSetting lineThicknessAboveAverage;
    public final FloatSetting minNameScale;
    public final FloatSetting maxNameScale;
    public final IntegerSetting maxdNameScale;
    public final IntegerSetting mindNameScale;
    public final BooleanSetting removeNameAtMinDistance;
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
      info = newEnumSetting(Priority.class)
          .name("info")
          .description("Draw info if priority is higher or equal than")
          .defaultTo(Priority.LOWEST)
          .build();
      this.names = newEnumSetting(Priority.class)
          .name("names")
          .description("Draw widget if priority is higher or equal than")
          .defaultTo(Priority.LOWEST)
          .build();
      widget = newEnumSetting(Priority.class)
          .name("widget")
          .description("Draw widget if priority is higher or equal than")
          .defaultTo(Priority.LOW)
          .build();
      trace = newEnumSetting(Priority.class)
          .name("trace")
          .description("Draw trace if priority is higher or equal than")
          .defaultTo(Priority.BELOW_AVERAGE)
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
      minNameScale = newFloatSetting()
          .name("minNameScale")
          .description("Scale of names for minimum distance")
          .defaultTo(0.5f)
          .build();
      maxNameScale = newFloatSetting()
          .name("maxNameScale")
          .description("Scale of names for maximum distance")
          .defaultTo(0.5f)
          .build();
      maxdNameScale = newIntegerSetting()
          .name("maxdNameScale")
          .description("Maximum distance for name scaling")
          .defaultTo(64)
          .build();
      mindNameScale = newIntegerSetting()
          .name("mindNameScale")
          .description("Minimum distance for name scaling")
          .defaultTo(8)
          .build();
      removeNameAtMinDistance = newBooleanSetting()
          .name("removeNameAtMin")
          .description("Prevent rendering if below min distance")
          .defaultTo(true)
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
      maxWidgets = newIntegerSetting()
          .name("maxWidgets")
          .description("Maximum widgets to be displayed at once")
          .defaultTo(6)
          .build();
      lookThreshold = newFloatSetting()
          .name("lookThreshold")
          .description("Threshold used to determine isPlayerLookingAt state relative to screen size")
          .defaultTo(0.05)
          .build();
      onFullyConstructed();
    }
  }

  TypeConverter<SearchQuery> searchQueryTypeConverter = new SearchQueryType();

  private class SearchQueryType extends TypeConverter<SearchQuery> {


    @Override
    public String label() {
      return "query";
    }

    @Override
    public Class<SearchQuery> type() {
      return SearchQuery.class;
    }

    @Override
    public SearchQuery parse(String value) {
      String[] args = value.split(" ");
      if (args.length == 0) {
        throw new IllegalArgumentException("Expected pokemon name");
      }
      SearchQuery query = new SearchQuery();

      query.pokemon = args[0];
      RegistryValue<Species> pokemon = PixelmonSpecies.get(query.pokemon).orElse(null);
      if (pokemon == null) {
        throw new IllegalArgumentException(String.format("Pokemon %s not found", query.pokemon));
      }

      for (int i = 1; i < args.length; ++i) {
        String arg = args[i];
        try {
          float ivs = Float.parseFloat(arg);
          query.ivs = ivs > 0 ? ivs : 0;
          continue;
        } catch (Throwable x) {
        }
        if (Arrays.stream(GrowthEnum.values()).anyMatch(x -> x.name().equalsIgnoreCase(arg))) {
//          String lowercase = arg.toLowerCase();
//          String normalized = lowercase.substring(0, 1).toUpperCase() + arg.toLowerCase().substring(1);
          query.growth.add(GrowthEnum.valueOf(arg.toUpperCase()));
        } else if (Arrays.stream(NatureEnum.values()).anyMatch(x -> x.name().equalsIgnoreCase(arg))) {
          query.nature.add(NatureEnum.valueOf(arg.toUpperCase()));
        } else if (Arrays.stream(GenderEnum.values()).anyMatch(x -> x.name().equalsIgnoreCase(arg))) {
          query.gender.add(GenderEnum.valueOf(arg.toUpperCase()));
        } else {
          throw new IllegalArgumentException(String.format("Argument %s is unknown to growth, nature or gender", arg));
        }
      }
      return query;
    }

    @Override
    public String convert(SearchQuery value) {
      StringBuilder specs = new StringBuilder();
      specs.append(value.pokemon);
      specs.append(' ');
      specs.append(value.ivs);
      specs.append(' ');
      for (GenderEnum g : value.gender) {
        specs.append(g.toString());
        specs.append(' ');
      }
      for (NatureEnum g : value.nature) {
        specs.append(g.toString());
        specs.append(' ');
      }
      for (GrowthEnum g : value.growth) {
        specs.append(g.toString());
        specs.append(' ');
      }
      if (specs.length() > 0) specs.deleteCharAt(specs.length() - 1);
      return specs.toString();
    }
  }

  public class SearchQuery {
    public String pokemon;
    public HashSet<GrowthEnum> growth;
    public HashSet<NatureEnum> nature;
    public HashSet<GenderEnum> gender;
    public float ivs;

    public SearchQuery() {
      pokemon = "";
      growth = new HashSet<>();
      nature = new HashSet<>();
      gender = new HashSet<>();
      ivs = 0.f;
    }

    @Override
    public String toString() {
      StringBuilder specs = new StringBuilder();
      for (GenderEnum g : gender) {
        specs.append(g.toString());
        specs.append(',');
      }
      for (NatureEnum g : nature) {
        specs.append(g.toString());
        specs.append(',');
      }
      for (GrowthEnum g : growth) {
        specs.append(g.toString());
        specs.append(',');
      }
      if (specs.length() > 0) specs.deleteCharAt(specs.length() - 1);
      return String.format("%s [%s] (IVs >= %.2f)", pokemon, specs.toString(), ivs * 100);
    }
  }
}

