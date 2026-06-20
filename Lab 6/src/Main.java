import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/*
 * Лабораторна робота №6.
 * Механізми управління віртуальною пам'яттю на рівні ОС.
 *
 * У моделі є:
 * - 5 процесів;
 * - 10 віртуальних сторінок у кожному процесі;
 * - 5 кадрів фізичної пам'яті;
 * - page fault;
 * - біт звернення R/use;
 * - біт модифікації M/dirty;
 * - алгоритми LRU, FIFO, Clock, Modified Clock.
 */
class Lab6VirtualMemory {

    private static final int FRAME_COUNT = 5;
    private static final int PROCESS_COUNT = 5;
    private static final int PAGES_PER_PROCESS = 10;

    enum Algorithm {
        LRU("LRU — найдовше не використовувана сторінка"),
        FIFO("FIFO — першим увійшов, першим вийшов"),
        CLOCK("Годинниковий алгоритм"),
        MODIFIED_CLOCK("Модифікований годинниковий алгоритм");

        private final String title;

        Algorithm(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    // Віртуальна сторінка конкретного процесу.
    static class Page {
        final int processId;
        final int number;

        Page(int processId, int number) {
            this.processId = processId;
            this.number = number;
        }

        @Override
        public String toString() {
            return "P" + processId + ":" + number;
        }
    }

    // Кадр фізичної пам'яті.
    static class Frame {
        Page page;

        // R/use = 1, якщо до сторінки нещодавно зверталися.
        boolean referenced;

        // M/dirty = 1, якщо сторінка була змінена.
        boolean modified;

        // Час завантаження сторінки в кадр.
        long loadedAt;

        // Час останнього використання сторінки.
        long lastUsedAt;

        boolean isFree() {
            return page == null;
        }

        void load(Page newPage, boolean writeOperation, long currentTime) {
            page = newPage;
            referenced = true;
            modified = writeOperation;
            loadedAt = currentTime;
            lastUsedAt = currentTime;
        }

        String shortState() {
            if (isFree()) {
                return "вільний";
            }

            return page + " [R=" + (referenced ? 1 : 0)
                    + ", M=" + (modified ? 1 : 0) + "]";
        }
    }

    // Одне звернення процесу до сторінки.
    static class Access {
        final int processId;
        final int pageNumber;
        final boolean write;

        Access(int processId, int pageNumber, boolean write) {
            this.processId = processId;
            this.pageNumber = pageNumber;
            this.write = write;
        }
    }

    static class VirtualMemoryManager {

        private final Frame[] frames;
        private final Algorithm algorithm;
        private final Map<Integer, Integer> faultsByProcess =
                new LinkedHashMap<Integer, Integer>();

        private long time = 0;
        private int clockPointer = 0;
        private int pageFaults = 0;
        private int diskWrites = 0;

        VirtualMemoryManager(int frameCount, Algorithm algorithm) {
            this.frames = new Frame[frameCount];
            this.algorithm = algorithm;

            for (int i = 0; i < frames.length; i++) {
                frames[i] = new Frame();
            }

            for (int processId = 1; processId <= PROCESS_COUNT; processId++) {
                faultsByProcess.put(processId, 0);
            }
        }

        void access(int processId, int pageNumber, boolean writeOperation) {
            validateAccess(processId, pageNumber);

            time++;

            Page requestedPage = new Page(processId, pageNumber);
            int frameIndex = findPage(processId, pageNumber);

            System.out.println("\nКрок " + time + ": процес P" + processId
                    + " звертається до сторінки " + pageNumber
                    + " (операція: "
                    + (writeOperation ? "ЗАПИС" : "ЧИТАННЯ") + ")");

            // Сторінка вже є у фізичній пам'яті.
            if (frameIndex >= 0) {
                Frame frame = frames[frameIndex];

                frame.referenced = true;
                frame.lastUsedAt = time;

                if (writeOperation) {
                    frame.modified = true;
                }

                System.out.println("  HIT: сторінка вже знаходиться у кадрі "
                        + frameIndex + ".");
            } else {
                // Сторінки у фізичній пам'яті немає.
                pageFaults++;
                faultsByProcess.put(processId,
                        faultsByProcess.get(processId) + 1);

                System.out.println("  PAGE FAULT: сторінки немає у фізичній пам'яті.");

                int targetIndex = findFreeFrame();

                // Якщо є вільний кадр, завантажуємо сторінку в нього.
                if (targetIndex >= 0) {
                    System.out.println("  Знайдено вільний кадр "
                            + targetIndex + ".");
                } else {
                    // Вільного кадру немає — вибираємо сторінку для заміщення.
                    targetIndex = chooseVictim();

                    Frame victim = frames[targetIndex];

                    System.out.println("  Для заміщення обрано кадр "
                            + targetIndex + " зі сторінкою "
                            + victim.page + ".");

                    // Якщо сторінка змінювалася, перед заміщенням
                    // її потрібно записати у зовнішню пам'ять.
                    if (victim.modified) {
                        diskWrites++;

                        System.out.println("  Сторінка " + victim.page
                                + " змінена (M=1), виконується запис на диск.");
                    } else {
                        System.out.println("  Сторінка " + victim.page
                                + " не змінена (M=0), запис на диск не потрібен.");
                    }
                }

                frames[targetIndex].load(
                        requestedPage,
                        writeOperation,
                        time
                );

                System.out.println("  Завантажено " + requestedPage
                        + " у кадр " + targetIndex + ".");

                // Для годинникових алгоритмів стрілка переходить далі.
                if (algorithm == Algorithm.CLOCK
                        || algorithm == Algorithm.MODIFIED_CLOCK) {
                    clockPointer = (targetIndex + 1) % frames.length;
                }
            }

            printFrames();
        }

        private void validateAccess(int processId, int pageNumber) {
            if (processId < 1 || processId > PROCESS_COUNT) {
                throw new IllegalArgumentException(
                        "Номер процесу повинен бути від 1 до "
                                + PROCESS_COUNT
                );
            }

            if (pageNumber < 0 || pageNumber >= PAGES_PER_PROCESS) {
                throw new IllegalArgumentException(
                        "Номер сторінки повинен бути від 0 до "
                                + (PAGES_PER_PROCESS - 1)
                );
            }
        }

        private int findPage(int processId, int pageNumber) {
            for (int i = 0; i < frames.length; i++) {
                Frame frame = frames[i];

                if (!frame.isFree()
                        && frame.page.processId == processId
                        && frame.page.number == pageNumber) {
                    return i;
                }
            }

            return -1;
        }

        private int findFreeFrame() {
            for (int i = 0; i < frames.length; i++) {
                if (frames[i].isFree()) {
                    return i;
                }
            }

            return -1;
        }

        private int chooseVictim() {
            switch (algorithm) {
                case FIFO:
                    return chooseFifoVictim();

                case LRU:
                    return chooseLruVictim();

                case CLOCK:
                    return chooseClockVictim();

                case MODIFIED_CLOCK:
                    return chooseModifiedClockVictim();

                default:
                    throw new IllegalStateException("Невідомий алгоритм.");
            }
        }

        // FIFO: вибирається сторінка, яка найдовше знаходиться в пам'яті.
        private int chooseFifoVictim() {
            int victim = 0;

            for (int i = 1; i < frames.length; i++) {
                if (frames[i].loadedAt < frames[victim].loadedAt) {
                    victim = i;
                }
            }

            return victim;
        }

        // LRU: вибирається сторінка, до якої найдовше не було звернень.
        private int chooseLruVictim() {
            int victim = 0;

            for (int i = 1; i < frames.length; i++) {
                if (frames[i].lastUsedAt < frames[victim].lastUsedAt) {
                    victim = i;
                }
            }

            return victim;
        }

        /*
         * Годинниковий алгоритм.
         * Якщо R = 1, сторінці дається "другий шанс":
         * R скидається до 0, а стрілка переходить далі.
         */
        private int chooseClockVictim() {
            while (true) {
                Frame frame = frames[clockPointer];

                if (!frame.referenced) {
                    return clockPointer;
                }

                System.out.println("  CLOCK: кадр " + clockPointer
                        + " має R=1, скидаємо R до 0.");

                frame.referenced = false;

                clockPointer = (clockPointer + 1) % frames.length;
            }
        }

        /*
         * Модифікований годинниковий алгоритм.
         *
         * Порядок пошуку:
         * 1. (R = 0, M = 0)
         * 2. (R = 0, M = 1)
         * 3. Скидання бітів R та повторний пошук.
         */
        private int chooseModifiedClockVictim() {
            int victim = findClassWithoutChangingBits(false, false);

            if (victim >= 0) {
                System.out.println("  MODIFIED CLOCK: знайдено клас (R=0, M=0).");
                return victim;
            }

            victim = findDirtyOldPageAndResetReferenceBits();

            if (victim >= 0) {
                System.out.println("  MODIFIED CLOCK: знайдено клас (R=0, M=1).");
                return victim;
            }

            // Після другого проходу R у переглянутих кадрів скинуто до 0.
            victim = findClassWithoutChangingBits(false, false);

            if (victim >= 0) {
                System.out.println("  MODIFIED CLOCK: після скидання R "
                        + "знайдено клас (R=0, M=0).");
                return victim;
            }

            victim = findClassWithoutChangingBits(false, true);

            if (victim >= 0) {
                System.out.println("  MODIFIED CLOCK: після скидання R "
                        + "знайдено клас (R=0, M=1).");
                return victim;
            }

            throw new IllegalStateException(
                    "Не вдалося знайти сторінку для заміщення."
            );
        }

        private int findClassWithoutChangingBits(
                boolean referenced,
                boolean modified
        ) {
            for (int offset = 0; offset < frames.length; offset++) {
                int index = (clockPointer + offset) % frames.length;

                Frame frame = frames[index];

                if (frame.referenced == referenced
                        && frame.modified == modified) {
                    return index;
                }
            }

            return -1;
        }

        /*
         * Другий прохід Modified Clock:
         * шукаємо (R=0, M=1) і одночасно скидаємо R=1 до R=0.
         */
        private int findDirtyOldPageAndResetReferenceBits() {
            for (int offset = 0; offset < frames.length; offset++) {
                int index = (clockPointer + offset) % frames.length;

                Frame frame = frames[index];

                if (!frame.referenced && frame.modified) {
                    return index;
                }

                if (frame.referenced) {
                    System.out.println("  MODIFIED CLOCK: у кадрі "
                            + index + " R=1, скидаємо R до 0.");

                    frame.referenced = false;
                }
            }

            return -1;
        }

        void printFrames() {
            System.out.println("  Стан фізичної пам'яті:");

            for (int i = 0; i < frames.length; i++) {
                String pointerMark = "";

                if (i == clockPointer
                        && (algorithm == Algorithm.CLOCK
                        || algorithm == Algorithm.MODIFIED_CLOCK)) {
                    pointerMark = "  <- стрілка";
                }

                System.out.println("    Кадр " + i + ": "
                        + frames[i].shortState()
                        + pointerMark);
            }
        }

        void printStatistics() {
            System.out.println("\n========== ПІДСУМКОВА СТАТИСТИКА ==========");
            System.out.println("Алгоритм: " + algorithm.getTitle());
            System.out.println("Кількість page faults: " + pageFaults);
            System.out.println("Кількість записів змінених сторінок на диск: "
                    + diskWrites);

            System.out.println("Page faults за процесами:");

            for (Map.Entry<Integer, Integer> entry
                    : faultsByProcess.entrySet()) {

                System.out.println("  P" + entry.getKey()
                        + ": " + entry.getValue());
            }
        }
    }

    public static void main(String[] args) {
        Algorithm algorithm = readAlgorithm();

        VirtualMemoryManager memory =
                new VirtualMemoryManager(FRAME_COUNT, algorithm);

        System.out.println("\nМОДЕЛЮВАННЯ ВІРТУАЛЬНОЇ ПАМ'ЯТІ ОС");
        System.out.println("Кадрів фізичної пам'яті: " + FRAME_COUNT);
        System.out.println("Процесів: " + PROCESS_COUNT);
        System.out.println("Віртуальних сторінок у кожному процесі: "
                + PAGES_PER_PROCESS);
        System.out.println("Обраний алгоритм: "
                + algorithm.getTitle());

        for (Access access : createReferenceString()) {
            memory.access(
                    access.processId,
                    access.pageNumber,
                    access.write
            );
        }

        memory.printStatistics();
    }

    private static Algorithm readAlgorithm() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Оберіть алгоритм заміщення сторінок:");
        System.out.println("1 — LRU");
        System.out.println("2 — FIFO");
        System.out.println("3 — Годинниковий алгоритм");
        System.out.println("4 — Модифікований годинниковий алгоритм");
        System.out.print("Ваш вибір: ");

        if (!scanner.hasNextInt()) {
            System.out.println("Некоректне значення. "
                    + "За замовчуванням обрано Modified Clock.");

            return Algorithm.MODIFIED_CLOCK;
        }

        int choice = scanner.nextInt();

        switch (choice) {
            case 1:
                return Algorithm.LRU;

            case 2:
                return Algorithm.FIFO;

            case 3:
                return Algorithm.CLOCK;

            case 4:
            default:
                return Algorithm.MODIFIED_CLOCK;
        }
    }

    /*
     * Рядок звернень до пам'яті.
     * Усі 5 процесів беруть участь у моделюванні.
     * Номери сторінок знаходяться у межах 0..9.
     */
    private static List<Access> createReferenceString() {
        return new ArrayList<Access>(Arrays.asList(
                new Access(1, 0, true),
                new Access(2, 1, false),
                new Access(3, 2, false),
                new Access(4, 3, true),
                new Access(5, 4, false),

                new Access(1, 0, false),
                new Access(2, 1, true),
                new Access(3, 2, false),

                new Access(4, 5, false),
                new Access(5, 4, true),
                new Access(1, 6, false),
                new Access(2, 7, true),
                new Access(3, 8, false),
                new Access(4, 3, false),
                new Access(5, 9, true),

                new Access(1, 0, true),
                new Access(2, 1, false),
                new Access(3, 2, true),
                new Access(4, 5, true),
                new Access(5, 4, false)
        ));
    }
}