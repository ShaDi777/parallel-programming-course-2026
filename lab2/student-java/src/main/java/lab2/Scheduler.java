package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;
    /** трейс / путь / трасса / порядок выполнения потоков */
    private final StringBuilder trace = new StringBuilder();

    private static final ThreadLocal<Scheduler> SCHEDULER = new ThreadLocal<>(); // общий планировщик
    private static final ThreadLocal<Integer> TID = new ThreadLocal<>(); // текущий поток

    private final List<Thread> threads = new ArrayList<>();
    private final List<Semaphore> threadSemaphores = new ArrayList<>(); // семафоры для старта ходов потоков
    private final Semaphore schedulerSemaphore = new Semaphore(0); // семафор для хода планировщика

    private final List<Boolean> finished = new ArrayList<>(); // полностью завершенные потоки
    private final List<Boolean> awaited = new ArrayList<>(); // ждут в await
    private final List<Boolean> dirty = new ArrayList<>(); // трогал общее состояние с прошлой паузы
    private final List<Boolean> forcedPause = new ArrayList<>(); // point()/await требуют паузу перед следующей операцией

    private final List<SchedLock> locks = new ArrayList<>(); // все локи списком
    private final List<SchedLock> wants = new ArrayList<>(); // список на то, какой лок ждет каждый поток (null значит не ждет)
    private boolean aborted = false; // запуск прерван

    private int last = -1;

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        int id = threads.size();
        threads.add(new Thread(() -> {
            SCHEDULER.set(this);
            TID.set(id);
            try {
                body.run();
            } catch (RunAbortedException _) {
                // Завершение из-за дедлока
            } finally {
                finished.set(id, true);
                schedulerSemaphore.release();
            }
        }));
        threadSemaphores.add(new Semaphore(0));
        finished.add(false);
        awaited.add(false);
        dirty.add(false);
        forcedPause.add(true);
        wants.add(null);
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        // Запустить каждый поток и довести его до паузы
        for (Thread thread : threads) {
            thread.start();
            try {
                schedulerSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        // Главный цикл: enabled -> strategy.pick -> разбудить -> ждать
        while (true) {
            // enabled
            List<Integer> enabled = new ArrayList<>();
            for (int i = 0; i < threads.size(); i++) {
                if (finished.get(i) || awaited.get(i)) continue;

                SchedLock w = wants.get(i);
                if (w != null && w.ownerThreadId != -1) continue;

                enabled.add(i);
            }
            if (enabled.isEmpty()) {
                if (!finished.stream().allMatch(Boolean::booleanValue)) {
                    deadlock = deadlockMessage();
                    abortThreads();
                }
                break;
            }

            // strategy.pick
            try {
                int chosen = strategy.pick(enabled, last);
                last = chosen;
                trace.append(letter(chosen));
                threadSemaphores.get(chosen).release(); // разбудить
            } catch (Exception e) {
                threads.forEach(Thread::interrupt);
                throw e;
            }

            try {
                schedulerSemaphore.acquire(); // ждать
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        // подождать все потоки
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        Scheduler s = SCHEDULER.get();
        doLog("window");
        s.forcedPause.set(TID.get(), true);
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        Scheduler s = SCHEDULER.get();
        int id = TID.get();
        pauseBeforeShared();
        // TODO recheck cycle later
        while (!cond.getAsBoolean()) {
            s.awaited.set(id, true);
            s.yieldTurn();
            s.awaited.set(id, false);
        }
        doLog(id, "await done");
        s.forcedPause.set(id, true);
        s.dirty.set(id, true);
    }

    /** Пауза перед операцией с общим состоянием */
    static void pauseBeforeShared() {
        Scheduler s = SCHEDULER.get();
        int id = TID.get();
        boolean needPauseNow = s.forcedPause.get(id) || s.dirty.get(id);
        if (needPauseNow) {
            s.forcedPause.set(id, false);
            s.dirty.set(id, false);
            s.yieldTurn();
        }
    }

    /** Поток использовал общее состояние, будет нужна пауза */
    static void markTouched() {
        SCHEDULER.get().dirty.set(TID.get(), true);
    }

    static char letter(int id) {
        return (char) ('A' + id);
    }

    static void doLog(String operation) {
        doLog(TID.get(), operation);
    }

    static void doLog(int id, String operation) {
        Scheduler s = SCHEDULER.get();
        if (!s.logging) return;
        s.log.add(letter(id) + "  " + operation);
    }

    /** Отдать ход планировщику */
    private void yieldTurn() {
        int id = TID.get();
        schedulerSemaphore.release();
        try {
            threadSemaphores.get(id).acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        if (aborted) throw new RunAbortedException();
    }

    void registerLock(SchedLock l) {
        locks.add(l);
    }

    void setWantLock(SchedLock l) {
        wants.set(TID.get(), l);
    }

    boolean tryAcquireLock(SchedLock l) {
        if (l.ownerThreadId == -1) {
            l.ownerThreadId = TID.get();
            return true;
        }
        return false;
    }

    static void blockOnLock() {
        SCHEDULER.get().yieldTurn();
    }

    void releaseLock(SchedLock l) {
        l.ownerThreadId = -1;
    }

    private String deadlockMessage() {
        return "held=" + locks.stream()
                .filter(l -> l.ownerThreadId >= 0)
                .map(l -> l.name + ':' + letter(l.ownerThreadId))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /** Принудительно завершить все потоки при дедлоке */
    private void abortThreads() {
        aborted = true;
        for (int i = 0; i < threads.size(); i++) {
            if (!finished.get(i)) {
                threadSemaphores.get(i).release();
            }
        }
    }

    static final class RunAbortedException extends RuntimeException {
        RunAbortedException() { super("run aborted"); }
    }
}
