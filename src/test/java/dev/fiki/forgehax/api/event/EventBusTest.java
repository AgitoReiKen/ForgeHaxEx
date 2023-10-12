package dev.fiki.forgehax.api.event;

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EventBusTest {
  private static EventBus eventBus;
  private static TestListenable object;

  @BeforeAll
  static void setup() {
    eventBus = new EventBus();
    object = new TestListenable();
  }

  @Test
  @DisplayName("registering events")
  @Order(1)
  void registerEvent() {
    /*
     // access flags 0x8
      static <clinit>()V
      L0
      LINENUMBER 95 L0
      NEW dev/fiki/forgehax/api/event/ListenerList
      DUP
      LDC Ldev/fiki/forgehax/api/event/EventBusTest$TestEventImplExt;.class
      INVOKESPECIAL dev/fiki/forgehax/api/event/ListenerList.<init> (Ljava/lang/Class;)V
      PUTSTATIC dev/fiki/forgehax/api/event/EventBusTest$TestEventImplExt.LISTENER_LIST : Ldev/fiki/forgehax/api/event/ListenerList;
      RETURN
      MAXSTACK = 3
      MAXLOCALS = 0
     */
    //@BUG without accessing listenerList, class B derived from class A will use class A listener too when posting.
    // I dunno if this issue continues in runtime
    // I want to play pokemons already, not going to full scale investigation
    // Seems clinit is not used properly.
    TestEventImpl.listenerList();
    TestEventImplExt.listenerList();
    eventBus.register(object);

    assertThat(TestEventImpl.listenerList())
        .describedAs("TestEventImpl should have one listener attached")
        .hasSize(1)
        .describedAs("TestEventImpl listener should match our object")
        .filteredOn(EventListenerWrapper.class::isInstance)
        .map(EventListenerWrapper.class::cast)
        .allMatch(e -> e.getDeclaringInstance() == object);

    assertThat(TestEventImplExt.listenerList())
        .describedAs("TestEventImplExt should have two listeners attached")
        .hasSize(2)
        .describedAs("TestEventImplExt listeners should match our object")
        .filteredOn(EventListenerWrapper.class::isInstance)
        .map(EventListenerWrapper.class::cast)
        .allMatch(e -> e.getDeclaringInstance() == object);
  }

  @Test
  @DisplayName("post TestEventImpl")
  @Order(2)
  void postEvent() {
    TestEventImplExt event = new TestEventImplExt();
    eventBus.post(event);

    assertThat(event.touches)
        .describedAs("::TestEventImplExt_Handler and ::TestEventImpl_Handler are invoked")
        .containsOnly("TestEventImplExt_Handler", "TestEventImpl_Handler");
  }

  @Test
  @DisplayName("post TestEventImplExt")
  @Order(3)
  void postSuperEvent() {
    TestEventImpl event = new TestEventImpl();
    eventBus.post(event);

    assertThat(event.touches)
        .describedAs("only ::TestEventImpl_Handler is invoked")
        .containsOnly("TestEventImpl_Handler");
  }

  @Test
  @DisplayName("unregistering events")
  @Order(999)
  void unregisterEvent() {
    eventBus.unregister(object);

    assertThat(TestEventImplExt.listenerList())
        .describedAs("all listeners should be removed")
        .isEmpty();

    assertThat(TestEventImpl.listenerList())
        .describedAs("all listeners should be removed")
        .isEmpty();
  }

  //
  //
  //

  public static class TestEventImpl extends Event {
    List<String> touches = new ArrayList<>();
  }

  @Cancelable
  public static class TestEventImplExt extends TestEventImpl {

  }

  public static class TestListenable {
    @SubscribeListener
    public void TestEventImplExt_Handler(TestEventImplExt event) {
      event.touches.add("TestEventImplExt_Handler");
    }

    @SubscribeListener
    public void TestEventImpl_Handler(TestEventImpl event) {
      event.touches.add("TestEventImpl_Handler");
    }
  }
}
