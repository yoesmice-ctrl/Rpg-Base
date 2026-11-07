package com.rpg.concurrency;

public class PruebaDaemon {

    public static void main(String[] args) throws InterruptedException {

        // PARTE 1: la JVM cierra aunque el hilo daemon siga vivo
        Thread daemon = new Thread(() -> {
            while (true) {
                System.out.println("[daemon] sigo trabajando...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "hilo-daemon");
        daemon.setDaemon(true); // siempre antes de start()
        daemon.start();

        Thread.sleep(2000);
        System.out.println("main termina: si el programa se cierra solo, el daemon no lo retuvo");

        // PARTE 2: setDaemon(true) despues de start() lanza una excepcion
        Thread tarde = new Thread(() -> System.out.println("[tarde] hilo normal ejecutandose"), "hilo-tarde");
        tarde.start();
        try {
            tarde.setDaemon(true);
        } catch (IllegalThreadStateException e) {
            System.out.println("IllegalThreadStateException: no se puede cambiar a daemon un hilo ya iniciado");
        }

        // Diferencia entre hilo de usuario y hilo daemon:
        // - Un hilo de usuario mantiene viva la JVM: el programa no termina hasta que todos acaban.
        // - Un hilo daemon es de servicio (como el auto-guardado): cuando solo quedan daemon, la JVM
        //   termina y los corta sin esperar, aunque esten en medio de un bucle infinito.
    }
}