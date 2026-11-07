package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

public class GestorTurno {

    private Thread hilo;

    public void iniciar(int segundos, TurnoCallback callback) {
        hilo = new Thread(new TemporizadorTurnoTask(segundos, callback), "temporizador-turno");
        hilo.start();
    }

    public void cancelar() {
        if (hilo != null) {
            hilo.interrupt();
        }
    }
}
