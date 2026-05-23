// Лабораторна робота №2. Варіант 13.
// 3 процеси, 3 ресурси
// Процес 1: ресурси 1, 3
// Процес 2: ресурси 2, 3
// Процес 3: ресурси 1, 2, 3

// ==================== СПІЛЬНИЙ СТАН ТА ВИВІД ====================
class SharedState {
    static int r1 = 0;
    static int r2 = 0;
    static int r3 = 0;

    static synchronized void display(String operation) {
        StringBuilder sb = new StringBuilder(operation);
        while (sb.length() < 45) sb.append(' ');
        System.out.println(sb + "" + r1 + "          " + r2 + "          " + r3);
    }
}

// ==================== РЕСУРС 1 ====================
class Resource1 {
    synchronized void use(int procId) {
        try {
            while (SharedState.r1 == 1) {
                SharedState.display("[Процес " + procId + "] очікує  Ресурс 1");
                wait();
            }
            SharedState.r1 = 1;
            SharedState.display("[Процес " + procId + "] захопив Ресурс 1");
            Thread.sleep(150);
            SharedState.r1 = 0;
            SharedState.display("[Процес " + procId + "] звільнив Ресурс 1");
            notifyAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// ==================== РЕСУРС 2 ====================
class Resource2 {
    synchronized void use(int procId) {
        try {
            while (SharedState.r2 == 1) {
                SharedState.display("[Процес " + procId + "] очікує  Ресурс 2");
                wait();
            }
            SharedState.r2 = 1;
            SharedState.display("[Процес " + procId + "] захопив Ресурс 2");
            Thread.sleep(150);
            SharedState.r2 = 0;
            SharedState.display("[Процес " + procId + "] звільнив Ресурс 2");
            notifyAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// ==================== РЕСУРС 3 ====================
class Resource3 {
    synchronized void use(int procId) {
        try {
            while (SharedState.r3 == 1) {
                SharedState.display("[Процес " + procId + "] очікує  Ресурс 3");
                wait();
            }
            SharedState.r3 = 1;
            SharedState.display("[Процес " + procId + "] захопив Ресурс 3");
            Thread.sleep(150);
            SharedState.r3 = 0;
            SharedState.display("[Процес " + procId + "] звільнив Ресурс 3");
            notifyAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// ==================== ПРОЦЕС 1: ресурси 1 -> 3 ====================
class Proc1 implements Runnable {
    private final Resource1 q1;
    private final Resource3 q3;

    Proc1(Resource1 q1, Resource3 q3) {
        this.q1 = q1;
        this.q3 = q3;
        new Thread(this, "Процес-1").start();
    }

    public void run() {
        try {
            for (int i = 1; i <= 5; i++) {
                q1.use(1);
                q3.use(1);
                Thread.sleep(100);
            }
            System.out.println("Процес 1 завершив роботу.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// ==================== ПРОЦЕС 2: ресурси 2 -> 3 ====================
class Proc2 implements Runnable {
    private final Resource2 q2;
    private final Resource3 q3;

    Proc2(Resource2 q2, Resource3 q3) {
        this.q2 = q2;
        this.q3 = q3;
        new Thread(this, "Процес-2").start();
    }

    public void run() {
        try {
            for (int i = 1; i <= 5; i++) {
                q2.use(2);
                q3.use(2);
                Thread.sleep(100);
            }
            System.out.println("Процес 2 завершив роботу.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// ==================== ПРОЦЕС 3: ресурси 1 -> 2 -> 3 ====================
class Proc3 implements Runnable {
    private final Resource1 q1;
    private final Resource2 q2;
    private final Resource3 q3;

    Proc3(Resource1 q1, Resource2 q2, Resource3 q3) {
        this.q1 = q1;
        this.q2 = q2;
        this.q3 = q3;
        new Thread(this, "Процес-3").start();
    }

    public void run() {
        try {
            for (int i = 1; i <= 5; i++) {
                q1.use(3);
                q2.use(3);
                q3.use(3);
                Thread.sleep(100);
            }
            System.out.println("Процес 3 завершив роботу.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// ==================== ГОЛОВНИЙ КЛАС ====================
class comm13 {
    public static void main(String[] args) {
        System.out.println("Лабораторна робота №1. Варіант 13");
        System.out.println("Процес 1: рес. 1->3 | Процес 2: рес. 2->3 | Процес 3: рес. 1->2->3");
        System.out.println();

        StringBuilder header = new StringBuilder("Операція");
        while (header.length() < 45) header.append(' ');
        System.out.println(header + "Рес.1     Рес.2     Рес.3");
        System.out.println("-".repeat(75));

        SharedState.display("Початковий стан");

        Resource1 res1 = new Resource1();
        Resource2 res2 = new Resource2();
        Resource3 res3 = new Resource3();

        new Proc1(res1, res3);
        new Proc2(res2, res3);
        new Proc3(res1, res2, res3);
    }
}