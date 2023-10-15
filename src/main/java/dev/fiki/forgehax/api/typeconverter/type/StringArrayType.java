package dev.fiki.forgehax.api.typeconverter.type;

import dev.fiki.forgehax.api.typeconverter.TypeConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringArrayType  extends TypeConverter<List> {
  @Override
  public String label() {
    return "string[]";
  }

  @Override
  public Class<List> type() {
    return List.class;
  }

  @Override
  public List<String> parse(String value) {
    return Arrays.stream(value.split(" ")).collect(Collectors.toList());
  }

  @Override
  public String convert(List value) {
    return String.join(" ", (List<String>)value);
  }
}
