package com.rpg.concurrency;

import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;

/** Reproduce la condicion de carrera: dos hilos restan vida al mismo Personaje. */
public class PruebaCondicionCarrera {

    private static final int VIDA = 1_000_000;
    private static final int GOLPES_POR_HILO = 200_000;

    public static void main(String[] args) throws InterruptedException {
        Personaje objetivo = new Mago("Objetivo");
        objetivo.setVidaMaxima(VIDA);
        objetivo.setVida(VIDA);

        Runnable golpes = () -> {
            for (int i = 0; i < GOLPES_POR_HILO; i++) {
                objetivo.recibirDanio(1);
            }
        };
        Thread hiloA = new Thread(golpes, "hilo-A");
        Thread hiloB = new Thread(golpes, "hilo-B");
        hiloA.start();
        hiloB.start();
        hiloA.join();
        hiloB.join();

        int esperada = VIDA - 2 * GOLPES_POR_HILO;
        System.out.println("Vida esperada: " + esperada);
        System.out.println("Vida real:     " + objetivo.getVida());
        System.out.println(objetivo.getVida() == esperada
                ? "Sin perdidas: el resultado es correcto."
                : "Se perdieron " + (objetivo.getVida() - esperada) + " golpes: hay condicion de carrera.");
    }
}