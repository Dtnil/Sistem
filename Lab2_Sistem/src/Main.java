import java.util.ArrayList;
import java.util.List;
class Lab3 {
    public static void main(String[] args) {
        System.err.println("Дія\t\t\t\tБуфер\tКількість зайнятих елементів");

        int numMessages = 3;

        SynchronizedBuffer sharedBuffer = new SynchronizedBuffer(3);
        sharedBuffer.displayState("Початковий стан\t\t\t");

        int consumer1Reads = 2 * numMessages;
        int consumer2Reads = 2 * numMessages;
        int consumer3Reads = 3 * numMessages;

        int totalWrites = 3 * numMessages;

        Producer producer1 = new Producer(sharedBuffer, numMessages, 1);
        producer1.setName("Виробник_1");

        Producer producer2 = new Producer(sharedBuffer, numMessages, 2);
        producer2.setName("Виробник_2");

        Producer producer3 = new Producer(sharedBuffer, numMessages, 3);
        producer3.setName("Виробник_3");

        int[] consumer1Producers = {1, 3};
        int[] consumer2Producers = {2, 3};
        int[] consumer3Producers = {1, 2, 3};

        Consumer consumer1 = new Consumer(sharedBuffer, consumer1Reads, consumer1Producers);
        consumer1.setName("Споживач_1");

        Consumer consumer2 = new Consumer(sharedBuffer, consumer2Reads, consumer2Producers);
        consumer2.setName("Споживач_2");

        Consumer consumer3 = new Consumer(sharedBuffer, consumer3Reads, consumer3Producers);
        consumer3.setName("Споживач_3");

        // Запуск потоків
        producer1.start();
        producer2.start();
        producer3.start();
        consumer1.start();
        consumer2.start();
        consumer3.start();
    }
}

class BufferItem {
    int producerId;   // ідентифікатор виробника
    int partNumber;   // номер частки інформації
    int data;         // власне дані (частка інформації)
    int readCount;    // скільки разів вже прочитано
    int maxReads;     // скільки разів треба прочитати (кількість споживачів цього виробника)

    public BufferItem(int producerId, int partNumber, int data, int maxReads) {
        this.producerId = producerId;
        this.partNumber = partNumber;
        this.data = data;
        this.readCount = 0;
        this.maxReads = maxReads;
    }

    public boolean isFullyRead() {
        return readCount >= maxReads;
    }

    @Override
    public String toString() {
        return "[П" + producerId + "|ч" + partNumber + "|д" + data + "]";
    }
}

class SynchronizedBuffer {
    private int maxSize;
    private BufferItem[] buffer;
    private int occupiedCount;  // кількість зайнятих елементів
    private int writePos;       // позиція для запису
    private int readPos;        // позиція для читання

    private static final int[] CONSUMERS_PER_PRODUCER = {0, 2, 2, 3}; // індекс 0 не використовується

    public SynchronizedBuffer(int size) {
        this.maxSize = size;
        this.buffer = new BufferItem[maxSize];
        this.occupiedCount = 0;
        this.writePos = 0;
        this.readPos = 0;
    }

    public synchronized void set(int producerId, int partNumber, int data) {
        String name = Thread.currentThread().getName();

        // Чекати, якщо буфер повний
        while (occupiedCount == maxSize) {
            try {
                System.out.println(name + " робить спробу писати.");
                displayState("Буфер повний. " + name + " чекає.\t");
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        int maxReads = CONSUMERS_PER_PRODUCER[producerId];
        buffer[writePos] = new BufferItem(producerId, partNumber, data, maxReads);
        occupiedCount++;

        displayState(name + " пише " + buffer[writePos] + "\t\t");

        writePos = (writePos + 1) % maxSize;

        if (occupiedCount == 1) {
            notifyAll();
        }
    }

    public synchronized BufferItem get(int[] allowedProducerIds) {
        String name = Thread.currentThread().getName();

        // Чекати поки не з'явиться відповідний елемент
        while (!hasItemFor(allowedProducerIds)) {
            try {
                System.out.println(name + " робить спробу читати.");
                displayState("Немає даних для " + name + ". Чекає.\t");
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        BufferItem item = null;
        int idx = readPos;
        for (int i = 0; i < maxSize; i++) {
            int pos = (readPos + i) % maxSize;
            if (buffer[pos] != null && !buffer[pos].isFullyRead() && isAllowed(buffer[pos].producerId, allowedProducerIds)) {
                item = buffer[pos];
                item.readCount++;
                displayState(name + " читає " + item + "\t\t");

                // Якщо всі споживачі прочитали — звільнити елемент
                if (item.isFullyRead()) {
                    buffer[pos] = null;
                    occupiedCount--;
                    // Просуваємо readPos якщо цей елемент був на позиції readPos
                    while (readPos < maxSize && buffer[readPos] == null) {
                        readPos = (readPos + 1) % maxSize;
                        // запобігаємо нескінченному циклу
                        if (readPos == writePos) break;
                    }
                }
                break;
            }
        }

        notifyAll();
        return item;
    }

    private boolean hasItemFor(int[] allowedProducerIds) {
        for (int i = 0; i < maxSize; i++) {
            if (buffer[i] != null && !buffer[i].isFullyRead() && isAllowed(buffer[i].producerId, allowedProducerIds)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowed(int producerId, int[] allowedProducerIds) {
        for (int id : allowedProducerIds) {
            if (id == producerId) return true;
        }
        return false;
    }

    public synchronized void displayState(String operation) {
        StringBuilder sb = new StringBuilder(operation);
        sb.append("| Буфер: [");
        for (int i = 0; i < maxSize; i++) {
            sb.append(buffer[i] != null ? buffer[i].toString() : "___");
            if (i < maxSize - 1) sb.append(", ");
        }
        sb.append("] | Зайнято: ").append(occupiedCount);
        System.out.println(sb);
    }
}


class Producer extends Thread {
    private SynchronizedBuffer sharedBuffer;
    private int numMessages;
    private int producerId;

    public Producer(SynchronizedBuffer buffer, int numMessages, int producerId) {
        this.sharedBuffer = buffer;
        this.numMessages = numMessages;
        this.producerId = producerId;
    }

    @Override
    public void run() {
        for (int part = 1; part <= numMessages; part++) {
            try {
                Thread.sleep((int)(Math.random() * 2001));
                int data = producerId * 10 + part;
                sharedBuffer.set(producerId, part, data);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.err.println(getName() + " закінчив виробництво. Повідомлень вироблено: "
                + numMessages + "\nЗавершення " + getName() + ".\n");
    }
}

class Consumer extends Thread {
    private SynchronizedBuffer sharedBuffer;
    private int numToRead;
    private int[] allowedProducers;

    public Consumer(SynchronizedBuffer buffer, int numToRead, int[] allowedProducers) {
        this.sharedBuffer = buffer;
        this.numToRead = numToRead;
        this.allowedProducers = allowedProducers;
    }

    @Override
    public void run() {
        List<BufferItem> received = new ArrayList<>();
        for (int i = 0; i < numToRead; i++) {
            try {
                Thread.sleep((int)(Math.random() * 2001));
                BufferItem item = sharedBuffer.get(allowedProducers);
                if (item != null) {
                    received.add(item);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(": прочитано ").append(received.size()).append(" елементів.\n");
        sb.append("Отримані дані: ");
        for (BufferItem item : received) {
            sb.append(item).append(" ");
        }
        sb.append("\nЗавершення ").append(getName()).append(".\n");
        System.err.println(sb);
    }
}