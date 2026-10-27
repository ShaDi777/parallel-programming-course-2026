package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    final Scheduler scheduler;
    int ownerThreadId = -1;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        this.scheduler = s;
        s.registerLock(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        scheduler.setWantLock(this);
        Scheduler.pauseBeforeShared();
        while (!scheduler.tryAcquireLock(this)) {
            Scheduler.blockOnLock();
        }
        scheduler.setWantLock(null);
        Scheduler.doLog("lock " + name);
        Scheduler.markTouched();
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        Scheduler.pauseBeforeShared();
        scheduler.releaseLock(this);
        Scheduler.doLog("unlock " + name);
        Scheduler.markTouched();
    }
}