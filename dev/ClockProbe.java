import java.time.Instant;
import java.util.concurrent.locks.LockSupport;

/** Verify frozen application dates and live duration clocks on the native JVM. */
public class ClockProbe {
    public static void main(String[] args) throws Exception {
        long expected = Long.parseLong(args[0]);
        if (System.currentTimeMillis() != expected) throw new AssertionError("Wall clock is not frozen");
        long start = System.nanoTime();
        Object monitor = new Object();
        synchronized (monitor) { monitor.wait(200); }
        LockSupport.parkNanos(200_000_000L);
        Thread.sleep(200);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        if (elapsed < 550 || elapsed > 5000) throw new AssertionError("Duration clock or timed waits failed: " + elapsed);
        if (System.currentTimeMillis() != expected) throw new AssertionError("Wall clock advanced");
        System.out.println("PASS frozen=" + Instant.now() + " duration_ms=" + elapsed);
    }
}
