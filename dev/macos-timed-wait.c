/* Native QA clock compatibility: macOS libfaketime does not interpose pthread
 * absolute timed waits. Translate the frozen CLOCK_REALTIME deadline back to a
 * real deadline while leaving relative/monotonic waits untouched. */
#include <pthread.h>
#include <stdint.h>
#include <sys/time.h>
#include <time.h>

static int reference_cond_timedwait(pthread_cond_t *condition, pthread_mutex_t *mutex,
                                    const struct timespec *deadline) {
    struct timeval frozen;
    gettimeofday(&frozen, NULL);
    /* This Darwin API is not interposed by libfaketime; a startup probe verifies it. */
    uint64_t real = clock_gettime_nsec_np(CLOCK_REALTIME);
    int64_t remaining = ((int64_t)deadline->tv_sec - frozen.tv_sec) * 1000000000LL
        + deadline->tv_nsec - (int64_t)frozen.tv_usec * 1000LL;
    uint64_t adjusted = remaining > 0 ? real + (uint64_t)remaining : real;
    struct timespec actual = { (time_t)(adjusted / 1000000000ULL), (long)(adjusted % 1000000000ULL) };
    return pthread_cond_timedwait(condition, mutex, &actual);
}

__attribute__((used)) static struct {
    const void *replacement;
    const void *original;
} interpose_wait __attribute__((section("__DATA,__interpose"))) = {
    (const void *)reference_cond_timedwait,
    (const void *)pthread_cond_timedwait
};
