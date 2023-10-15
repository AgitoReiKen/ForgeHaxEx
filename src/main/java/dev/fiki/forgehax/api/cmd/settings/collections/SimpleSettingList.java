package dev.fiki.forgehax.api.cmd.settings.collections;

import dev.fiki.forgehax.api.cmd.IParentCommand;
import dev.fiki.forgehax.api.cmd.argument.Arguments;
import dev.fiki.forgehax.api.cmd.argument.ConverterArgument;
import dev.fiki.forgehax.api.cmd.argument.IArgument;
import dev.fiki.forgehax.api.cmd.flag.EnumFlag;
import dev.fiki.forgehax.api.cmd.listener.ICommandListener;
import dev.fiki.forgehax.api.cmd.value.IValue;
import dev.fiki.forgehax.api.typeconverter.TypeConverters;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.util.*;
import java.util.function.Supplier;

public final class SimpleSettingList<E> extends BaseSimpleSettingCollection<E, List<E>> implements List<E> {
  @Builder
  public SimpleSettingList(IParentCommand parent,
                           String name, @Singular Set<String> aliases, String description,
                           @Singular Set<EnumFlag> flags,
                           @NonNull Supplier<List<E>> supplier,
                           @Singular("defaultsTo") Collection<E> defaultTo,
                           @NonNull IArgument<E> argument,
                           @Singular List<ICommandListener> listeners) {
    super(parent, name, aliases, description, flags, supplier, defaultTo, argument, listeners);

    IArgument<Integer> indexArg = Arguments.newIntegerArgument().label("index").build();
    newSimpleCommand()
        .name("set")
        .alias("=")
        .description("Set item at specific index [=]")
        .arguments(Arrays.asList(indexArg, argument))
        .executor(args -> {
          IValue<Integer> i = args.getFirst();
          IValue<E> object = args.getSecond();
          if (this.size() < (i.getValue() + 1)) {
            args.warn("Index is out of bounds. Max index is %d.", this.size() - 1);
            return;
          }

          this.set(i.getValue(), object.getValue());
          args.inform("Item has been set at %d.", i.getValue());
        })
        .build();
    newSimpleCommand()
        .name("removeAt")
        .alias("~")
        .description("Remove item at specific index [~]")
        .argument(indexArg)
        .executor(args -> {
          int arg = (int)args.getFirst().getValue();
          if (this.remove(arg) != null) {
            args.inform("Item at %d has been removed .", arg);
          } else {
            args.warn("Could not remove element. Max index is %d.", this.size() - 1);
          }
        })
        .build();

    onFullyConstructed();
  }

  @Override
  public boolean addAll(int i, Collection<? extends E> collection) {
    boolean ret = this.wrapping.addAll(collection);
    if (ret) {
      callUpdateListeners();
    }
    return ret;
  }

  @Override
  public E get(int i) {
    return wrapping.get(i);
  }

  @Override
  public E set(int i, E e) {
    E v = wrapping.set(i, e);
    if (v != null) {
      callUpdateListeners();
    }
    return v;
  }

  @Override
  public void add(int i, E e) {
    int beforeSize = size();
    wrapping.add(i, e);
    if (size() != beforeSize) {
      callUpdateListeners();
    }
  }

  @Override
  public E remove(int i) {
    E ret = wrapping.remove(i);
    if (ret != null) {
      callUpdateListeners();
    }
    return ret;
  }

  @Override
  public int indexOf(Object o) {
    return wrapping.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return wrapping.lastIndexOf(o);
  }

  @Override
  public ListIterator<E> listIterator() {
    return wrapping.listIterator();
  }

  @Override
  public ListIterator<E> listIterator(int i) {
    return wrapping.listIterator(i);
  }

  @Override
  public List<E> subList(int i, int i1) {
    return wrapping.subList(i, i1);
  }
}
