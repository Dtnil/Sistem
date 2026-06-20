import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Лабораторна робота №5.
 * Моделювання та усунення взаємного блокування і голодування.
 *
 * Програма підтримує всі пункти індивідуальних завдань:
 * 1 - виявлення взаємного блокування;
 * 2 - заборона запуску нового процесу;
 * 3-5 - алгоритм банкіра для заданої кількості процесів і ресурсів;
 * 6 - користувацькі параметри;
 * 7 - усунення голодування через справедливе блокування ReentrantLock(true).
 *
 * Файл повинен називатися Lab5.java.
 */
class Lab5 {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        while (true) {
            printMenu();
            int choice = readInt("Оберіть пункт: ");

            switch (choice) {
                case 1 -> task1DeadlockDetection();
                case 2 -> task2StartPrevention();
                case 3 -> bankerSimulation(3, new int[]{10}, "Завдання 3");
                case 4 -> bankerSimulation(3, new int[]{10, 8, 9}, "Завдання 4");
                case 5 -> bankerSimulation(4, new int[]{8, 10, 9}, "Завдання 5");
                case 6 -> task6CustomBanker();
                case 7 -> starvationPreventionDemo();
                case 0 -> {
                    System.out.println("Програму завершено.");
                    return;
                }
                default -> System.out.println("Невірний пункт меню.");
            }

            System.out.println("\nНатисніть Enter, щоб повернутися до меню...");
            SCANNER.nextLine();
        }
    }

    private static void printMenu() {
        System.out.println("\n======================================================");
        System.out.println("ЛР №5. ВЗАЄМНЕ БЛОКУВАННЯ ТА ГОЛОДУВАННЯ");
        System.out.println("1 - Виявлення взаємного блокування (4 процеси, 3 ресурси)");
        System.out.println("2 - Заборона запуску нового процесу");
        System.out.println("3 - Алгоритм банкіра: P=3, E1=10");
        System.out.println("4 - Алгоритм банкіра: P=3, E=(10, 8, 9)");
        System.out.println("5 - Алгоритм банкіра: P=4, E=(8, 10, 9)");
        System.out.println("6 - Алгоритм банкіра з власними параметрами");
        System.out.println("7 - Демонстрація усунення голодування");
        System.out.println("0 - Вихід");
        System.out.println("======================================================");
    }

    /*
     * Завдання 1.
     * Генерується випадкова система з 4 процесів і 3 типів ресурсів,
     * після чого виконується алгоритм виявлення тупика.
     */
    private static void task1DeadlockDetection() {
        System.out.println("\n--- Завдання 1: виявлення взаємного блокування ---");
        int[] total = readResourceVector(3);
        SystemState state = SystemState.randomState(4, total, RANDOM);

        printState(state);
        analyzeState(state, true);
    }

    /*
     * Завдання 2.
     * Перевіряється умова з методичних матеріалів:
     * E[j] >= MaxNew[j] + sum(Max[i][j]).
     */
    private static void task2StartPrevention() {
        System.out.println("\n--- Завдання 2: заборона запуску процесу ---");
        int[] total = readResourceVector(3);
        SystemState current = SystemState.lightState(4, total, RANDOM);

        System.out.println("\nПоточні максимальні вимоги процесів:");
        printMatrix("Max", current.max);
        System.out.println("Загальна кількість ресурсів E = " + vectorToString(current.total));

        int[] newProcessMax = new int[total.length];
        System.out.println("\nВведіть максимальні вимоги нового процесу P4:");
        for (int j = 0; j < total.length; j++) {
            newProcessMax[j] = readNonNegativeInt("Максимум для R" + (j + 1) + ": ");
        }

        int[] sumCurrentMax = new int[total.length];
        for (int j = 0; j < total.length; j++) {
            for (int i = 0; i < current.processCount; i++) {
                sumCurrentMax[j] += current.max[i][j];
            }
        }

        boolean canStart = true;
        System.out.println("\nПеревірка умови запуску:");
        for (int j = 0; j < total.length; j++) {
            int required = sumCurrentMax[j] + newProcessMax[j];
            boolean resourceEnough = total[j] >= required;
            canStart &= resourceEnough;

            System.out.printf(
                    "R%d: E%d=%d; сума поточних вимог + вимога P4 = %d -> %s%n",
                    j + 1, j + 1, total[j], required,
                    resourceEnough ? "достатньо" : "недостатньо"
            );
        }

        if (canStart) {
            System.out.println("\nP4 МОЖНА запустити: максимальні вимоги всіх процесів "
                    + "можуть бути гарантовано задоволені.");
        } else {
            System.out.println("\nP4 НЕ МОЖНА запускати: його запуск потенційно може "
                    + "призвести до взаємного блокування.");
        }
    }

    /*
     * Завдання 3, 4, 5.
     * Стан генерується випадково. За бажанням користувача можна
     * отримати гарантовано безпечний або небезпечний стан.
     */
    private static void bankerSimulation(int processes, int[] total, String taskName) {
        System.out.println("\n--- " + taskName + ": алгоритм банкіра ---");
        System.out.println("1 - Випадковий стан");
        System.out.println("2 - Гарантовано безпечний стан");
        System.out.println("3 - Гарантовано небезпечний стан");
        int mode = readInt("Оберіть тип генерації: ");

        SystemState state;
        if (mode == 2) {
            state = SystemState.guaranteedSafeState(processes, total);
        } else if (mode == 3) {
            state = SystemState.guaranteedUnsafeState(processes, total);
        } else {
            state = SystemState.randomState(processes, total, RANDOM);
        }

        printState(state);
        SafetyResult result = analyzeState(state, true);

        if (!result.safe) {
            recoverFromDeadlock(state, result);
        } else {
            demonstrateBankerRequest(state);
        }
    }

    /*
     * Завдання 6: користувач задає кількість процесів, кількість ресурсів
     * та число екземплярів кожного ресурсу.
     */
    private static void task6CustomBanker() {
        System.out.println("\n--- Завдання 6: власні параметри алгоритму банкіра ---");
        int processCount;
        do {
            processCount = readInt("Кількість процесів (2..4): ");
        } while (processCount < 2 || processCount > 4);

        int resourceCount;
        do {
            resourceCount = readInt("Кількість типів ресурсів (1..3): ");
        } while (resourceCount < 1 || resourceCount > 3);

        int[] total = readResourceVector(resourceCount);
        bankerSimulation(processCount, total, "Завдання 6");
    }

    private static int[] readResourceVector(int resourceCount) {
        int[] total = new int[resourceCount];
        System.out.println("Введіть кількість екземплярів кожного ресурсу:");
        for (int j = 0; j < resourceCount; j++) {
            do {
                total[j] = readInt("E" + (j + 1) + ": ");
                if (total[j] <= 0) {
                    System.out.println("Кількість ресурсу повинна бути більшою за 0.");
                }
            } while (total[j] <= 0);
        }
        return total;
    }

    /*
     * Виявлення тупика / перевірка безпечності.
     *
     * Work = Available.
     * Знаходимо незавершений процес з Need[i] <= Work.
     * Після його завершення Work = Work + Allocation[i].
     */
    private static SafetyResult analyzeState(SystemState state, boolean printSteps) {
        int[] work = state.available.clone();
        boolean[] finish = new boolean[state.processCount];

        for (int i = 0; i < state.processCount; i++) {
            finish[i] = !state.active[i];
        }

        List<Integer> sequence = new ArrayList<>();
        boolean found;

        if (printSteps) {
            System.out.println("\nАлгоритм перевірки стану:");
            System.out.println("Початковий Work = Available = " + vectorToString(work));
        }

        do {
            found = false;

            for (int i = 0; i < state.processCount; i++) {
                if (state.active[i] && !finish[i] && lessOrEqual(state.need[i], work)) {
                    if (printSteps) {
                        System.out.printf(
                                "P%d: Need=%s <= Work=%s. Процес може завершитись.%n",
                                i, vectorToString(state.need[i]), vectorToString(work)
                        );
                    }

                    for (int j = 0; j < state.resourceCount; j++) {
                        work[j] += state.allocation[i][j];
                    }

                    finish[i] = true;
                    sequence.add(i);
                    found = true;

                    if (printSteps) {
                        System.out.println("P" + i + " повертає Allocation="
                                + vectorToString(state.allocation[i])
                                + "; новий Work=" + vectorToString(work));
                    }
                }
            }
        } while (found);

        List<Integer> blocked = new ArrayList<>();
        for (int i = 0; i < state.processCount; i++) {
            if (state.active[i] && !finish[i]) {
                blocked.add(i);
            }
        }

        if (blocked.isEmpty()) {
            System.out.println("\nСТАН БЕЗПЕЧНИЙ.");
            System.out.println("Безпечна послідовність: " + formatProcessSequence(sequence));
            return new SafetyResult(true, sequence, blocked);
        }

        System.out.println("\nСТАН НЕБЕЗПЕЧНИЙ: є ризик взаємного блокування.");
        System.out.println("Процеси, які не можуть завершитися: " + formatProcessSequence(blocked));
        System.out.println("\nМожлива послідовність, що приводить до тупика:");
        System.out.println("Усі незавершені процеси одночасно запитують свої залишкові ресурси.");

        for (int process : blocked) {
            System.out.println("P" + process + " утримує "
                    + vectorToString(state.allocation[process])
                    + " і очікує " + vectorToString(state.need[process]));
        }

        System.out.println("Доступно лише: " + vectorToString(work));
        System.out.println("Для жодного з цих процесів Need <= Work не виконується, "
                + "тому за умови утримання ресурсів виникає взаємне блокування.");

        return new SafetyResult(false, sequence, blocked);
    }

    /*
     * Відновлення після тупика:
     * модель примусово завершує один процес-жертву, повертає його ресурси
     * та повторно запускає алгоритм безпеки.
     */
    private static void recoverFromDeadlock(SystemState state, SafetyResult firstResult) {
        System.out.println("\n--- ВІДНОВЛЕННЯ ПІСЛЯ ВЗАЄМНОГО БЛОКУВАННЯ ---");
        SafetyResult result = firstResult;

        while (!result.safe) {
            int victim = chooseVictim(state, result.blockedProcesses);

            System.out.println("\nДля розриву циклу обрано процес-жертву P" + victim + ".");
            System.out.println("Його примусово завершено, ресурси "
                    + vectorToString(state.allocation[victim]) + " повертаються системі.");

            state.terminate(victim);
            System.out.println("Новий Available = " + vectorToString(state.available));

            result = analyzeState(state, true);
        }

        System.out.println("\nВідновлення завершено: після звільнення ресурсів "
                + "решта процесів має безпечну послідовність виконання.");
    }

    private static int chooseVictim(SystemState state, List<Integer> blockedProcesses) {
        int victim = blockedProcesses.get(0);
        int maxReleased = -1;

        for (int process : blockedProcesses) {
            int released = 0;
            for (int value : state.allocation[process]) {
                released += value;
            }

            if (released > maxReleased) {
                maxReleased = released;
                victim = process;
            }
        }
        return victim;
    }

    /*
     * Демонстрація основної частини алгоритму банкіра:
     * запит задовольняється лише тоді, коли після пробного виділення
     * система залишається у безпечному стані.
     */
    private static void demonstrateBankerRequest(SystemState state) {
        int process = firstActiveProcessWithNeed(state);
        if (process == -1) {
            return;
        }

        int[] request = new int[state.resourceCount];
        for (int j = 0; j < state.resourceCount; j++) {
            request[j] = Math.min(state.need[process][j], state.available[j]);
        }

        boolean anyRequested = false;
        for (int value : request) {
            if (value > 0) {
                anyRequested = true;
                break;
            }
        }

        if (!anyRequested) {
            System.out.println("\nДемонстрація нового запиту пропущена: немає доступного "
                    + "ресурсу, який можна безпечно виділити.");
            return;
        }

        System.out.println("\n--- ПЕРЕВІРКА НОВОГО ЗАПИТУ ЗА АЛГОРИТМОМ БАНКІРА ---");
        System.out.println("P" + process + " подає запит Request=" + vectorToString(request));

        if (!lessOrEqual(request, state.need[process])) {
            System.out.println("Помилка: запит перевищує максимальну потребу процесу.");
            return;
        }

        if (!lessOrEqual(request, state.available)) {
            System.out.println("Запит відкладається: недостатньо доступних ресурсів.");
            return;
        }

        SystemState test = state.copy();
        test.allocate(process, request);
        SafetyResult testResult = analyzeStateQuiet(test);

        if (testResult.safe) {
            state.allocate(process, request);
            System.out.println("Запит СХВАЛЕНО: після пробного виділення стан безпечний.");
            System.out.println("Новий Available = " + vectorToString(state.available));
        } else {
            System.out.println("Запит ВІДХИЛЕНО: пробне виділення робить стан небезпечним.");
        }
    }

    private static int firstActiveProcessWithNeed(SystemState state) {
        for (int i = 0; i < state.processCount; i++) {
            if (!state.active[i]) {
                continue;
            }

            for (int j = 0; j < state.resourceCount; j++) {
                if (state.need[i][j] > 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static SafetyResult analyzeStateQuiet(SystemState state) {
        int[] work = state.available.clone();
        boolean[] finish = new boolean[state.processCount];
        List<Integer> sequence = new ArrayList<>();

        for (int i = 0; i < state.processCount; i++) {
            finish[i] = !state.active[i];
        }

        boolean found;
        do {
            found = false;

            for (int i = 0; i < state.processCount; i++) {
                if (state.active[i] && !finish[i] && lessOrEqual(state.need[i], work)) {
                    for (int j = 0; j < state.resourceCount; j++) {
                        work[j] += state.allocation[i][j];
                    }

                    finish[i] = true;
                    sequence.add(i);
                    found = true;
                }
            }
        } while (found);

        List<Integer> blocked = new ArrayList<>();
        for (int i = 0; i < state.processCount; i++) {
            if (state.active[i] && !finish[i]) {
                blocked.add(i);
            }
        }

        return new SafetyResult(blocked.isEmpty(), sequence, blocked);
    }

    /*
     * Додаткова частина для теми "голодування".
     * Нечесне планування може довго не давати P2 доступ до ресурсу.
     * Справедливий ReentrantLock(true) утворює чергу очікування,
     * тому кожен потік, який чекає, отримає ресурс після попередніх.
     */
    private static void starvationPreventionDemo() {
        System.out.println("\n--- ДЕМОНСТРАЦІЯ ГОЛОДУВАННЯ ТА ЙОГО УСУНЕННЯ ---");
        System.out.println("Модель голодування без справедливої черги:");
        for (int tick = 1; tick <= 6; tick++) {
            String selected = (tick % 2 == 0) ? "P0" : "P1";
            System.out.println("Крок " + tick + ": ресурс отримує " + selected
                    + ", P2 продовжує чекати.");
        }

        System.out.println("\nУсунення: застосовуємо ReentrantLock(true), "
                + "який обслуговує потоки у справедливій черзі.");

        ReentrantLock fairLock = new ReentrantLock(true);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            Thread thread = new Thread(new FairWorker("P" + i, fairLock));
            threads.add(thread);
            thread.start();

            // Невелика пауза робить порядок постановки в чергу наочним.
            sleep(20);
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        System.out.println("Усі процеси одержали ресурс і завершили роботу: "
                + "голодування усунено.");
    }

    private static class FairWorker implements Runnable {
        private final String name;
        private final ReentrantLock lock;

        FairWorker(String name, ReentrantLock lock) {
            this.name = name;
            this.lock = lock;
        }

        @Override
        public void run() {
            for (int step = 1; step <= 2; step++) {
                System.out.println(name + " очікує ресурс.");
                lock.lock();

                try {
                    System.out.println(name + " отримав ресурс (ітерація " + step + ").");
                    sleep(70);
                    System.out.println(name + " звільняє ресурс.");
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private static void printState(SystemState state) {
        System.out.println("\n================ СТАН СИСТЕМИ ================");
        System.out.println("Кількість процесів: " + state.processCount);
        System.out.println("Кількість типів ресурсів: " + state.resourceCount);
        System.out.println("Існуючі ресурси E = " + vectorToString(state.total));
        System.out.println("Доступні ресурси A = " + vectorToString(state.available));
        printMatrix("Max (максимальні вимоги)", state.max);
        printMatrix("Allocation C (поточний розподіл)", state.allocation);
        printMatrix("Need R = Max - Allocation (залишкові запити)", state.need);
        System.out.println("================================================");
    }

    private static void printMatrix(String title, int[][] matrix) {
        System.out.println("\n" + title + ":");
        System.out.print("        ");
        for (int j = 0; j < matrix[0].length; j++) {
            System.out.printf("R%-4d", j + 1);
        }
        System.out.println();

        for (int i = 0; i < matrix.length; i++) {
            System.out.printf("P%-5d", i);
            for (int j = 0; j < matrix[i].length; j++) {
                System.out.printf("%-6d", matrix[i][j]);
            }
            System.out.println();
        }
    }

    private static boolean lessOrEqual(int[] left, int[] right) {
        for (int i = 0; i < left.length; i++) {
            if (left[i] > right[i]) {
                return false;
            }
        }
        return true;
    }

    private static String vectorToString(int[] vector) {
        return Arrays.toString(vector);
    }

    private static String formatProcessSequence(List<Integer> sequence) {
        if (sequence.isEmpty()) {
            return "немає";
        }

        StringBuilder result = new StringBuilder("<");
        for (int i = 0; i < sequence.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append("P").append(sequence.get(i));
        }
        result.append(">");
        return result.toString();
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = SCANNER.nextLine().trim();

            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Введіть ціле число.");
            }
        }
    }

    private static int readNonNegativeInt(String prompt) {
        int value;
        do {
            value = readInt(prompt);
            if (value < 0) {
                System.out.println("Число не може бути від'ємним.");
            }
        } while (value < 0);
        return value;
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class SafetyResult {
        final boolean safe;
        final List<Integer> sequence;
        final List<Integer> blockedProcesses;

        SafetyResult(boolean safe, List<Integer> sequence, List<Integer> blockedProcesses) {
            this.safe = safe;
            this.sequence = sequence;
            this.blockedProcesses = blockedProcesses;
        }
    }

    /*
     * Дані моделі системи.
     *
     * total      - E, загальна кількість ресурсів;
     * available  - A, доступні ресурси;
     * max        - максимальні вимоги процесів;
     * allocation - C, уже виділені ресурси;
     * need       - R, залишкові потреби (Max - Allocation).
     */
    private static class SystemState {
        final int processCount;
        final int resourceCount;
        final int[] total;
        final int[] available;
        final int[][] max;
        final int[][] allocation;
        final int[][] need;
        final boolean[] active;

        SystemState(int processCount, int[] total) {
            this.processCount = processCount;
            this.resourceCount = total.length;
            this.total = total.clone();
            this.available = new int[resourceCount];
            this.max = new int[processCount][resourceCount];
            this.allocation = new int[processCount][resourceCount];
            this.need = new int[processCount][resourceCount];
            this.active = new boolean[processCount];
            Arrays.fill(this.active, true);
        }

        static SystemState randomState(int processCount, int[] total, Random random) {
            SystemState state = new SystemState(processCount, total);

            for (int i = 0; i < processCount; i++) {
                for (int j = 0; j < state.resourceCount; j++) {
                    state.max[i][j] = 1 + random.nextInt(total[j]);
                }
            }

            for (int j = 0; j < state.resourceCount; j++) {
                int remaining = total[j];

                for (int i = 0; i < processCount; i++) {
                    int limit = Math.min(state.max[i][j], remaining);
                    state.allocation[i][j] = random.nextInt(limit + 1);
                    remaining -= state.allocation[i][j];
                }
            }

            state.recalculate();
            return state;
        }

        /*
         * Гарантовано безпечний стан:
         * у кожного процесу є невеликі потреби, а доступних ресурсів
         * досить для завершення хоча б одного процесу.
         */
        static SystemState guaranteedSafeState(int processCount, int[] total) {
            SystemState state = new SystemState(processCount, total);

            for (int j = 0; j < state.resourceCount; j++) {
                int remaining = total[j];

                for (int i = 0; i < processCount; i++) {
                    if (remaining > 1) {
                        state.allocation[i][j] = 1;
                        remaining--;
                    }

                    state.max[i][j] = state.allocation[i][j] + 1;
                    if (state.max[i][j] > total[j]) {
                        state.max[i][j] = total[j];
                    }
                }
            }

            state.recalculate();
            return state;
        }

        /*
         * Гарантовано небезпечний стан:
         * усі ресурси розподілені між процесами, Available = 0,
         * але кожному процесу потрібна ще принаймні одна одиниця ресурсу.
         */
        static SystemState guaranteedUnsafeState(int processCount, int[] total) {
            SystemState state = new SystemState(processCount, total);

            for (int j = 0; j < state.resourceCount; j++) {
                int remaining = total[j];

                // Кожен процес одержує хоча б одну одиницю, якщо це можливо.
                for (int i = 0; i < processCount; i++) {
                    if (remaining > 0) {
                        state.allocation[i][j]++;
                        remaining--;
                    }
                }

                // Решта ресурсу розподіляється по процесах.
                int index = 0;
                while (remaining > 0) {
                    state.allocation[index % processCount][j]++;
                    remaining--;
                    index++;
                }

                for (int i = 0; i < processCount; i++) {
                    // Need = 1 для кожного процесу, якщо це не перевищує E.
                    state.max[i][j] = Math.min(total[j], state.allocation[i][j] + 1);

                    // Якщо процес вже має всі ресурси цього типу, переносимо
                    // одну одиницю до іншого процесу, коли це можливо.
                    if (state.max[i][j] == state.allocation[i][j]
                            && total[j] > 1
                            && state.allocation[i][j] > 0) {
                        int receiver = (i + 1) % processCount;
                        if (state.allocation[receiver][j] > 0) {
                            state.allocation[i][j]--;
                            state.allocation[receiver][j]++;
                            state.max[i][j] = state.allocation[i][j] + 1;
                            state.max[receiver][j] = Math.min(
                                    total[j], state.allocation[receiver][j] + 1
                            );
                        }
                    }
                }
            }

            state.recalculate();

            /*
             * Для нетипових введених значень (наприклад, E=1 і багато процесів)
             * стан може бути не строго небезпечним. У такому разі випадково
             * шукаємо небезпечний коректний стан.
             */
            if (analyzeStateQuietForGeneration(state).safe) {
                for (int attempt = 0; attempt < 10000; attempt++) {
                    SystemState candidate = randomState(processCount, total, RANDOM);
                    if (!analyzeStateQuietForGeneration(candidate).safe) {
                        return candidate;
                    }
                }
            }

            return state;
        }

        static SystemState lightState(int processCount, int[] total, Random random) {
            SystemState state = new SystemState(processCount, total);

            for (int i = 0; i < processCount; i++) {
                for (int j = 0; j < state.resourceCount; j++) {
                    int upperBound = Math.max(1, total[j] / (processCount + 1));
                    state.max[i][j] = random.nextInt(upperBound + 1);
                }
            }

            // Розподіляємо ресурси по стовпцях, не перевищуючи E[j].
            for (int j = 0; j < state.resourceCount; j++) {
                int remaining = total[j];

                for (int i = 0; i < processCount; i++) {
                    int limit = Math.min(state.max[i][j], remaining);
                    state.allocation[i][j] = random.nextInt(limit + 1);
                    remaining -= state.allocation[i][j];
                }
            }

            state.recalculate();
            return state;
        }

        private static SafetyResult analyzeStateQuietForGeneration(SystemState state) {
            int[] work = state.available.clone();
            boolean[] finish = new boolean[state.processCount];
            List<Integer> sequence = new ArrayList<>();

            boolean found;
            do {
                found = false;
                for (int i = 0; i < state.processCount; i++) {
                    if (!finish[i] && lessOrEqual(state.need[i], work)) {
                        for (int j = 0; j < state.resourceCount; j++) {
                            work[j] += state.allocation[i][j];
                        }
                        finish[i] = true;
                        sequence.add(i);
                        found = true;
                    }
                }
            } while (found);

            List<Integer> blocked = new ArrayList<>();
            for (int i = 0; i < state.processCount; i++) {
                if (!finish[i]) {
                    blocked.add(i);
                }
            }
            return new SafetyResult(blocked.isEmpty(), sequence, blocked);
        }

        void recalculate() {
            for (int j = 0; j < resourceCount; j++) {
                int allocated = 0;

                for (int i = 0; i < processCount; i++) {
                    if (allocation[i][j] > max[i][j]) {
                        throw new IllegalStateException("Allocation не може перевищувати Max.");
                    }

                    need[i][j] = max[i][j] - allocation[i][j];
                    allocated += allocation[i][j];
                }

                if (allocated > total[j]) {
                    throw new IllegalStateException("Розподілено більше ресурсів, ніж існує.");
                }

                available[j] = total[j] - allocated;
            }
        }

        void allocate(int process, int[] request) {
            for (int j = 0; j < resourceCount; j++) {
                available[j] -= request[j];
                allocation[process][j] += request[j];
                need[process][j] -= request[j];
            }
        }

        void terminate(int process) {
            for (int j = 0; j < resourceCount; j++) {
                available[j] += allocation[process][j];
                allocation[process][j] = 0;
                need[process][j] = 0;
                max[process][j] = 0;
            }
            active[process] = false;
        }

        SystemState copy() {
            SystemState clone = new SystemState(processCount, total);

            for (int i = 0; i < processCount; i++) {
                clone.active[i] = active[i];

                for (int j = 0; j < resourceCount; j++) {
                    clone.max[i][j] = max[i][j];
                    clone.allocation[i][j] = allocation[i][j];
                    clone.need[i][j] = need[i][j];
                }
            }

            System.arraycopy(available, 0, clone.available, 0, resourceCount);
            return clone;
        }
    }
}
