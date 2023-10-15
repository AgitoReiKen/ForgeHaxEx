package dev.fiki.forgehax.api.typeconverter.type;

import dev.fiki.forgehax.api.color.Color;
import dev.fiki.forgehax.api.color.Colors;
import dev.fiki.forgehax.api.typeconverter.TypeConverter;

public class ColorType extends TypeConverter<Color> {
  @Override
  public String label() {
    return "color";
  }

  @Override
  public Class<Color> type() {
    return Color.class;
  }

  @Override
  public Color parse(String value) {
    String[] split = value.split(" ");
    if (split.length == 0)
    {
      return Colors.BLACK;
    }
    /*
    * white 0.5
    * */
    Color color = Colors.map().color(split[0]);
    if (color != null && split.length >= 2)
    {
      float opacity = Float.parseFloat(split[1]);
      color.setAlpha(opacity > 1.0 ? opacity : opacity * 255);
      return color;
    }
    /*
    * 0xDEADFACE
    * */
    if(split[0].startsWith("0x") || split[0].startsWith("#")) {
      return Color.of(Integer.parseUnsignedInt(
          value.substring(
              split[0].startsWith("#") ? 1 : 2
          ), 16));
    }

    if(split.length < 3) throw new IllegalArgumentException("color expected 3 or 4 arguments, got " + split.length);

    return split.length == 3
        ? Color.of(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]))
        : Color.of(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]));

  }

  @Override
  public String convert(Color value) {
    return value.getName() != null ? value.getName() : value.toString();
  }
}
