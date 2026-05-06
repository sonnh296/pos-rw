package turbo.pos.boost.controller;

public class MyThread {

    public static class TestThread implements Runnable {
        public void run() {
            String threadName = Thread.currentThread().getName();
            System.out.println("Hello from MyThread!" + " This is thread: " + threadName);
        }
    }
}
