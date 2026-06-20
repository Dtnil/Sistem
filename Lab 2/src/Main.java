import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class Lab3 {
    public static void main(String[] args) {
        System.out.printf("%-30s %-8s %-8s %-8s %-8s%n",
                "Дія", "B1", "B2", "B3", "Зайнято");

        SynchronizedBuffer buffer = new SynchronizedBuffer(3, 3);
        buffer.printState("Початковий стан");

        int partsPerProducer = 3; // max(3 виробники, 3 споживачі) = 3

        Producer p1 = new Producer(1, buffer, partsPerProducer, new int[]{1, 3});
        Producer p2 = new Producer(2, buffer, partsPerProducer, new int[]{2, 3});
        Producer p3 = new Producer(3, buffer, partsPerProducer, new int[]{1, 2, 3});

        Consumer c1 = new Consumer(1, buffer, 6); // I1 + I3
        Consumer c2 = new Consumer(2, buffer, 6); // I2 + I3
        Consumer c3 = new Consumer(3, buffer, 9); // I1 + I2 + I3

        p1.start();
        p2.start();
        p3.start();

        c1.start();
        c2.start();
        c3.start();

        try {
            p1.join();
            p2.join();
            p3.join();

            c1.join();
            c2.join();
            c3.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\nМоделювання завершено.");
    }
}

class BufferItem {
    int producerId;     // номер інформації
    int partNumber;     // номер частини
    boolean[] need;     // кому ще треба прочитати [1..N]

    public BufferItem(int producerId, int partNumber, int consumersCount, int[] targets) {
        this.producerId = producerId;
        this.partNumber = partNumber;
        this.need = new boolean[consumersCount + 1];
        for (int t : targets) {
            this.need[t] = true;
        }
    }

    public String shortName() {
        return "I" + producerId + ":" + partNumber;
    }

    public boolean neededBy(int consumerId) {
        return need[consumerId];
    }

    public void markRead(int consumerId) {
        need[consumerId] = false;
    }

    public boolean noMoreReaders() {
        for (int i = 1; i < need.length; i++) {
            if (need[i]) return false;
        }
        return true;
    }
}

class SynchronizedBuffer {
    private final BufferItem[] buffer;
    private final int capacity;
    private final int consumersCount;
    private int occupied = 0;
    private int writePos = 0;

    public SynchronizedBuffer(int capacity, int consumersCount) {
        this.capacity = capacity;
        this.consumersCount = consumersCount;
        this.buffer = new BufferItem[capacity];
    }

    public synchronized void put(BufferItem item, String producerName) {
        while (occupied == capacity) {
            printState(producerName + " чекає");
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        int pos = findEmptySlot();
        buffer[pos] = item;
        occupied++;
        writePos = (pos + 1) % capacity;

        printState(producerName + " записав " + item.shortName());
        notifyAll();
    }

    public synchronized BufferItem get(int consumerId, String consumerName) {
        while (!hasDataForConsumer(consumerId)) {
            printState(consumerName + " чекає");
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        int pos = findDataForConsumer(consumerId);
        BufferItem item = buffer[pos];
        item.markRead(consumerId);

        String action;
        if (item.noMoreReaders()) {
            action = consumerName + " прочитав " + item.shortName() + " (звільнив)";
            buffer[pos] = null;
            occupied--;
            notifyAll();
        } else {
            action = consumerName + " прочитав " + item.shortName();
        }

        printState(action);
        return item;
    }

    private boolean hasDataForConsumer(int consumerId) {
        for (BufferItem item : buffer) {
            if (item != null && item.neededBy(consumerId)) {
                return true;
            }
        }
        return false;
    }

    private int findDataForConsumer(int consumerId) {
        for (int i = 0; i < capacity; i++) {
            if (buffer[i] != null && buffer[i].neededBy(consumerId)) {
                return i;
            }
        }
        return -1;
    }

    private int findEmptySlot() {
        for (int i = 0; i < capacity; i++) {
            int pos = (writePos + i) % capacity;
            if (buffer[pos] == null) {
                return pos;
            }
        }
        return -1;
    }

    public synchronized void printState(String action) {
        System.out.printf("%-30s %-8s %-8s %-8s %-8d%n",
                action,
                cellText(0),
                cellText(1),
                cellText(2),
                occupied);
    }

    private String cellText(int index) {
        return buffer[index] == null ? "-" : buffer[index].shortName();
    }
}

class Producer extends Thread {
    private final int producerId;
    private final SynchronizedBuffer buffer;
    private final int partsCount;
    private final int[] targets;
    private final Random random = new Random();

    public Producer(int producerId, SynchronizedBuffer buffer, int partsCount, int[] targets) {
        this.producerId = producerId;
        this.buffer = buffer;
        this.partsCount = partsCount;
        this.targets = targets;
        setName("В" + producerId);
    }

    @Override
    public void run() {
        for (int part = 1; part <= partsCount; part++) {
            try {
                Thread.sleep(300 + random.nextInt(700));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            BufferItem item = new BufferItem(producerId, part, 3, targets);
            buffer.put(item, getName());
        }

        System.out.println(getName() + " завершив роботу");
    }
}

class Consumer extends Thread {
    private final int consumerId;
    private final SynchronizedBuffer buffer;
    private final int toRead;
    private final Random random = new Random();
    private final List<String> received = new ArrayList<>();

    public Consumer(int consumerId, SynchronizedBuffer buffer, int toRead) {
        this.consumerId = consumerId;
        this.buffer = buffer;
        this.toRead = toRead;
        setName("С" + consumerId);
    }

    @Override
    public void run() {
        for (int i = 0; i < toRead; i++) {
            try {
                Thread.sleep(400 + random.nextInt(800));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            BufferItem item = buffer.get(consumerId, getName());
            received.add(item.shortName());
        }

        System.out.println(getName() + " отримав: " + received);
    }
}