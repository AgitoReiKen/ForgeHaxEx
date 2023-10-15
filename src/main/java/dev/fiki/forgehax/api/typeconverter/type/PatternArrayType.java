package dev.fiki.forgehax.api.typeconverter.type;


import dev.fiki.forgehax.api.typeconverter.TypeConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PatternArrayType extends TypeConverter<List> {
  @Override
  public String label() {
    return "regex[]";
  }

  @Override
  public Class<List> type() {
    return List.class;
  }

  @Override
  public List<Pattern> parse(String value) {
    String[] s = value.split(" ");
    List<Pattern> result = new ArrayList<>();
    for (String it : s) result.add(Pattern.compile(it));
    return result;
  }

  @Override
  public String convert(List value) {
    StringBuilder result = new StringBuilder();
    for (Pattern it : (List<Pattern>)value)
    {
      result.append(it.pattern()).append(" ");
    }
    if (result.length() > 0) {
      result.deleteCharAt(result.length() - 1);
    }
    return result.toString();
  }
}
