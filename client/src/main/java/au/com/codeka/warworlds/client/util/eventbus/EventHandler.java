package au.com.codeka.warworlds.client.util.eventbus;

import au.com.codeka.warworlds.client.concurrency.Threads;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {
  /** Which thread should the callback be called on, {@link Threads#UI}, etc? */
  Threads thread() default Threads.UI;
}
