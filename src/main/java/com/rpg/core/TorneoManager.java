package com.rpg.core;

import com.rpg.entities.Escuadron;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;

import java.util.List;

/** Torneo de 4 gladiadores: dos semifinales y una final. */
public class TorneoManager {

    private static final long CALENTAMIENTO_MS = 500;
    private static final long PAUSA_OBSERVACION_MS = 150;

    private final Arena arena;
    private final RegistroCombate registro;

    public TorneoManager(Arena arena, RegistroCombate registro) {
        this.arena = arena;
        this.registro = registro;
    }

    /** @return el campeon del torneo */
    public Personaje jugarTorneo() {
        List<Personaje> gladiadores = crearGladiadores().getMiembros();

        // Cada semifinal corre en su propio hilo; cada hilo guarda su ganador en su posicion del arreglo
        Personaje[] ganadores = new Personaje[2];
        Thread semifinalA = crearHiloSemifinal("semifinal-A", gladiadores.get(0), gladiadores.get(1), ganadores, 0);
        Thread semifinalB = crearHiloSemifinal("semifinal-B", gladiadores.get(2), gladiadores.get(3), ganadores, 1);

        registro.agregar("Estado antes de start(): A=%s, B=%s", semifinalA.getState(), semifinalB.getState());
        semifinalA.start();
        semifinalB.start();

        try {
            Thread.sleep(PAUSA_OBSERVACION_MS);
            registro.agregar("Estado durante la ejecucion: A=%s, B=%s", semifinalA.getState(), semifinalB.getState());
            // La final solo puede empezar cuando ambas semifinales hayan terminado
            semifinalA.join();
            semifinalB.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("El torneo fue interrumpido antes de terminar las semifinales", e);
        }
        registro.agregar("Estado despues del join(): A=%s, B=%s", semifinalA.getState(), semifinalB.getState());

        // Los ganadores se leen solo despues de ambos join()
        return simularFinal(ganadores[0], ganadores[1]);
    }

    private Thread crearHiloSemifinal(String nombreHilo, Personaje a, Personaje b, Personaje[] ganadores, int posicion) {
        return new Thread(() -> {
            try {
                Thread.sleep(CALENTAMIENTO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            ganadores[posicion] = simularSemifinal(a, b);
        }, nombreHilo);
    }

    public Personaje simularSemifinal(Personaje a, Personaje b) {
        registro.agregar("=== SEMIFINAL: %s vs %s ===", a.getNombre(), b.getNombre());
        Personaje ganador = arena.duelo(a, b);
        registro.agregar("Clasifica a la final: %s", ganador.getNombre());
        return ganador;
    }

    private Personaje simularFinal(Personaje a, Personaje b) {
        a.restaurarVida();
        b.restaurarVida();
        registro.agregar("=== FINAL: %s vs %s ===", a.getNombre(), b.getNombre());
        return arena.duelo(a, b);
    }

    private Escuadron crearGladiadores() {
        Escuadron escuadron = new Escuadron("Gladiadores");
        escuadron.agregar(new Guerrero("Maximo"));
        escuadron.agregar(new Mago("Circe"));
        escuadron.agregar(new Guerrero("Aquiles"));
        escuadron.agregar(new Mago("Merlina"));
        return escuadron;
    }
}