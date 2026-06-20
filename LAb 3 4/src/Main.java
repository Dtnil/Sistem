import java.util.*;

/*
 * Лабораторна робота №4.
 * Моделювання управління процесами та потоками в ОС Linux.
 *
 * Варіант 13:
 * P1: 35 МБ, ресурси R1 -> R2 -> R5, пріоритет M
 * P2: 54 МБ, ресурси R2 -> R3 -> R4, пріоритет H
 * P3: 24 МБ, ресурси R2 -> R4, пріоритет H
 *
 * Загальний обсяг оперативної пам'яті: 96 МБ.
 * Кількість ресурсів: 5.
 */

class Lab4LinuxProcesses {

    // Стани процесу за аналогією з Linux.
    enum LinuxState {
        NEW,
        READY,
        RUNNING,
        WAITING,
        SUSPENDED,
        ZOMBIE
    }

    // Рівні пріоритетів.
    static final int LOW = 3;
    static final int MEDIUM = 5;
    static final int HIGH = 7;

    static final Object PRINT_LOCK = new Object();

    // П'ять спільних ресурсів.
    static final Resource[] RESOURCES = {
            new Resource(1),
            new Resource(2),
            new Resource(3),
            new Resource(4),
            new Resource(5)
    };

    // Ядро моделі: керує пам'яттю та процесами.
    static final Kernel KERNEL = new Kernel(96, RESOURCES);

    static void print(String text) {
        synchronized (PRINT_LOCK) {
            System.out.println(text);
        }
    }

    static String priorityName(int priority) {
        if (priority == HIGH) {
            return "H (високий)";
        }

        if (priority == MEDIUM) {
            return "M (нормальний)";
        }

        return "L (низький)";
    }

    // ==================== РЕСУРС ====================

    static class Resource {
        private final int number;
        private ProcessModel owner;

        // Черга очікування процесів за спаданням пріоритету.
        private final PriorityQueue<ProcessModel> waiting =
                new PriorityQueue<>(
                        Comparator.comparingInt((ProcessModel p) -> p.priority)
                                .reversed()
                                .thenComparingInt(p -> p.id)
                );

        Resource(int number) {
            this.number = number;
        }

        synchronized void removeWaitingProcess(ProcessModel process) {
            if (waiting.remove(process)) {
                notifyAll();
            }
        }

        void use(ProcessModel process) throws InterruptedException {

            while (true) {
                // Якщо процес був витіснений з пам'яті,
                // він очікує можливості відновлення.
                process.awaitResumeIfNeeded();

                synchronized (this) {
                    boolean resourceIsFree = owner == null;

                    boolean firstInPriorityQueue =
                            waiting.isEmpty() || waiting.peek() == process;

                    // Ресурс вільний і процес має найвищий пріоритет у черзі.
                    if (resourceIsFree && firstInPriorityQueue) {
                        waiting.remove(process);
                        owner = process;
                        break;
                    }

                    // Додаємо процес у чергу очікування.
                    if (!waiting.contains(process)) {
                        waiting.add(process);
                    }

                    process.changeState(
                            LinuxState.WAITING,
                            "очікує звільнення ресурсу R" + number
                    );

                    wait(250);
                }
            }

            process.changeState(
                    LinuxState.RUNNING,
                    "захопив ресурс R" + number
            );

            print("    " + process.name +
                    " використовує ресурс R" + number + "...");

            // Імітація роботи процесу з ресурсом.
            for (int i = 0; i < 3; i++) {
                Thread.sleep(350);
            }

            synchronized (this) {
                owner = null;
                notifyAll();
            }

            process.changeState(
                    LinuxState.READY,
                    "звільнив ресурс R" + number +
                            "; завершився квант часу"
            );
        }
    }

    // ==================== ЯДРО / ПАМ'ЯТЬ ====================

    static class Kernel {
        private final int totalRam;
        private int freeRam;

        private final List<ProcessModel> processes = new ArrayList<>();
        private final Resource[] resources;

        Kernel(int totalRam, Resource[] resources) {
            this.totalRam = totalRam;
            this.freeRam = totalRam;
            this.resources = resources;
        }

        synchronized void register(ProcessModel process) {
            processes.add(process);
        }

        // Пошук процесу з нижчим пріоритетом для витіснення.
        private ProcessModel findLowerPriorityVictim(ProcessModel requester) {
            return processes.stream()
                    .filter(p -> p != requester)
                    .filter(p -> p.memoryAllocated)
                    .filter(p -> p.priority < requester.priority)

                    // Не витісняємо процес, який прямо зараз працює з ресурсом.
                    .filter(p -> p.state == LinuxState.READY ||
                            p.state == LinuxState.WAITING)

                    .min(Comparator.comparingInt(p -> p.priority))
                    .orElse(null);
        }

        synchronized void requestMemory(ProcessModel process)
                throws InterruptedException {

            while (freeRam < process.ram) {

                ProcessModel victim = findLowerPriorityVictim(process);

                // Якщо є процес з нижчим пріоритетом — витісняємо його.
                if (victim != null) {
                    victim.suspended = true;
                    victim.memoryAllocated = false;

                    freeRam += victim.ram;

                    victim.changeState(
                            LinuxState.SUSPENDED,
                            "витіснений процесом " + process.name +
                                    "; звільнено " + victim.ram + " МБ ОЗП"
                    );

                    // Витіснений процес забирається з черг ресурсів.
                    for (Resource resource : resources) {
                        resource.removeWaitingProcess(victim);
                    }

                    continue;
                }

                // Якщо витіснити нікого неможливо — процес очікує пам'ять.
                process.changeState(
                        LinuxState.SUSPENDED,
                        "недостатньо ОЗП; вільно " + freeRam +
                                " МБ із " + totalRam + " МБ"
                );

                wait();
            }

            freeRam -= process.ram;

            process.memoryAllocated = true;
            process.suspended = false;

            process.changeState(
                    LinuxState.READY,
                    "отримав " + process.ram +
                            " МБ ОЗП; вільно " + freeRam + " МБ"
            );
        }

        synchronized void restoreMemory(ProcessModel process)
                throws InterruptedException {

            while (freeRam < process.ram) {
                process.changeState(
                        LinuxState.SUSPENDED,
                        "очікує повернення " + process.ram +
                                " МБ ОЗП; вільно " + freeRam + " МБ"
                );

                wait();
            }

            freeRam -= process.ram;

            process.memoryAllocated = true;
            process.suspended = false;

            process.changeState(
                    LinuxState.READY,
                    "відновлений у пам'яті; вільно " +
                            freeRam + " МБ"
            );
        }

        synchronized void releaseMemory(ProcessModel process) {
            if (process.memoryAllocated) {
                freeRam += process.ram;
                process.memoryAllocated = false;

                print("    " + process.name +
                        " звільнив " + process.ram +
                        " МБ ОЗП; вільно " +
                        freeRam + " МБ");

                notifyAll();
            }
        }
    }

    // ==================== ПРОЦЕС ====================

    static class ProcessModel extends Thread {
        final int id;
        final String name;
        final int ram;
        final Resource[] plan;
        final int priority;
        final long startDelayMs;

        volatile LinuxState state = LinuxState.NEW;

        volatile boolean memoryAllocated = false;
        volatile boolean suspended = false;

        ProcessModel(
                int id,
                String name,
                int ram,
                int[] resourceNumbers,
                int priority,
                long startDelayMs
        ) {
            super(name);

            this.id = id;
            this.name = name;
            this.ram = ram;
            this.priority = priority;
            this.startDelayMs = startDelayMs;

            this.plan = new Resource[resourceNumbers.length];

            for (int i = 0; i < resourceNumbers.length; i++) {
                this.plan[i] = RESOURCES[resourceNumbers[i] - 1];
            }

            // Встановлення стандартного пріоритету потоку Java.
            setPriority(
                    priority == HIGH ? Thread.MAX_PRIORITY :
                            priority == MEDIUM ? Thread.NORM_PRIORITY :
                            Thread.MIN_PRIORITY
            );

            KERNEL.register(this);
        }

        void changeState(LinuxState newState, String reason) {
            LinuxState oldState = state;

            if (oldState == newState) {
                return;
            }

            state = newState;

            print(name + ": " +
                    oldState + " -> " + newState +
                    ". " + reason);
        }

        void awaitResumeIfNeeded() throws InterruptedException {
            while (suspended) {
                KERNEL.restoreMemory(this);
            }
        }

        @Override
        public void run() {
            try {
                print(name + ": створено. Пріоритет " +
                        priorityName(priority) +
                        ", потрібно ОЗП: " + ram + " МБ.");

                // Імітація надходження процесів у різний час.
                Thread.sleep(startDelayMs);

                // Отримання пам'яті.
                KERNEL.requestMemory(this);

                // Послідовне використання ресурсів.
                for (Resource resource : plan) {
                    awaitResumeIfNeeded();

                    resource.use(this);

                    Thread.sleep(180);
                }

                // Звільнення пам'яті після завершення.
                KERNEL.releaseMemory(this);

                changeState(
                        LinuxState.ZOMBIE,
                        "виконання завершено; запис процесу ще зберігається в таблиці"
                );

                print(name + ": ZOMBIE -> ВИХІД.");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                print(name + ": виконання перервано.");
            }
        }
    }

    // ==================== ГОЛОВНИЙ МЕТОД ====================

    public static void main(String[] args) throws InterruptedException {

        print("=== ЛР №4. Моделювання керування процесами та потоками Linux ===");
        print("Варіант 13: ОЗП = 96 МБ; ресурсів = 5.");
        print("P1: 35 МБ, R1 -> R2 -> R5, M");
        print("P2: 54 МБ, R2 -> R3 -> R4, H");
        print("P3: 24 МБ, R2 -> R4, H");
        print("---------------------------------------------------------------");

        /*
         * Затримки потрібні, щоб у роботі програми було видно:
         * - конкуренцію за ресурси;
         * - очікування ресурсу;
         * - витіснення P1 процесом з вищим пріоритетом;
         * - відновлення призупиненого процесу.
         */

        ProcessModel p1 = new ProcessModel(
                1,
                "Процес P1",
                35,
                new int[]{1, 2, 5},
                MEDIUM,
                0
        );

        ProcessModel p2 = new ProcessModel(
                2,
                "Процес P2",
                54,
                new int[]{2, 3, 4},
                HIGH,
                350
        );

        ProcessModel p3 = new ProcessModel(
                3,
                "Процес P3",
                24,
                new int[]{2, 4},
                HIGH,
                1300
        );

        p1.start();
        p2.start();
        p3.start();

        p1.join();
        p2.join();
        p3.join();

        print("---------------------------------------------------------------");
        print("Моделювання завершено.");
    }
}