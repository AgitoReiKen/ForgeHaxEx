package dev.fiki.forgehax.api.extension;

import java.util.ArrayList;
import java.util.Map;

public class StringEx {
  public static String format(String template, Map<String, Object> arguments) {
    for (Map.Entry<String, Object> entry : arguments.entrySet()) {
      template = template.replace("{" + entry.getKey() + "}", entry.getValue().toString());
    }
    return template;
  }

}
